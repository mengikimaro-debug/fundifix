package com.example.myapplication.model

import androidx.compose.ui.graphics.vector.ImageVector

// --- Enums ---
enum class AppScreen { SPLASH, ROLE_SELECT, AUTH, OTP_VERIFY, DASHBOARD, REQUEST_FORM }
enum class UserRole { CLIENT, FUNDI }
enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class DashboardTab { HOME, SERVICES, AI, ACCOUNT }
enum class AppLanguage { SWAHILI, ENGLISH }

// --- Data Models ---
data class UserData(
    val phone: String, 
    val role: String, 
    val method: String,
    var profileImage: String? = null,
    var name: String? = null,
    var password: String? = null
)

data class ServiceReq(
    val id: String = java.util.UUID.randomUUID().toString(),
    val service: String, 
    val desc: String, 
    val clientPhone: String, 
    var price: String = "0",
    val bookingDate: String? = null,
    val location: String? = null,
    val status: String = "pending"
)

data class GenericResponse(
    val success: Boolean, 
    val message: String
)

data class OtpRequest(val phone: String)
data class OtpVerifyRequest(val phone: String, val otp: String)
data class PasswordLoginRequest(val phone: String, val password: String, val role: String)

data class ServiceCategory(
    val name: String,
    val icon: ImageVector
)
