package com.example.myapplication.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.example.myapplication.model.AppLanguage
import com.example.myapplication.model.DashboardTab
import com.example.myapplication.ui.theme.*
import com.example.myapplication.util.Localization
import kotlinx.coroutines.delay

@Composable
fun ConnectedBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dotCount = 15
    val dotStates = List(dotCount) {
        val duration = remember { (3000..6000).random() }
        val delay = remember { (0..2000).random() }
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot"
        )
    }

    Canvas(modifier = Modifier.fillMaxSize().alpha(0.2f)) {
        val points = dotStates.mapIndexed { index, state ->
            val angle = (index.toFloat() / dotCount) * 2f * Math.PI
            val offsetX = (state.value - 0.5f) * 100f
            val offsetY = (state.value - 0.5f) * 100f
            
            val baseStartX = size.width * (0.1f + 0.8f * (index % 4) / 3f)
            val baseStartY = size.height * (0.1f + 0.8f * (index / 4) / 3f)
            
            Offset(
                (baseStartX + offsetX).coerceIn(0f, size.width),
                (baseStartY + offsetY).coerceIn(0f, size.height)
            )
        }

        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val dist = (points[i] - points[j]).getDistance()
                if (dist < 350f) {
                    drawLine(
                        color = CyanPrimary.copy(alpha = (1f - (dist / 350f)) * 0.3f),
                        start = points[i],
                        end = points[j],
                        strokeWidth = 1f
                    )
                }
            }
        }

        points.forEach { point ->
            drawCircle(CyanPrimary.copy(alpha = 0.6f), radius = 4f, center = point)
        }
    }
}

@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.toPx()
            val h = size.toPx()
            val center = Offset(w / 2, h / 2)
            
            // Premium Radial Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CyanPrimary.copy(alpha = glowAlpha), Color.Transparent),
                    center = center,
                    radius = w * 0.7f
                ),
                radius = w * 0.7f,
                center = center
            )

            // Draw Hexagon Border
            val hexPath = androidx.compose.ui.graphics.Path().apply {
                val radius = w * 0.45f
                for (i in 0..5) {
                    val angle = Math.toRadians(i * 60.0 - 90.0)
                    val x = center.x + (radius * kotlin.math.cos(angle)).toFloat()
                    val y = center.y + (radius * kotlin.math.sin(angle)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(hexPath, color = CyanPrimary, style = Stroke(width = w * 0.08f, cap = StrokeCap.Round))

            // Draw Stylized "F" (wrench-like)
            val fPath = androidx.compose.ui.graphics.Path().apply {
                val unit = w * 0.05f
                moveTo(center.x - unit * 3, center.y + unit * 4) // Bottom of F
                lineTo(center.x - unit * 3, center.y - unit * 4) // Vertical bar
                lineTo(center.x + unit * 4, center.y - unit * 4) // Top horizontal
                moveTo(center.x - unit * 3, center.y - unit * 0.5f)
                lineTo(center.x + unit * 2, center.y - unit * 0.5f) // Middle horizontal
            }
            drawPath(fPath, color = CyanPrimary, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round))
            
            // Wrench head circle at the bottom
            drawCircle(CyanPrimary, radius = w * 0.08f, center = Offset(center.x - w * 0.15f, center.y + w * 0.22f))
            drawCircle(BgColor, radius = w * 0.03f, center = Offset(center.x - w * 0.15f, center.y + w * 0.22f))
        }
    }
}

