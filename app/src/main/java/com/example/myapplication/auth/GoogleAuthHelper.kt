package com.example.myapplication.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleAuthHelper {
    
    // MUHIMU: Unahitaji Web Client ID kutoka Google Cloud Console (Firebase)
    // Ingia hapa kupata: https://console.cloud.google.com/
    private const val WEB_CLIENT_ID = "299132984063-bvaa4uujnm3ffh6ctb8rfhnsphgn1tv3.apps.googleusercontent.com"

    suspend fun signInWithGoogle(context: Context): String? {
        val credentialManager = CredentialManager.create(context)
        
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = context,
                request = request
            )
            
            val credential = result.credential
            if (credential is GoogleIdTokenCredential) {
                Log.d("GoogleAuth", "Success: ${credential.displayName} (${credential.id})")
                // Hapa ndipo unarudisha email au ID token kwa server yako
                credential.id
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("GoogleAuth", "Error during sign in: ${e.message}")
            null
        }
    }
}
