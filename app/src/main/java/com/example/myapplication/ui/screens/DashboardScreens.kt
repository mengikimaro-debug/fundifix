package com.example.myapplication.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.myapplication.ai.AiAssistant
import com.example.myapplication.model.*
import com.example.myapplication.ui.components.*
import com.example.myapplication.ui.theme.*
import com.example.myapplication.viewmodel.MainViewModel
import com.example.myapplication.util.Localization
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun playNewJobAlert(context: Context) {
    val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    RingtoneManager.getRingtone(context, notificationUri)?.play()

    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    vibrator?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 350), -1))
        } else {
            @Suppress("DEPRECATION")
            it.vibrate(longArrayOf(0, 250, 150, 350), -1)
        }
    }
}

@Composable
fun ClientDashboard(
    viewModel: MainViewModel,
    clientPhone: String,
    onServiceClick: (Pair<String, ImageVector>) -> Unit
) {
    val lang by viewModel.language.collectAsState()
    var selectedTab by remember { mutableStateOf(DashboardTab.HOME) }
    var selectedCategory by remember { mutableStateOf<Pair<String, ImageVector>?>(null) }

    LaunchedEffect(clientPhone) { viewModel.fetchClientRequests(clientPhone) }
    LaunchedEffect(selectedTab) {
        if (selectedTab == DashboardTab.SERVICES) viewModel.fetchClientRequests(clientPhone)
    }

    Scaffold(
        bottomBar = { BottomNav(lang, selectedTab) { selectedTab = it } },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                    }.using(SizeTransform(clip = false))
                },
                label = "tab_switch"
            ) { tab ->
                when (tab) {
                    DashboardTab.HOME -> ClientHomeContent(
                        lang = lang,
                        onLanguageToggle = {
                            viewModel.setLanguage(if (lang == AppLanguage.SWAHILI) AppLanguage.ENGLISH else AppLanguage.SWAHILI)
                        }
                    ) { selectedCategory = it }
                    DashboardTab.SERVICES -> ClientHistoryContent(viewModel, clientPhone, lang)
                    DashboardTab.AI -> AiChatDialog(onDismiss = { selectedTab = DashboardTab.HOME })
                    DashboardTab.ACCOUNT -> AccountContent(viewModel, lang)
                }
            }
        }
    }

    if (selectedCategory != null) {
        SubServiceSelectionDialog(
            category = selectedCategory!!,
            onDismiss = { selectedCategory = null },
            onSelect = { subServiceName ->
                onServiceClick(Pair(subServiceName, selectedCategory!!.second))
                selectedCategory = null
            }
        )
    }
}

@Composable
fun ClientHomeContent(
    lang: AppLanguage,
    onLanguageToggle: () -> Unit,
    onServiceClick: (Pair<String, ImageVector>) -> Unit
) {
    val protectionAnimation = rememberInfiniteTransition(label = "protection_breathing")
    val protectionAlpha by protectionAnimation.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "protection_breathing_alpha"
    )
    val protectionScale by protectionAnimation.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "protection_breathing_scale"
    )
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopHeader(lang, onLanguageToggle)
        HeroBanner(lang)
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .graphicsLayer {
                    alpha = protectionAlpha
                    scaleX = protectionScale
                    scaleY = protectionScale
                },
            colors = CardDefaults.cardColors(containerColor = CyanPrimary.copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(Localization.getString("protection_enabled", lang), color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, maxLines = 2)
            }
        }

        SectionTitle(Localization.getString("services", lang))
        GridHuduma(onServiceClick)
    }
}

