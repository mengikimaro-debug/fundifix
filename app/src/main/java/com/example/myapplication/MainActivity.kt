package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.model.AppScreen
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.model.UserRole
import com.example.myapplication.ui.components.*
import com.example.myapplication.ui.screens.*
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            
            val isDarkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                val currentScreen by viewModel.uiState.collectAsState()
                val userRole by viewModel.userRole.collectAsState()
                val currentUser by viewModel.currentUser.collectAsState()
                
                var userPhone by remember { mutableStateOf("") }
                var userName by remember { mutableStateOf<String?>(null) }
                var userPassword by remember { mutableStateOf("") }
                var showExitDialog by remember { mutableStateOf(false) }
                var selectedService by remember {
                    mutableStateOf(Pair("Electrical", Icons.Default.ElectricBolt))
                }

                LaunchedEffect(currentUser) {
                    currentUser?.let {
                        userPhone = it.phone
                        userName = it.name
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(), 
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConnectedBackground()
                    
                    BackHandler(enabled = true) {
                        when (currentScreen) {
                            AppScreen.AUTH -> viewModel.setScreen(AppScreen.ROLE_SELECT)
                            AppScreen.OTP_VERIFY -> viewModel.setScreen(AppScreen.AUTH)
                            AppScreen.REQUEST_FORM -> viewModel.setScreen(AppScreen.DASHBOARD)
                            AppScreen.DASHBOARD, AppScreen.ROLE_SELECT -> showExitDialog = true
                            else -> showExitDialog = true
                        }
                    }

                    if (showExitDialog) {
                        AlertDialog(
                            onDismissRequest = { showExitDialog = false },
                            title = { Text("Are you sure you want to exit?") },
                            text = { Text("Taarifa zako za mteja na fundi zimehifadhiwa.") },
                            confirmButton = {
                                Button(onClick = { finishAndRemoveTask() }) { Text("Exit") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showExitDialog = false }) { Text("Cancel") }
                            }
                        )
                    }

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn(tween(500)) togetherWith fadeOut(tween(500))
                        },
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            AppScreen.SPLASH -> SplashScreen {
                                viewModel.setScreen(AppScreen.ROLE_SELECT)
                            }
                            AppScreen.ROLE_SELECT -> RoleSelectionScreen { role ->
                                viewModel.setRole(role)
                                viewModel.setScreen(AppScreen.AUTH)
                            }
                            AppScreen.AUTH -> AuthScreen(
                                role = userRole,
                                viewModel = viewModel,
                                onNext = { phone, name, password ->
                                    userPhone = phone
                                    userName = name
                                    userPassword = password.orEmpty()
                                    viewModel.setScreen(AppScreen.OTP_VERIFY)
                                },
                                onBack = { viewModel.setScreen(AppScreen.ROLE_SELECT) },
                                onGoogleLoginSuccess = { googleId ->
                                    viewModel.registerUser(googleId, "Google", userName)
                                }
                            )
                            AppScreen.OTP_VERIFY -> OtpVerifyScreen(
                                phone = userPhone,
                                userName = userName,
                                password = userPassword,
                                viewModel = viewModel,
                                onDone = {},
                                onBack = { viewModel.setScreen(AppScreen.AUTH) }
                            )
                            AppScreen.DASHBOARD -> {
                                if (userRole == UserRole.CLIENT) {
                                    ClientDashboard(
                                        viewModel = viewModel,
                                        clientPhone = userPhone,
                                        onServiceClick = {
                                            selectedService = it
                                            viewModel.setScreen(AppScreen.REQUEST_FORM)
                                        }
                                    )
                                } else {
                                    FundiDashboard(viewModel)
                                }
                            }
                            AppScreen.REQUEST_FORM -> RequestFormScreen(
                                service = selectedService,
                                onBack = { viewModel.setScreen(AppScreen.DASHBOARD) },
                                onSubmit = { desc, price, date, loc ->
                                    viewModel.submitServiceRequest(selectedService.first, desc, userPhone, price, date, loc)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
