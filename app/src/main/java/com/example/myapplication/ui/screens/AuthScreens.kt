package com.example.myapplication.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.auth.GoogleAuthHelper
import com.example.myapplication.auth.SmsAuthHelper
import com.example.myapplication.model.UserRole
import com.example.myapplication.ui.components.*
import com.example.myapplication.ui.theme.*
import com.example.myapplication.util.Localization
import com.example.myapplication.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onNext: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        delay(2500)
        onNext()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(1200)) + slideInVertically(tween(1200)) { it / 3 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppLogo(size = 140.dp)
                Spacer(Modifier.height(32.dp))
                Text(
                    "FundiFix",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CyanPrimary,
                    letterSpacing = 2.sp
                )
                Text(
                    "Connect & Fix",
                    fontSize = 16.sp,
                    color = TextLo,
                    letterSpacing = 6.sp
                )
            }
        }
    }
}

@Composable
fun RoleSelectionScreen(onSelect: (UserRole) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(800)) + expandVertically(tween(800))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppLogo(size = 90.dp)
                Spacer(Modifier.height(20.dp))
                Text("FundiFix", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = CyanPrimary)
                Text("Chagua upande wako leo", color = TextLo, modifier = Modifier.padding(top = 10.dp))
            }
        }

        Spacer(modifier = Modifier.height(56.dp))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(800, delayMillis = 300)) + slideInVertically(tween(800, delayMillis = 300)) { it / 2 }
        ) {
            Column {
                RoleCard("Ninahitaji Fundi", "Tafuta huduma za mafundi", Icons.Default.Person, CyanPrimary) {
                    onSelect(UserRole.CLIENT)
                }
                Spacer(modifier = Modifier.height(20.dp))
                RoleCard("Mimi ni Fundi", "Pokea kazi na panga bei", Icons.Default.Handyman, AmberStar) {
                    onSelect(UserRole.FUNDI)
                }
            }
        }
    }
}

@Composable
fun RoleCard(title: String, sub: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(120.dp).premiumClickable(onClick).border(1.2.dp, color.copy(0.4f), RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp).background(color.copy(0.1f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = TextHi, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(sub, color = TextLo, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = color)
        }
    }
}