@Composable
fun ClientHistoryContent(viewModel: MainViewModel, clientPhone: String, lang: AppLanguage) {
    val requests by viewModel.clientRequests.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(Localization.getString("history", lang), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = { viewModel.fetchClientRequests(clientPhone) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh bills", tint = CyanPrimary)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (requests.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Bado huna maombi ya huduma", color = TextLo)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(requests) { request ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = BgCard)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(request.service, color = TextHi, fontWeight = FontWeight.Bold)
                                Text(request.price, color = CyanPrimary, fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Bill ya sasa", color = TextLo, fontSize = 12.sp)
                            Text(request.status, color = AmberStar, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            RatingStars("Rate huduma")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountContent(viewModel: MainViewModel, lang: AppLanguage) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLangDialog by remember { mutableStateOf(false) }
    val userRole by viewModel.userRole.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.updateProfileImage(it) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(Localization.getString("account", lang), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text("Mipangilio ya akaunti yako", color = TextLo, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        
        // Profile Section
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(80.dp).clickable { 
                photoLauncher.launch("image/*")
            }) {
                if (currentUser?.profileImage != null) {
                    AsyncImage(
                        model = currentUser?.profileImage,
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxSize().clip(CircleShape).border(2.dp, CyanPrimary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(BgCard, CircleShape).border(1.dp, CyanPrimary, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AddAPhoto, null, tint = CyanPrimary)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(currentUser?.name ?: Localization.getString("welcome", lang), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(currentUser?.phone ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(12.dp))
        RatingStars(if (userRole == UserRole.FUNDI) "Fundi rating" else "Mteja rating")

        Spacer(Modifier.height(24.dp))
        Text("Mipangilio", color = CyanPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = BgCard)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                AccountItem(Icons.Default.Translate, Localization.getString("language", lang)) { showLangDialog = true }
                AccountItem(Icons.Default.Palette, "Mwonekano / Theme") { showThemeDialog = true }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Usalama na msaada", color = CyanPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = BgCard)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                AccountItem(Icons.Default.Shield, "Kinga na usalama") { }
                AccountItem(Icons.Default.Help, "Msaada") { }
            }
        }
        Spacer(Modifier.height(8.dp))
        AccountItem(Icons.Default.Logout, Localization.getString("logout", lang), color = Danger) { viewModel.logout() }

        if (showThemeDialog) ThemeSelectionDialog(viewModel, onDismiss = { showThemeDialog = false })
        if (showLangDialog) LanguageSelectionDialog(viewModel, onDismiss = { showLangDialog = false })

        Spacer(Modifier.height(40.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Green.copy(0.1f)),
            border = BorderStroke(1.dp, Color.Green.copy(0.3f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, tint = Success)
                Spacer(Modifier.width(12.dp))
                Text(Localization.getString("protection_enabled", lang), color = TextHi, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun LanguageSelectionDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val currentLang by viewModel.language.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chagua Lugha / Select Language", color = CyanPrimary) },
        text = {
            Column {
                LanguageOption("Kiswahili", AppLanguage.SWAHILI, currentLang) { viewModel.setLanguage(it) }
                LanguageOption("English", AppLanguage.ENGLISH, currentLang) { viewModel.setLanguage(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        containerColor = BgCard
    )
}

@Composable
fun LanguageOption(label: String, lang: AppLanguage, current: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(lang) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = lang == current, onClick = { onSelect(lang) })
        Spacer(Modifier.width(8.dp))
        Text(label, color = TextHi)
    }
}

@Composable
fun AccountItem(icon: ImageVector, label: String, color: Color = CyanPrimary, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = TextLo)
    }
}

@Composable
fun RatingStars(label: String) {
    var rating by remember { mutableStateOf(0) }
    Column {
        Text(label, color = TextLo, fontSize = 12.sp)
        Row {
            (1..5).forEach { value ->
                IconButton(onClick = { rating = value }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "$value stars",
                        tint = if (value <= rating) AmberStar else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun FundiDashboard(viewModel: MainViewModel) {
    val lang by viewModel.language.collectAsState()
    var selectedTab by remember { mutableStateOf(DashboardTab.HOME) }
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = { BottomNav(lang, selectedTab) { selectedTab = it } },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                DashboardTab.HOME -> FundiHomeContent(viewModel, lang)
                DashboardTab.SERVICES -> FundiJobsHistoryContent(viewModel)
                DashboardTab.AI -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("FundiFix AI", color = CyanPrimary, fontWeight = FontWeight.Bold)
                }
                DashboardTab.ACCOUNT -> AccountContent(viewModel, lang)
            }
        }
    }

    if (selectedTab == DashboardTab.AI) {
        AiChatDialog(onDismiss = { selectedTab = DashboardTab.HOME })
    }
}

@Composable
fun FundiHomeContent(viewModel: MainViewModel, lang: AppLanguage) {
    var isOnline by remember { mutableStateOf(true) }
    val requests by viewModel.availableJobs.collectAsState()
    var visible by remember { mutableStateOf(false) }
    var priceReviewJob by remember { mutableStateOf<ServiceReq?>(null) }
    var knownJobIds by remember { mutableStateOf<Set<String>?>(null) }
    val context = LocalContext.current
    val waitingAnimation = rememberInfiniteTransition(label = "waiting_for_jobs_animation")
    val waitingRotation by waitingAnimation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "waiting_rotation"
    )
    val waitingPulse by waitingAnimation.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "waiting_pulse"
    )

    LaunchedEffect(isOnline) {
        visible = true
        if (isOnline) {
            while (true) {
                viewModel.fetchJobs()
                delay(15_000)
            }
        }
    }

    LaunchedEffect(requests) {
        val currentJobIds = requests.map { it.id }.toSet()
        val newJobIds = knownJobIds?.let { currentJobIds - it } ?: emptySet()
        if (isOnline && newJobIds.isNotEmpty()) playNewJobAlert(context)
        knownJobIds = currentJobIds
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        TopHeader(lang) {
            viewModel.setLanguage(if (lang == AppLanguage.SWAHILI) AppLanguage.ENGLISH else AppLanguage.SWAHILI)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                Localization.getString("new_jobs", lang),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextHi
            )
            Switch(checked = isOnline, onCheckedChange = { isOnline = it })
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + expandVertically()
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp)
                    .background(if (isOnline) CyanPrimary.copy(0.1f) else Color.Red.copy(0.1f), RoundedCornerShape(24.dp))
                    .border(1.dp, if (isOnline) CyanPrimary else Color.Red, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isOnline) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .graphicsLayer { rotationZ = waitingRotation; alpha = waitingPulse }
                                .border(2.dp, CyanPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Refresh, null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        if (isOnline) Localization.getString("online", lang) else Localization.getString("offline", lang),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnline) CyanPrimary else Danger
                    )
                    if (isOnline) LinearProgressIndicator(
                        modifier = Modifier.padding(top = 16.dp),
                        color = CyanPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(requests) { req ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInHorizontally()
                ) {
                    JobCard(
                        req = req,
                        onAccept = { viewModel.acceptJob(req.id) },
                        onReject = { viewModel.rejectJob(req.id) },
                        onPriceReview = { priceReviewJob = req }
                    )
                }
            }
        }
    }

    priceReviewJob?.let { job ->
        PriceReviewDialog(
            job = job,
            onDismiss = { priceReviewJob = null },
            onUpdatePrice = { price ->
                viewModel.updateJobPrice(job.id, price)
                priceReviewJob = null
            }
        )
    }
}

@Composable
fun FundiJobsHistoryContent(viewModel: MainViewModel) {
    val requests by viewModel.availableJobs.collectAsState()
    LaunchedEffect(Unit) { viewModel.fetchJobs() }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Services", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = { viewModel.fetchJobs() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload services", tint = CyanPrimary)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (requests.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Hakuna services bado", color = TextLo)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(requests) { request ->
                    JobCard(
                        req = request,
                        onAccept = { viewModel.acceptJob(request.id) },
                        onReject = { viewModel.rejectJob(request.id) },
                        onPriceReview = { }
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeSelectionDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val currentMode by viewModel.themeMode.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme", color = CyanPrimary) },
        text = {
            Column {
                ThemeOption("Light Mode", ThemeMode.LIGHT, currentMode) { viewModel.setThemeMode(it) }
                ThemeOption("Dark Mode", ThemeMode.DARK, currentMode) { viewModel.setThemeMode(it) }
                ThemeOption("System Default", ThemeMode.SYSTEM, currentMode) { viewModel.setThemeMode(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = BgCard
    )
}

@Composable
fun ThemeOption(label: String, mode: ThemeMode, current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = mode == current, onClick = { onSelect(mode) })
        Spacer(Modifier.width(8.dp))
        Text(label, color = TextHi)
    }
}

@Composable
fun AiChatDialog(onDismiss: () -> Unit) {
    var message by rememberSaveable { mutableStateOf("") }
    var response by rememberSaveable { mutableStateOf("Habari! Mimi ni FundiFix AI. Nawezaje kukusaidia leo?") }
    val scope = rememberCoroutineScope()
    val aiAnimation = rememberInfiniteTransition(label = "ai_breathing")
    val aiScale by aiAnimation.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "ai_breathing_scale"
    )
    val aiAlpha by aiAnimation.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "ai_breathing_alpha"
    )
    Surface(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.35f))
    ) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        null,
                        tint = CyanPrimary.copy(alpha = aiAlpha),
                        modifier = Modifier.size(22.dp).graphicsLayer {
                            scaleX = aiScale
                            scaleY = aiScale
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("FundiFix AI", color = CyanPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Mazungumzo yako na AI", color = TextLo, fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Rudi") }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    Text(response, color = TextHi, fontSize = 15.sp, lineHeight = 22.sp)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { value -> message = value },
                    placeholder = { Text("Uliza chochote...", color = TextLo) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 180.dp),
                    minLines = 5
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = {
                            if (message.isNotBlank()) scope.launch {
                                val question = message.trim()
                                val aiResponse = AiAssistant.getServiceAdvice(question)
                                response = "$response\n\nWewe: $question\n\n$aiResponse"
                                message = ""
                            }
                        },
                        enabled = message.isNotBlank()
                    ) { Text("Tuma") }
                }
            }
    }
}

