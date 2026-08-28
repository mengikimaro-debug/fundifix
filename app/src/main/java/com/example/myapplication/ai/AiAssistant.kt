package com.example.myapplication.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hapa ndipo tunapounganisha AI (kama Gemini au OpenAI).
 * Kwa sasa tumeweka simulizi ya jinsi AI inavyofanya kazi.
 */
object AiAssistant {
    private const val ollamaEndpoint = "http://192.168.1.109:5001/api/ai/ask"
    
    suspend fun getServiceAdvice(issueDescription: String): String {
        val aiReply = askOllama("Wewe ni msaidizi wa FundiFix. Toa ushauri mfupi na salama kwa Kiswahili kuhusu tatizo hili la huduma: $issueDescription")
        if (aiReply != null) return aiReply
        
        return if (issueDescription.contains("umeme", ignoreCase = true)) {
            "AI Inashauri: Shida ya umeme inaweza kuwa hatari. Hakikisha umezima swichi kuu (Main Switch) kabla fundi hajafika."
        } else if (issueDescription.contains("cctv", ignoreCase = true) || issueDescription.contains("camera", ignoreCase = true)) {
            "AI Inashauri: Hakikisha maeneo unayotaka kuweka kamera yana mwanga wa kutosha au kamera zina mfumo wa Night Vision."
        } else if (issueDescription.contains("ac", ignoreCase = true) || issueDescription.contains("air condition", ignoreCase = true)) {
            "AI Inashauri: Zima AC yako ikiwa inatoa sauti isiyo ya kawaida au maji yanavuja ndani ili kuzuia uharibifu wa compressor."
        } else {
            "AI Inashauri: Eleza shida kwa undani ili upate fundi bora na bei sahihi."
        }
    }

    suspend fun suggestPrice(serviceType: String): String {
        delay(1000)
        return when {
            serviceType.contains("AC Installation", true) -> "90,000"
            serviceType.contains("Electrical", true) -> "35,000"
            serviceType.contains("Dish", true) -> "25,000"
            serviceType.contains("CCTV", true) -> "50,000"
            serviceType.contains("Air Conditioning", true) || serviceType.contains("AC", true) -> "45,000"
            else -> "30,000"
        }
    }

    suspend fun getPriceWithCurrency(serviceType: String): String {
        val price = suggestPrice(serviceType)
        return "$price Tsh"
    }

    private suspend fun askOllama(prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(ollamaEndpoint).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 60000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("prompt", prompt).toString().toByteArray()
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode !in 200..299) return@withContext null
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            JSONObject(response).optString("reply").takeIf { it.isNotBlank() }
        } catch (error: Exception) {
            null
        }
    }
}
