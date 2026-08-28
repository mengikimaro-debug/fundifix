package com.example.myapplication.auth

import android.util.Log
import com.example.myapplication.model.GenericResponse
import com.example.myapplication.model.OtpRequest
import com.example.myapplication.model.OtpVerifyRequest
import com.example.myapplication.network.ApiService
import retrofit2.awaitResponse

object WhatsAppAuthHelper {
    
    private val apiService = ApiService.create()

    /**
     * Tuma OTP kwenye WhatsApp ya mtumiaji kupitia Backend yetu
     */
    suspend fun sendOtp(phone: String): Boolean {
        Log.d("WhatsAppAuth", "Attempting to send OTP to $phone")
        return try {
            val response = apiService.sendWhatsAppOtp(OtpRequest(phone)).awaitResponse()
            Log.d("WhatsAppAuth", "Response: ${response.code()} - ${response.body()}")
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e("WhatsAppAuth", "Send OTP Failed: ${e.message}")
            false
        }
    }

    /**
     * Hakiki kama OTP iliyoingizwa ni sahihi
     */
    suspend fun verifyOtp(phone: String, otp: String): Boolean {
        Log.d("WhatsAppAuth", "Verifying OTP $otp for $phone")
        return try {
            val response = apiService.verifyWhatsAppOtp(OtpVerifyRequest(phone, otp)).awaitResponse()
            Log.d("WhatsAppAuth", "Verify Response: ${response.code()}")
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e("WhatsAppAuth", "Verify OTP Failed: ${e.message}")
            false
        }
    }
}