@Composable
fun PriceReviewDialog(job: ServiceReq, onDismiss: () -> Unit, onUpdatePrice: (String) -> Unit) {
    var correctedPrice by remember { mutableStateOf("") }
    var conversation by remember {
        mutableStateOf("AI: Fundi, je bei ya ${job.price} kwa ${job.service} ni sahihi kwa mteja? Ikiwa si sahihi, andika bei mpya hapa.")
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth(0.94f), shape = RoundedCornerShape(24.dp), color = BgCard) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = CyanPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("AI Price Check", color = CyanPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp).verticalScroll(rememberScrollState())) {
                    Text(conversation, color = TextHi)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = correctedPrice,
                    onValueChange = { correctedPrice = it },
                    label = { Text("Bei mpya kwa mteja") },
                    placeholder = { Text("Mfano: 45000 Tsh") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Bei iko sawa") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (correctedPrice.isNotBlank()) {
                            conversation += "\n\nFundi: Bei sahihi ni ${correctedPrice.trim()}.\nAI: Nimebadilisha bill ya mteja kuwa ${correctedPrice.trim()}."
                            onUpdatePrice(correctedPrice.trim())
                        }
                    }, enabled = correctedPrice.isNotBlank()) { Text("Badilisha bill") }
                }
            }
        }
    }
}

