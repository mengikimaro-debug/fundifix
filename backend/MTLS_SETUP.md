# mTLS (Mutual TLS) Setup Guide for FundiFix

Complete guide for implementing Mutual TLS with Let's Encrypt certificates for production and self-signed certificates for development.

## Table of Contents
1. [Overview](#overview)
2. [Development Setup](#development-setup)
3. [Production Setup with Let's Encrypt](#production-setup)
4. [Android App Configuration](#android-app-configuration)
5. [Testing](#testing)
6. [Troubleshooting](#troubleshooting)

---

## Overview

mTLS (Mutual TLS) requires BOTH the server and client to authenticate each other using certificates. This provides:
- ✅ Server authentication (client verifies server identity)
- ✅ Client authentication (server verifies client identity)
- ✅ Encrypted communication between client and server
- ✅ Protection against man-in-the-middle attacks

### Architecture
```
┌─────────────────┐         mTLS         ┌──────────────┐
│  Android App    │◄──────────────────────►│ FundiFix API │
│ (Client Cert)   │  Encrypted + Auth     │(Server Cert) │
└─────────────────┘                       └──────────────┘
```

---

## Development Setup

### Quick Start (Self-Signed Certificates)

1. **Generate self-signed certificates:**
   ```bash
   cd backend
   npm run generate-certs
   ```

   This creates self-signed certificates in `backend/certs/`:
   - `ca.crt` - Certificate Authority cert
   - `server.crt` & `server.key` - Server certificates
   - `client.crt` & `client.key` - Client certificates
   - `client.p12` - Android keystore (password: `changeit`)

2. **Enable mTLS in your backend:**
   ```bash
   # Start with mTLS enabled
   USE_MTLS=true npm start
   
   # Or development mode without mTLS
   npm run dev
   ```

3. **Verify certificates were generated:**
   ```bash
   ls -la backend/certs/
   ```

### Environment Variables

```bash
# Enable mTLS in production
USE_MTLS=true

# Server port (default: 5000)
PORT=5000

# MongoDB connection
MONGODB_URI=mongodb://127.0.0.1:27017/fundifix
```

### File Structure
```
backend/
├── certs/                    # Generated certificates (gitignore this!)
│   ├── ca.crt
│   ├── ca.key
│   ├── server.crt
│   ├── server.key
│   ├── client.crt
│   ├── client.key
│   └── client.p12            # For Android
├── scripts/
│   └── generate-certs.js     # Certificate generation script
└── server.js                 # Updated with HTTPS/mTLS support
```

---

## Production Setup with Let's Encrypt

### Prerequisites
- Domain name (e.g., `api.fundifix.com`)
- Ubuntu/Linux server with root access
- Certbot installed

### Step 1: Install Certbot

```bash
sudo apt-get update
sudo apt-get install certbot python3-certbot-nginx
```

### Step 2: Create Production Certificates

#### Option A: Using Certbot (Recommended)

```bash
# For server certificate
sudo certbot certonly --standalone -d api.fundifix.com

# This creates certificates in:
# /etc/letsencrypt/live/api.fundifix.com/
# ├── cert.pem        (server certificate)
# ├── privkey.pem     (server private key)
# ├── chain.pem       (CA chain)
# └── fullchain.pem   (cert + CA chain)
```

#### For Client Certificates (mTLS):
You have two options:

**Option 1: Self-signed client certificates (simpler)**
```bash
# Generate client certificates with the Let's Encrypt CA
mkdir -p /home/ubuntu/certs
cd /home/ubuntu/certs

# Generate client key
openssl genrsa -out client.key 2048

# Generate client CSR
openssl req -new -key client.key -out client.csr \
  -subj "/CN=android-client"

# Sign with Let's Encrypt CA (you need the CA key)
# This is complex - Option 2 is easier for production
```

**Option 2: Use an intermediary CA (Production-ready)**
```bash
# Install step-ca for more robust mTLS
curl -L https://dl.step.sm/gh-release/cli/releases/latest/step_linux_0.24.4_amd64.tar.gz | tar xz

# Create a private CA for client certificates
step ca init --deployment-type standalone

# Issue client certificates signed by your private CA
step ca certificate android-client client.crt client.key
```

### Step 3: Configure Backend

```bash
# Create certs directory
mkdir -p /home/ubuntu/fundifix/backend/certs

# Copy Let's Encrypt certificates
sudo cp /etc/letsencrypt/live/api.fundifix.com/privkey.pem /home/ubuntu/fundifix/backend/certs/server.key
sudo cp /etc/letsencrypt/live/api.fundifix.com/cert.pem /home/ubuntu/fundifix/backend/certs/server.crt
sudo cp /etc/letsencrypt/live/api.fundifix.com/chain.pem /home/ubuntu/fundifix/backend/certs/ca.crt

# Fix permissions
sudo chown ubuntu:ubuntu /home/ubuntu/fundifix/backend/certs/*
chmod 600 /home/ubuntu/fundifix/backend/certs/*
```

### Step 4: Auto-renewal

Let's Encrypt certificates expire after 90 days. Set up auto-renewal:

```bash
# Create renewal script
sudo tee /usr/local/bin/renew-fundifix-certs.sh > /dev/null <<'EOF'
#!/bin/bash

# Renew Let's Encrypt certificate
certbot renew --quiet

# Copy to app directory
cp /etc/letsencrypt/live/api.fundifix.com/privkey.pem /home/ubuntu/fundifix/backend/certs/server.key
cp /etc/letsencrypt/live/api.fundifix.com/cert.pem /home/ubuntu/fundifix/backend/certs/server.crt
cp /etc/letsencrypt/live/api.fundifix.com/chain.pem /home/ubuntu/fundifix/backend/certs/ca.crt

# Fix permissions
chown ubuntu:ubuntu /home/ubuntu/fundifix/backend/certs/*
chmod 600 /home/ubuntu/fundifix/backend/certs/*

# Restart server (if using PM2)
su - ubuntu -c "cd /home/ubuntu/fundifix/backend && npm restart"
EOF

sudo chmod +x /usr/local/bin/renew-fundifix-certs.sh

# Add to crontab (runs daily at 3 AM)
sudo crontab -e
# Add line: 0 3 * * * /usr/local/bin/renew-fundifix-certs.sh
```

---

## Android App Configuration

### Step 1: Add Certificate to Android App

1. **Copy the certificate to your Android app:**
   ```bash
   # Copy client.p12 (or client.bks for BKS format)
   cp backend/certs/client.p12 app/src/main/res/raw/client.p12
   
   # Also copy CA certificate for server verification
   cp backend/certs/ca.crt app/src/main/res/raw/ca.pem
   ```

2. **Add dependencies to `app/build.gradle`:**
   ```gradle
   dependencies {
       // ... existing dependencies
       implementation 'androidx.security:security-crypto:1.1.0-alpha06'
   }
   ```

### Step 2: Create Certificate Loader Helper

Create file: `app/src/main/java/com/fundifix/network/CertificateHelper.kt`

```kotlin
package com.fundifix.network

import android.content.Context
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

object CertificateHelper {
    
    /**
     * Load client certificate from PKCS12 keystore
     */
    fun getClientKeyStore(context: Context, keystoreName: String = "client.p12", password: String = "changeit"): KeyStore {
        val keyStore = KeyStore.getInstance("PKCS12")
        val inputStream: InputStream = context.resources.openRawResource(
            context.resources.getIdentifier(
                keystoreName.replace(".p12", ""),
                "raw",
                context.packageName
            )
        )
        keyStore.load(inputStream, password.toCharArray())
        inputStream.close()
        return keyStore
    }
    
    /**
     * Load CA certificate for server verification
     */
    fun getTrustStore(context: Context, certName: String = "ca"): KeyStore {
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
        trustStore.load(null, null)
        
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val inputStream: InputStream = context.resources.openRawResource(
            context.resources.getIdentifier(certName, "raw", context.packageName)
        )
        
        val certificate = certificateFactory.generateCertificate(inputStream)
        inputStream.close()
        
        trustStore.setCertificateEntry("ca", certificate)
        return trustStore
    }
    
    /**
     * Create SSLContext with mTLS
     */
    fun createSSLContext(context: Context): SSLContext {
        // Load client certificates
        val keyStore = getClientKeyStore(context)
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, "changeit".toCharArray())
        
        // Load server CA certificate
        val trustStore = getTrustStore(context)
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(trustStore)
        
        // Create SSL context
        return SSLContext.getInstance("TLSv1.2").apply {
            init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, null)
        }
    }
}
```

### Step 3: Update Retrofit/OkHttp Configuration

If using OkHttp (Retrofit):

```kotlin
package com.fundifix.network

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    
    fun getClientWithMTLS(context: Context): OkHttpClient {
        val sslContext = CertificateHelper.createSSLContext(context)
        
        return OkHttpClient.Builder()
            .sslSocketFactory(
                sslContext.socketFactory,
                TrustAllCertificates()  // Or use proper trust manager
            )
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
    
    fun getRetrofit(context: Context): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.fundifix.com:5000/")  // Use HTTPS!
            .client(getClientWithMTLS(context))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}

// For development only - verify certificates in production!
class TrustAllCertificates : javax.net.ssl.X509TrustManager {
    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate>? = null
}
```

### Step 4: Use in Your API Service

```kotlin
// In your Android Activity/Fragment
val retrofit = RetrofitClient.getRetrofit(context)
val apiService = retrofit.create(YourApiService::class.java)

// Make HTTPS requests with mTLS
apiService.loginUser(phone, password)
    .enqueue(object : Callback<LoginResponse> {
        override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
            // Handle response
        }
        
        override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
            // Handle error
        }
    })
```

---

## Testing

### Test Server with mTLS

1. **Test with curl:**
   ```bash
   # Without client cert (should fail)
   curl -k https://localhost:5000/
   
   # With client cert (should succeed)
   curl --cert backend/certs/client.crt \
        --key backend/certs/client.key \
        --cacert backend/certs/ca.crt \
        https://localhost:5000/
   ```

2. **Test with Node.js:**
   ```javascript
   const https = require('https');
   const fs = require('fs');
   
   const options = {
       hostname: 'localhost',
       port: 5000,
       path: '/',
       method: 'GET',
       cert: fs.readFileSync('certs/client.crt'),
       key: fs.readFileSync('certs/client.key'),
       ca: fs.readFileSync('certs/ca.crt'),
       rejectUnauthorized: true
   };
   
   https.request(options, (res) => {
       console.log(res.statusCode);
       res.on('data', d => process.stdout.write(d));
   }).end();
   ```

3. **Test Android App:**
   - Run the app in emulator/device
   - Check Logcat for SSL errors
   - Verify certificate is loaded correctly

---

## Troubleshooting

### Common Issues

#### 1. "CERTIFICATE_VERIFY_FAILED"
```
Error: CERTIFICATE_VERIFY_FAILED
```
**Solution:** Ensure CA certificate is properly loaded and trusted.

```bash
# Verify certificate chain
openssl verify -CAfile certs/ca.crt certs/server.crt
```

#### 2. "CLIENT_CERT_REQUIRED"
```
Error: CLIENT_CERT_REQUIRED
```
**Solution:** Ensure client certificate is being sent.

```bash
# Check if client cert is included
curl -v --cert certs/client.crt --key certs/client.key https://localhost:5000/
```

#### 3. "SSL_ERROR_BAD_CERT_DOMAIN"
```
Error: SSL_ERROR_BAD_CERT_DOMAIN
```
**Solution:** Certificate CN doesn't match domain. Use correct domain in certificate.

```bash
# Regenerate with correct domain
openssl req -new -key server.key -out server.csr -subj "/CN=your-domain.com"
```

#### 4. Android: "Javax.net.ssl.SSLHandshakeException"
**Solution:** Check certificate formats and ensure they're properly loaded:

```kotlin
// Verify certificate loading
val keyStore = CertificateHelper.getClientKeyStore(context)
Log.d("Certs", "Keystore size: ${keyStore.size()}")
```

#### 5. Certificate Expired
```bash
# Check expiration
openssl x509 -in certs/server.crt -text -noout | grep -i validity

# For Let's Encrypt, renew:
sudo certbot renew --force-renewal
```

### Debug Logging

Enable SSL debugging:

```bash
# Node.js
NODE_DEBUG=tls npm start

# Java/Android
adb shell setprop log.tag.CertificateHelper DEBUG
```

---

## Security Best Practices

✅ **DO:**
- Use strong passwords for keystores (at least 16 characters)
- Rotate certificates regularly
- Keep private keys secure (.gitignore them!)
- Use Let's Encrypt for production
- Implement certificate pinning on Android
- Monitor certificate expiration dates

❌ **DON'T:**
- Commit private keys to git
- Use development certificates in production
- Share keystores/passwords
- Disable certificate verification
- Use TLS versions below 1.2

---

## Certificate Files Reference

| File | Purpose | Keep Secret |
|------|---------|-------------|
| `ca.crt` | Certificate Authority cert | No |
| `ca.key` | CA private key | **YES** |
| `server.crt` | Server public certificate | No |
| `server.key` | Server private key | **YES** |
| `client.crt` | Client public certificate | No |
| `client.key` | Client private key | **YES** |
| `client.p12` | Android keystore (encrypted) | **YES** |

---

## Next Steps

1. ✅ Generate certificates: `npm run generate-certs`
2. ✅ Enable mTLS: `USE_MTLS=true npm start`
3. ✅ Copy certificates to Android app
4. ✅ Update Android API client
5. ✅ Test end-to-end
6. ✅ Deploy to production with Let's Encrypt

For production deployment, ensure all certificates are renewed before expiration and monitoring is in place.