@Composable
fun AuthScreen(
    role: UserRole, 
    viewModel: MainViewModel,
    onNext: (String, String?, String?) -> Unit, 
    onBack: () -> Unit, 
    onGoogleLoginSuccess: (String) -> Unit
) {
    val lang by viewModel.language.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0: Login, 1: Register
    var phone by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var isSmsLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val vmError by viewModel.errorMessage.collectAsState()
    LaunchedEffect(vmError) {
        if (vmError.isNotEmpty()) {
            errorMessage = vmError
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextHi)
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        AppLogo(size = 60.dp)
        Text(
            Localization.getString("welcome", lang) + " ${if (role == UserRole.CLIENT) "Mteja" else "Fundi"}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextHi
        )

        Spacer(modifier = Modifier.height(24.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = CyanPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CyanPrimary
                )
            },
            divider = {}
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Ingia (Login)", modifier = Modifier.padding(16.dp), color = if(selectedTab == 0) CyanPrimary else TextLo)
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("Jisajili (Register)", modifier = Modifier.padding(16.dp), color = if(selectedTab == 1) CyanPrimary else TextLo)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedTab == 1) {
            Text("Jina Lako Kamili", color = TextLo, modifier = Modifier.align(Alignment.Start))
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                placeholder = { Text("Mfano: Juma Hamisi", color = TextLo.copy(0.5f)) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = BgInput, unfocusedContainerColor = BgInput)
            )
        }

        Text(Localization.getString("phone_number", lang), color = TextLo, modifier = Modifier.align(Alignment.Start))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            leadingIcon = { Text("🇹🇿 +255 ", color = TextHi) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = BgInput, unfocusedContainerColor = BgInput),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Password", color = TextLo, modifier = Modifier.align(Alignment.Start))
        OutlinedTextField(
            value = password,
            onValueChange = { value ->
                if (value.length <= 6 && value.all { it.isDigit() }) password = value
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            placeholder = { Text("Weka tarakimu 6", color = TextLo.copy(0.5f)) },
            shape = RoundedCornerShape(16.dp),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = BgInput, unfocusedContainerColor = BgInput)
        )

        Spacer(modifier = Modifier.height(24.dp))
        
        AnimatedVisibility(
            visible = errorMessage.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(errorMessage, color = Danger, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        }

        Button(
            onClick = {
                if (phone.length >= 9 && password.length == 6 && (selectedTab == 0 || fullName.isNotEmpty())) {
                    if (selectedTab == 0) {
                        viewModel.loginWithPassword(phone, password) { success, message ->
                            if (!success) errorMessage = message
                        }
                    } else {
                        errorMessage = ""
                        isSmsLoading = true
                        viewModel.startRegistration(phone, fullName, password) { success, message ->
                            isSmsLoading = false
                            if (success) onNext(phone, fullName, password)
                            else errorMessage = message
                        }
                    }
                } else {
                    if (phone.length < 9) {
                        errorMessage = "Namba ya simu lazima iwe na tarakimu 9 au zaidi."
                    } else if (selectedTab == 1 && fullName.isEmpty()) {
                        errorMessage = "Tafadhali jaza jina lako kamili."
                    } else if (password.length != 6) {
                        errorMessage = "Password lazima iwe tarakimu 6."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
            shape = RoundedCornerShape(16.dp),
            enabled = !isSmsLoading
        ) {
            if (isSmsLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
            } else {
                Text(if(selectedTab == 0) "Ingia kwa Password" else "Jisajili kwa OTP", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            if (selectedTab == 0) "Tumia namba na password yako kuingia" else "Password ya usalama ni tarakimu 6",
            color = TextLo,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = {
                scope.launch {
                    errorMessage = ""
                    isLoading = true
                    val result = GoogleAuthHelper.signInWithGoogle(context)
                    isLoading = false
                    if (result != null) {
                        onGoogleLoginSuccess(result)
                    } else {
                        errorMessage = "Imeshindwa kuingia na Google. Hakiki kama una internet na Google Account ipo tayari."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = TextHi)
            } else {
                Icon(Icons.Default.AccountCircle, null, tint = TextHi)
                Spacer(Modifier.width(12.dp))
                Text(Localization.getString("sign_in_google", lang), color = TextHi)
            }
        }

    }
}

@Composable
fun OtpVerifyScreen(phone: String, userName: String?, password: String, viewModel: MainViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val lang by viewModel.language.collectAsState()
    var otp by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var resendSeconds by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf("") }

    val vmError by viewModel.errorMessage.collectAsState()
    LaunchedEffect(vmError) {
        if (vmError.isNotEmpty()) {
            errorMessage = vmError
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextHi)
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        Icon(Icons.Default.LockPerson, null, tint = CyanPrimary, modifier = Modifier.size(72.dp))
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            Localization.getString("verify_otp", lang), 
            fontSize = 26.sp, 
            fontWeight = FontWeight.ExtraBold, 
            color = TextHi
        )
        Text(
            Localization.getString("otp_sent", lang) + "\nna namba: +255 $phone", 
            color = TextLo,
            modifier = Modifier.padding(top = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))
        
        OutlinedTextField(
            value = otp,
            onValueChange = { 
                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                    otp = it 
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("6 - Digit OTP", color = TextLo.copy(0.4f)) },
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanPrimary,
                unfocusedBorderColor = LineColor,
                focusedContainerColor = BgInput,
                unfocusedContainerColor = BgInput
            )
        )

        if (errorMessage.isNotEmpty()) {
            Text(
                errorMessage, 
                color = Color.Red, 
                fontSize = 14.sp, 
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = {
                if (otp.length == 6) {
                    scope.launch {
                        errorMessage = ""
                        isLoading = true
                        viewModel.completeRegistration(phone, userName.orEmpty(), password, otp) { success, message ->
                            isLoading = false
                            if (success) onDone() else errorMessage = message
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading && otp.length == 6,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
            } else {
                Text(
                    Localization.getString("confirm_login", lang), 
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
        
        TextButton(
            onClick = {
                if (!isResending && resendSeconds == 0) {
                    scope.launch {
                        errorMessage = ""
                        isResending = true
                        viewModel.startRegistration(phone, userName.orEmpty(), password) { success, message ->
                            isResending = false
                            if (success) {
                                otp = ""
                                resendSeconds = 60
                                errorMessage = "Kodi mpya imetumwa kwa SMS."
                            } else errorMessage = message
                        }
                    }
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                when {
                    isResending -> "Inatuma kodi..."
                    resendSeconds > 0 -> "Tuma tena baada ya ${resendSeconds}s"
                    else -> "Tuma kodi tena (Resend)"
                },
                color = if (resendSeconds > 0) TextLo else CyanPrimary
            )
        }

        LaunchedEffect(resendSeconds) {
            if (resendSeconds > 0) {
                delay(1000)
                resendSeconds -= 1
            }
        }
    }
}
