package com.example.myapplication.util

import com.example.myapplication.model.AppLanguage

object Localization {
    
    fun getString(key: String, lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.SWAHILI -> swahiliStrings[key] ?: key
            AppLanguage.ENGLISH -> englishStrings[key] ?: key
        }
    }

    private val swahiliStrings = mapOf(
        "app_name" to "FundiFix",
        "home" to "Nyumbani",
        "services" to "Huduma",
        "account" to "Akaunti",
        "choose_role" to "Chagua upande wako leo",
        "need_fundi" to "Ninahitaji Fundi",
        "i_am_fundi" to "Mimi ni Fundi",
        "welcome" to "Karibu",
        "phone_number" to "Namba ya simu",
        "continue_whatsapp" to "Endelea na WhatsApp",
        "continue_sms" to "Endelea na SMS za kawaida",
        "or" to "AU",
        "sign_in_google" to "Jisajili na Google Account",
        "verify_otp" to "Hakiki WhatsApp OTP",
        "otp_sent" to "Tumetuma kodi kwenye WhatsApp yako",
        "confirm_login" to "Thibitisha na Uingie",
        "new_jobs" to "Kazi Mpya",
        "online" to "Unasubiri Kazi...",
        "offline" to "Upo Offline",
        "history" to "Historia",
        "protection_enabled" to "Kinga ya FundiFix imewashwa. Kila huduma inahakikiwa kwa usalama wako.",
        "booking_date" to "Tarehe ya Booking (Sio lazima)",
        "describe_issue" to "Elezea Shida ya",
        "search_fundi_now" to "Tafuta Fundi Sasa",
        "language" to "Lugha",
        "select_language" to "Chagua Lugha",
        "theme" to "Muonekano",
        "logout" to "Ondoka",
        "protection" to "Kinga na Usalama",
        "support" to "Msaada",
        "set_profile" to "Weka Picha ya Profile",
        "upload_image" to "Pakia Picha",
        "booking_hint" to "Weka tarehe kama unataka booking"
    )

    private val englishStrings = mapOf(
        "app_name" to "FundiFix",
        "home" to "Home",
        "services" to "Services",
        "account" to "Account",
        "choose_role" to "Choose your side today",
        "need_fundi" to "I need a Technician",
        "i_am_fundi" to "I am a Technician",
        "welcome" to "Welcome",
        "phone_number" to "Phone Number",
        "continue_whatsapp" to "Continue with WhatsApp",
        "continue_sms" to "Continue with Normal SMS",
        "or" to "OR",
        "sign_in_google" to "Sign up with Google Account",
        "verify_otp" to "Verify WhatsApp OTP",
        "otp_sent" to "We have sent a code to your WhatsApp",
        "confirm_login" to "Confirm and Enter",
        "new_jobs" to "New Jobs",
        "online" to "Waiting for Jobs...",
        "offline" to "You are Offline",
        "history" to "History",
        "protection_enabled" to "FundiFix Protection is active. Every service is verified for your safety.",
        "booking_date" to "Booking Date (Optional)",
        "describe_issue" to "Describe the issue for",
        "search_fundi_now" to "Search Technician Now",
        "language" to "Language",
        "select_language" to "Select Language",
        "theme" to "Appearance",
        "logout" to "Logout",
        "protection" to "Protection and Safety",
        "support" to "Support",
        "set_profile" to "Set Profile Picture",
        "upload_image" to "Upload Image",
        "booking_hint" to "Enter date for future booking"
    )
}