@Composable
fun GridHuduma(onClick: (Pair<String, ImageVector>) -> Unit) {
    val items = listOf(
        Pair("Electrical", Icons.Default.ElectricBolt),
        Pair("Dish", Icons.Default.SettingsInputAntenna),
        Pair("CCTV", Icons.Default.Videocam),
        Pair("Air Conditioning", Icons.Default.AcUnit)
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items.take(2).forEach { CatCard(it.first, it.second, false, Modifier.weight(1f).clickable { onClick(it) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items.drop(2).forEach { CatCard(it.first, it.second, false, Modifier.weight(1f).clickable { onClick(it) }) }
        }
    }
}

@Composable
fun SubServiceSelectionDialog(category: Pair<String, ImageVector>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val subServices = when (category.first) {
        "Electrical" -> listOf("Short Circuit", "Removal Service", "New Installation", "Maintenance")
        "Dish" -> listOf("Dish Installation", "Dish Service", "Dish Replacement", "Signal Fix")
        "CCTV" -> listOf("Camera Installation", "System Maintenance", "Storage Setup", "Night Vision Fix")
        "Air Conditioning" -> listOf("AC Installation", "Gas Refill", "Cleaning Service", "Repair")
        else -> listOf("General Service")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(category.second, null, tint = CyanPrimary); Spacer(Modifier.width(8.dp)); Text("Huduma za ${category.first}", color = CyanPrimary) } },
        text = { Column { subServices.forEach { sub -> TextButton(onClick = { onSelect(sub) }, modifier = Modifier.fillMaxWidth()) { Text(sub, color = TextHi, modifier = Modifier.fillMaxWidth()) } } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Ghairi") } },
        containerColor = BgCard
    )
}

@Composable
fun JobCard(req: ServiceReq, onAccept: () -> Unit, onReject: () -> Unit, onPriceReview: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = BgCard), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, LineColor)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(32.dp).background(CyanPrimary, CircleShape), contentAlignment = Alignment.Center) { Text(req.service.take(1), color = Color.Black, fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.width(12.dp))
                Text(req.service, color = TextHi, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(req.price, color = CyanPrimary, fontWeight = FontWeight.ExtraBold)
            }
            Text(req.desc, color = TextLo, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            OutlinedButton(onClick = onPriceReview, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("AI: Kagua bei ya mteja")
            }
            Spacer(Modifier.height(8.dp))
            if (!req.bookingDate.isNullOrEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Event, null, tint = AmberStar, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Booking: ${req.bookingDate}", color = AmberStar, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onReject, 
                    modifier = Modifier.weight(1f), 
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                ) { 
                    Text("Kataa", color = Danger) 
                }
                Button(
                    onClick = onAccept, 
                    modifier = Modifier.weight(1f), 
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) { 
                    Text("Kubali", color = MaterialTheme.colorScheme.onPrimary) 
                }
            }
        }
    }
}

