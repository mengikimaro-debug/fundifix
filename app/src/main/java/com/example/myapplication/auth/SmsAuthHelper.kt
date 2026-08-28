package com.example.myapplication.auth

import android.util.Log
import com.example.myapplication.model.OtpRequest
import com.example.myapplication.model.OtpVerifyRequest
import com.example.myapplication.network.ApiService
import retrofit2.awaitResponse

/**
 * Mfumo wa kuhakiki OTP kupitia SMS za kawaida
 */
object SmsAuthHelper {
    
    private val apiService = ApiService.create()

    /**
     * Tuma ombi la kodi (OTP) kwenda kwenye namba ya simu kupitia SMS (Normal SMS)
     */
    suspend fun sendSmsOtp(phone: String): Boolean {
        Log.d("SmsAuth", "Inatuma SMS OTP kwenda $phone")
        return try {
            // Tunatumia endpoint mpya ya SMS kwenye server yetu
            val response = apiService.sendSmsOtp(OtpRequest(phone)).awaitResponse()
            Log.d("SmsAuth", "Jibu la Server: ${response.code()}")
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e("SmsAuth", "Imeshindwa kutuma SMS: ${e.message}")
            false
        }
    }

    /**
     * Hakiki kodi ya SMS iliyoingizwa na mtumiaji
     */
    suspend fun verifySmsOtp(phone: String, otp: String): Boolean {
        Log.d("SmsAuth", "Inahakiki kodi ya SMS $otp kwa namba $phone")
        return try {
            val response = apiService.verifySmsOtp(OtpVerifyRequest(phone, otp)).awaitResponse()
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun resendSmsOtp(phone: String): Pair<Boolean, String> {
        return try {
            val response = apiService.resendOtp(OtpRequest(phone)).awaitResponse()
            val message = response.body()?.message ?: "Imeshindikana kutuma kodi tena."
            Pair(response.isSuccessful && response.body()?.success == true, message)
        } catch (e: Exception) {
            Pair(false, "Tatizo la mtandao. Hakiki muunganisho wako.")
        }
    }
}
