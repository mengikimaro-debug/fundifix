package com.example.myapplication.network

import com.example.myapplication.model.*
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("/register") 
    fun register(@Body data: UserData): Call<GenericResponse>

    @POST("/register/verify")
    fun verifyRegistration(@Body req: OtpVerifyRequest): Call<GenericResponse>

    @POST("/login")
    fun login(@Body data: PasswordLoginRequest): Call<UserData>
    
    @POST("/submit-request") 
    fun submitRequest(@Body req: ServiceReq): Call<GenericResponse>

    @POST("/auth/whatsapp/send")
    fun sendWhatsAppOtp(@Body req: OtpRequest): Call<GenericResponse>

    @POST("/auth/whatsapp/verify")
    fun verifyWhatsAppOtp(@Body req: OtpVerifyRequest): Call<GenericResponse>

    @POST("/auth/sms/send")
    fun sendSmsOtp(@Body req: OtpRequest): Call<GenericResponse>

    @POST("/auth/sms/verify")
    fun verifySmsOtp(@Body req: OtpVerifyRequest): Call<GenericResponse>

    @POST("/auth/otp/resend")
    fun resendOtp(@Body req: OtpRequest): Call<GenericResponse>
    
    @GET("/active-requests") 
    fun getRequests(): Call<List<ServiceReq>>

    @GET("/client-requests/{phone}")
    fun getClientRequests(@Path("phone") phone: String): Call<List<ServiceReq>>

    @POST("/accept-request/{id}")
    fun acceptRequest(@Path("id") id: String): Call<GenericResponse>

    @POST("/reject-request/{id}")
    fun rejectRequest(@Path("id") id: String): Call<GenericResponse>

    @POST("/update-request-price/{id}")
    fun updateRequestPrice(@Path("id") id: String, @Body price: Map<String, String>): Call<GenericResponse>

    companion object {
        /**
         * MUHIMU KWA MAJARIBIO:
         * 1. Emulator: Tumia "http://10.0.2.2:5000"
         * 2. Simu Halisi (Real Device): Tumia IP ya kompyuta yako, mfano "http://192.168.1.10:5000"
         *    Hakikisha simu na kompyuta zipo kwenye Wi-Fi moja.
         * 3. Production: Tumia URL ya server (mfano: https://api.fundifix.co.tz)
         */
        private const val BASE_URL = "https://ta-connect.onrender.com/"

        fun create(): ApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}