@Composable
fun RequestFormScreen(
    service: Pair<String, ImageVector>, 
    onBack: () -> Unit,
    onSubmit: (String, String, String?, String?) -> Unit
) {
    var desc by remember { mutableStateOf("") }
    var bookingDate by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    
    val darAreas = listOf(
        "Kinondoni", "Ilala", "Temeke", "Kigamboni", "Ubungo", 
        "Mbagala", "Tabata", "Kawe", "Masaki", "Kariakoo", "Mikocheni"
    )

    var aiAdvice by remember { mutableStateOf("") }
    var aiPrice by remember { mutableStateOf("Inatathmini...") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(service.first) { aiPrice = AiAssistant.getPriceWithCurrency(service.first) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface) }
        Text("Ombi la ${service.first}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Elezea changamoto yako", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = desc, 
            onValueChange = { 
                desc = it
                if (it.length > 5) { scope.launch { aiAdvice = AiAssistant.getServiceAdvice(it) } } 
            }, 
            modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp), 
            placeholder = { Text("Andika maelezo ya shida yako hapa...", color = MaterialTheme.colorScheme.onSurfaceVariant) }, 
            shape = RoundedCornerShape(16.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Eneo unapoishi (Dar es Salaam)", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(
                value = location,
                onValueChange = { },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Chagua eneo...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                shape = RoundedCornerShape(16.dp),
                trailingIcon = { 
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.8f).background(MaterialTheme.colorScheme.surface)
            ) {
                darAreas.forEach { area ->
                    DropdownMenuItem(
                        text = { Text(area, color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            location = area
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Tarehe ya Booking (Sio lazima)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        OutlinedTextField(
            value = bookingDate, 
            onValueChange = { bookingDate = it }, 
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp), 
            placeholder = { Text("Mfano: 20/08/2026", color = MaterialTheme.colorScheme.onSurfaceVariant) }, 
            shape = RoundedCornerShape(16.dp), 
            leadingIcon = { Icon(Icons.Default.Event, null) }
        )

        if (aiAdvice.isNotEmpty() || aiPrice.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(colors = CardDefaults.cardColors(containerColor = CyanPrimary.copy(0.1f)), border = BorderStroke(1.dp, CyanPrimary)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (aiPrice.isNotEmpty()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PriceCheck, null, tint = CyanPrimary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Bei Inayopendekezwa na AI: $aiPrice", color = CyanPrimary, fontWeight = FontWeight.Bold) } }
                    if (aiAdvice.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(aiAdvice, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { 
                if (desc.isNotEmpty() && location.isNotEmpty()) { 
                    onSubmit(desc, aiPrice, if (bookingDate.isEmpty()) null else bookingDate, location) 
                } 
            }, 
            modifier = Modifier.fillMaxWidth().height(56.dp), 
            shape = RoundedCornerShape(18.dp), 
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
            enabled = desc.isNotEmpty() && location.isNotEmpty()
        ) { 
            Text("Tafuta Fundi Sasa", color = Color.Black, fontWeight = FontWeight.Bold) 
        }
    }
}