@Composable
fun TopHeader(lang: AppLanguage, onLanguageToggle: () -> Unit) {
    var showNotifications by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -it / 2 }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(size = 32.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    Localization.getString("app_name", lang),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLanguageToggle) {
                    Text(
                        if (lang == AppLanguage.SWAHILI) "EN" else "SW",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box {
                    IconButton(onClick = { showNotifications = !showNotifications }) {
                        Icon(Icons.Default.Notifications, "Notifications", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Danger, CircleShape)
                            .align(Alignment.TopEnd)
                    )
                }
            }
        }
    }

    if (showNotifications) {
        Popup(
            alignment = Alignment.TopEnd,
            offset = IntOffset(-16, 74),
            onDismissRequest = { showNotifications = false }
        ) {
            Surface(
                modifier = Modifier.width(280.dp),
                shape = RoundedCornerShape(16.dp),
                color = BgCard,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.45f))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = CyanPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(if (lang == AppLanguage.SWAHILI) "Arifa mpya" else "New notification", color = CyanPrimary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (lang == AppLanguage.SWAHILI) "FundiFix iko tayari. Tutakujulisha kuhusu kazi na huduma mpya." else "FundiFix is ready. We will notify you about new jobs and services.",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showNotifications = false }, modifier = Modifier.align(Alignment.End)) {
                        Text(if (lang == AppLanguage.SWAHILI) "Funga" else "Close")
                    }
                }
            }
        }
    }
}

@Composable
fun HeroBanner(lang: AppLanguage) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(800)) + expandVertically(tween(800))
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(120.dp)
                .background(Brush.linearGradient(listOf(CyanDeep, BgCard)), RoundedCornerShape(22.dp))
                .shimmerEffect().padding(20.dp)
        ) {
            Text(
                Localization.getString("welcome", lang) + "!\n" + Localization.getString("online", lang),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(
        t,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

@Composable
fun CatCard(n: String, i: ImageVector, a: Boolean, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "category_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label = "category_alpha"
    )
    val iconTransition = rememberInfiniteTransition(label = "category_icon_animation")
    val iconScale by iconTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "category_icon_scale"
    )
    val glowAlpha by iconTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "category_icon_glow"
    )

    LaunchedEffect(Unit) {
        delay(200)
        visible = true
    }

    Card(
        modifier.then(Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }).height(118.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (a) AmberStar else AmberStar.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(AmberStar.copy(alpha = glowAlpha), RoundedCornerShape(14.dp))
                    .border(1.dp, AmberStar.copy(alpha = glowAlpha + 0.2f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    i,
                    null,
                    tint = AmberStar,
                    modifier = Modifier.size(21.dp).graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        rotationZ = (iconScale - 1f) * 8f
                    }
                )
            }
            Text(n, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

fun Modifier.premiumClickable(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "click_scale"
    )

    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}

@Composable
fun FundiAvatar(url: String? = null) {
    Box(
        Modifier.size(64.dp).background(AmberStar.copy(alpha = 0.14f), CircleShape).border(1.5.dp, AmberStar, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (url == null) {
            Icon(Icons.Default.Person, null, tint = AmberStar, modifier = Modifier.size(26.dp))
        } else {
            // Katika App halisi, tumia Coil/Glide kupakia picha
            Text("IMG", color = AmberStar, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }
    }
}

@Composable
fun BottomNav(lang: AppLanguage, currentTab: DashboardTab, onTabSelected: (DashboardTab) -> Unit) {
    NavigationBar(containerColor = BgColor) {
        NavigationBarItem(
            selected = currentTab == DashboardTab.HOME,
            onClick = { onTabSelected(DashboardTab.HOME) },
            icon = { Icon(Icons.Default.Home, null) },
            label = { Text(Localization.getString("home", lang)) }
        )
        NavigationBarItem(
            selected = currentTab == DashboardTab.SERVICES,
            onClick = { onTabSelected(DashboardTab.SERVICES) },
            icon = { Icon(Icons.AutoMirrored.Filled.Assignment, null) },
            label = { Text(Localization.getString("services", lang)) }
        )
        NavigationBarItem(
            selected = currentTab == DashboardTab.AI,
            onClick = { onTabSelected(DashboardTab.AI) },
            icon = { Icon(Icons.Default.AutoAwesome, null) },
            label = { Text("AI") }
        )
        NavigationBarItem(
            selected = currentTab == DashboardTab.ACCOUNT,
            onClick = { onTabSelected(DashboardTab.ACCOUNT) },
            icon = { Icon(Icons.Default.Person, null) },
            label = { Text(Localization.getString("account", lang)) }
        )
    }
}

fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(1500)),
        label = "shimmerOffsetX"
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, CyanPrimary.copy(alpha = 0.2f), Color.Transparent),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).onGloballyPositioned { size = it.size }
}
