# Android mTLS Implementation Guide

Step-by-step guide to implement mTLS client authentication in your FundiFix Android app.

## Overview

Your Android app will:
1. Load the client certificate (`.p12` keystore)
2. Load the CA certificate to verify the server
3. Create an SSL context with mutual certificate authentication
4. Use it with Retrofit/OkHttp to make HTTPS requests

## Prerequisites

- Android Studio
- Minimum SDK: API 21+
- Target SDK: API 33+
- Client certificates from backend (`client.p12`, `ca.pem`)

## Step-by-Step Implementation

### Step 1: Add Certificates to Android Project

1. **In Android Studio**, navigate to `app/src/main/res`
2. **Create a `raw` folder** (if it doesn't exist):
   - Right-click on `res` → New → Folder → Raw Resources Folder
3. **Copy certificate files:**
   - Copy `backend/certs/client.p12` to `app/src/main/res/raw/client.p12`
   - Copy `backend/certs/ca.crt` to `app/src/main/res/raw/ca_pem.pem` (rename to avoid extension issues)

### Step 2: Add Dependencies

Open `app/build.gradle` and add:

```gradle
dependencies {
    // ... existing dependencies
    
    // OkHttp for HTTPS/mTLS
    implementation 'com.squareup.okhttp3:okhttp:4.10.0'
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    
    // Android security (optional, for keystore management)
    implementation 'androidx.security:security-crypto:1.1.0-alpha06'
    
    // Logging (optional)
    implementation 'com.squareup.okhttp3:logging-interceptor:4.10.0'
}
```

Then sync Gradle.

### Step 3: Create Certificate Manager Class

Create file: `app/src/main/java/com/fundifix/network/CertificateManager.kt`

```kotlin
package com.fundifix.network

import android.content.Context
import android.util.Log
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class CertificateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CertificateManager"
        private const val KEYSTORE_PASSWORD = "changeit"  // Default password from generate-certs.js
        private const val KEYSTORE_TYPE = "PKCS12"
        private const val CERTIFICATE_TYPE = "X.509"
    }
    
    /**
     * Load client certificate from PKCS12 keystore (client.p12)
     */
    fun getClientKeyStore(): KeyStore {
        return try {
            Log.d(TAG, "Loading client keystore...")
            val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
            
            // Load client.p12 from raw resources
            val keystoreInputStream: InputStream = 
                context.resources.openRawResource(
                    context.resources.getIdentifier("client", "raw", context.packageName)
                )
            
            keyStore.load(keystoreInputStream, KEYSTORE_PASSWORD.toCharArray())
            keystoreInputStream.close()
            
            Log.d(TAG, "Client keystore loaded successfully. Size: ${keyStore.size()}")
            keyStore
        } catch (e: Exception) {
            Log.e(TAG, "Error loading client keystore: ${e.message}", e)
            throw RuntimeException("Failed to load client certificate", e)
        }
    }
    
    /**
     * Load CA certificate for server verification (ca.pem)
     */
    fun getTrustStore(): KeyStore {
        return try {
            Log.d(TAG, "Loading trust store (CA certificate)...")
            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
            trustStore.load(null, null)
            
            // Load CA certificate
            val certificateFactory = CertificateFactory.getInstance(CERTIFICATE_TYPE)
            val caInputStream: InputStream =
                context.resources.openRawResource(
                    context.resources.getIdentifier("ca_pem", "raw", context.packageName)
                )
            
            val certificate: Certificate? = certificateFactory.generateCertificate(caInputStream)
            caInputStream.close()
            
            if (certificate != null) {
                trustStore.setCertificateEntry("ca", certificate)
                
                val cert = certificate as X509Certificate
                Log.d(TAG, "CA Certificate loaded:")
                Log.d(TAG, "  Subject: ${cert.subjectDN}")
                Log.d(TAG, "  Issuer: ${cert.issuerDN}")
                Log.d(TAG, "  Valid from: ${cert.notBefore}")
                Log.d(TAG, "  Valid to: ${cert.notAfter}")
            }
            
            trustStore
        } catch (e: Exception) {
            Log.e(TAG, "Error loading trust store: ${e.message}", e)
            throw RuntimeException("Failed to load CA certificate", e)
        }
    }
    
    /**
     * Create SSLContext with mTLS configuration
     */
    fun createSSLContext(): SSLContext {
        return try {
            Log.d(TAG, "Creating SSL context with mTLS...")
            
            // Initialize key manager with client certificate
            val keyStore = getClientKeyStore()
            val keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm()
            )
            keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD.toCharArray())
            
            // Initialize trust manager with CA certificate
            val trustStore = getTrustStore()
            val trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            )
            trustManagerFactory.init(trustStore)
            
            // Create SSL context
            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(
                keyManagerFactory.keyManagers,
                trustManagerFactory.trustManagers,
                null
            )
            
            Log.d(TAG, "SSL context created successfully")
            sslContext
        } catch (e: Exception) {
            Log.e(TAG, "Error creating SSL context: ${e.message}", e)
            throw RuntimeException("Failed to create SSL context", e)
        }
    }
    
    /**
     * Get X509TrustManager for OkHttp configuration
     */
    fun getTrustManager(): X509TrustManager? {
        return try {
            val trustStore = getTrustStore()
            val trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            )
            trustManagerFactory.init(trustStore)
            
            val trustManagers = trustManagerFactory.trustManagers
            if (trustManagers.isNotEmpty()) {
                trustManagers[0] as? X509TrustManager
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting trust manager: ${e.message}", e)
            null
        }
    }
}
```

### Step 4: Create API Client with mTLS

Create file: `app/src/main/java/com/fundifix/network/ApiClient.kt`

```kotlin
package com.fundifix.network

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(private val context: Context) {
    
    companion object {
        private const val TAG = "ApiClient"
        private const val API_BASE_URL = "https://api.fundifix.com:5000/"  // Change to your domain
        private const val CONNECT_TIMEOUT = 10L
        private const val READ_TIMEOUT = 10L
        private const val WRITE_TIMEOUT = 10L
        
        private var instance: Retrofit? = null
    }
    
    /**
     * Create OkHttpClient with mTLS configuration
     */
    private fun createOkHttpClient(): OkHttpClient {
        return try {
            val certificateManager = CertificateManager(context)
            val sslContext = certificateManager.createSSLContext()
            val trustManager = certificateManager.getTrustManager()
            
            val logging = HttpLoggingInterceptor { message ->
                Log.d(TAG, message)
            }
            logging.level = HttpLoggingInterceptor.Level.BODY
            
            OkHttpClient.Builder()
                .sslSocketFactory(
                    sslContext.socketFactory,
                    trustManager ?: throw IllegalStateException("Trust manager not initialized")
                )
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating OkHttpClient: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Get or create Retrofit instance with mTLS
     */
    fun getRetrofit(): Retrofit {
        return instance ?: run {
            val retrofit = Retrofit.Builder()
                .baseUrl(API_BASE_URL)
                .client(createOkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            instance = retrofit
            Log.d(TAG, "Retrofit instance created with mTLS")
            retrofit
        }
    }
    
    /**
     * Create API service instance
     */
    fun <T> createService(apiClass: Class<T>): T {
        return getRetrofit().create(apiClass)
    }
}
```

### Step 5: Define API Service Interface

Create file: `app/src/main/java/com/fundifix/api/FundiFix ApiService.kt`

```kotlin
package com.fundifix.api

import retrofit2.Call
import retrofit2.http.*

data class LoginRequest(
    val phone: String,
    val password: String
)

data class LoginResponse(
    val success: Boolean,
    val phone: String?,
    val role: String?,
    val method: String?,
    val name: String?,
    val message: String?
)

data class OTPRequest(
    val phone: String
)

data class OTPResponse(
    val success: Boolean,
    val message: String
)

data class VerifyOTPRequest(
    val phone: String,
    val otp: String
)

data class VerifyOTPResponse(
    val success: Boolean,
    val message: String
)

interface FundiFix ApiService {
    
    @POST("login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>
    
    @POST("auth/sms/send")
    fun sendSmsOTP(@Body request: OTPRequest): Call<OTPResponse>
    
    @POST("auth/sms/verify")
    fun verifySmsOTP(@Body request: VerifyOTPRequest): Call<VerifyOTPResponse>
    
    @POST("auth/whatsapp/send")
    fun sendWhatsAppOTP(@Body request: OTPRequest): Call<OTPResponse>
    
    @POST("auth/whatsapp/verify")
    fun verifyWhatsAppOTP(@Body request: VerifyOTPRequest): Call<VerifyOTPResponse>
    
    @GET("active-requests")
    fun getActiveRequests(): Call<List<ServiceRequest>>
    
    @GET("client-requests/{phone}")
    fun getClientRequests(@Path("phone") phone: String): Call<List<ServiceRequest>>
}

data class ServiceRequest(
    val id: String,
    val service: String,
    val desc: String,
    val clientPhone: String,
    val price: String,
    val location: String,
    val bookingDate: String,
    val status: String
)
```

### Step 6: Use API Client in Your Activity

Example: `app/src/main/java/com/fundifix/LoginActivity.kt`

```kotlin
package com.fundifix

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.fundifix.api.FundiFix ApiService
import com.fundifix.api.LoginRequest
import com.fundifix.network.ApiClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "LoginActivity"
    }
    
    private lateinit var apiService: FundiFix ApiService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize API client with mTLS
        try {
            val apiClient = ApiClient(this)
            apiService = apiClient.createService(FundiFix ApiService::class.java)
            Log.d(TAG, "API Service initialized with mTLS")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize API client: ${e.message}", e)
            // Show error to user
            return
        }
    }
    
    /**
     * Example: Login with mTLS
     */
    private fun loginUser(phone: String, password: String) {
        val request = LoginRequest(phone, password)
        
        apiService.login(request).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    Log.d(TAG, "Login successful: ${loginResponse?.phone}")
                    // Handle successful login
                } else {
                    Log.e(TAG, "Login failed: ${response.code()}")
                    // Handle error
                }
            }
            
            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                Log.e(TAG, "Login request failed: ${t.message}", t)
                // Handle network error
            }
        })
    }
    
    /**
     * Example: Send SMS OTP with mTLS
     */
    private fun sendOTP(phone: String) {
        val request = OTPRequest(phone)
        
        apiService.sendSmsOTP(request).enqueue(object : Callback<OTPResponse> {
            override fun onResponse(call: Call<OTPResponse>, response: Response<OTPResponse>) {
                if (response.isSuccessful) {
                    Log.d(TAG, "OTP sent successfully")
                } else {
                    Log.e(TAG, "Failed to send OTP: ${response.code()}")
                }
            }
            
            override fun onFailure(call: Call<OTPResponse>, t: Throwable) {
                Log.e(TAG, "OTP request failed: ${t.message}", t)
            }
        })
    }
}
```

## Testing mTLS Connection

### Test from Android Emulator

1. **Start your backend with mTLS:**
   ```bash
   cd backend
   USE_MTLS=true npm start
   ```

2. **Update API_BASE_URL** in `ApiClient.kt`:
   ```kotlin
   private const val API_BASE_URL = "https://10.0.2.2:5000/"  // Emulator localhost
   ```

3. **Run the app** in Android Emulator:
   - Open Logcat (View → Tool Windows → Logcat)
   - Run your app
   - Check logs for "SSL context created successfully"
   - Make a login request
   - Monitor logs for any SSL errors

### Test from Physical Device

1. **Update API_BASE_URL** to your server's IP/domain:
   ```kotlin
   private const val API_BASE_URL = "https://your-server-ip:5000/"
   ```

2. **Ensure device can reach server:**
   ```bash
   # On your machine
   adb shell ping your-server-ip
   ```

3. **Test with curl first** to verify server is working:
   ```bash
   curl --cert backend/certs/client.crt \
        --key backend/certs/client.key \
        --cacert backend/certs/ca.crt \
        https://your-server-ip:5000/
   ```

## Debugging Common Issues

### Issue 1: "CERTIFICATE_VERIFY_FAILED"

**Cause:** Client certificate not loaded or CA certificate not trusted.

**Fix:**
```kotlin
// In CertificateManager, add debugging:
Log.d(TAG, "Keystore entries:")
val aliases = keyStore.aliases()
while (aliases.hasMoreElements()) {
    Log.d(TAG, "  - ${aliases.nextElement()}")
}
```

### Issue 2: "SSLHandshakeException"

**Cause:** Server certificate verification failed.

**Fix:**
- Verify CA certificate is copied correctly
- Check certificate paths in logcat
- For self-signed certificates, ensure ca.pem is the correct file

### Issue 3: "CLIENT_CERT_REQUIRED"

**Cause:** Client certificate not being sent to server.

**Fix:**
- Verify client.p12 is in `app/src/main/res/raw/`
- Check KEYSTORE_PASSWORD matches (default: "changeit")
- Enable logging interceptor to see request headers

### Issue 4: "Certificate CN mismatch"

**Cause:** Certificate CN doesn't match hostname.

**Fix:** For development, use localhost/10.0.2.2. For production, use actual domain name.

## Enable Debug Logging

Add this to `ApiClient.kt` to see all SSL/TLS details:

```kotlin
// In createOkHttpClient()
System.setProperty("javax.net.debug", "ssl")  // For full SSL debugging
```

Check logcat with:
```bash
adb logcat | grep -i ssl
```

## Production Checklist

- ✅ Use Let's Encrypt certificates (not self-signed)
- ✅ Use actual domain name (not localhost)
- ✅ Enable certificate pinning (optional but recommended)
- ✅ Use strong passwords for keystores (at least 16 characters)
- ✅ Implement certificate rotation mechanism
- ✅ Monitor certificate expiration dates
- ✅ Test on real device before deployment
- ✅ Never commit certificates to git

## Certificate Pinning (Optional but Recommended)

For additional security, implement certificate pinning:

```kotlin
import okhttp3.CertificatePinner

// In createOkHttpClient()
val certificatePinner = CertificatePinner.Builder()
    .add("api.fundifix.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .build()

OkHttpClient.Builder()
    .certificatePinner(certificatePinner)
    // ... rest of configuration
    .build()
```

Get the pin by running:
```bash
openssl x509 -in server.crt -noout -pubkey | \
  openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | \
  openssl enc -base64
```

## Next Steps

1. ✅ Copy certificates to Android project
2. ✅ Add OkHttp/Retrofit dependencies
3. ✅ Implement CertificateManager
4. ✅ Implement ApiClient with mTLS
5. ✅ Create API service interface
6. ✅ Test with backend running mTLS
7. ✅ Deploy to production

For backend setup, see: `backend/MTLS_SETUP.md`
