# FundiFix mTLS Setup - Quick Start Guide

Complete implementation of Mutual TLS (mTLS) for your FundiFix backend and Android app.

## 📋 Quick Summary

You now have:
- ✅ **Backend Server** with HTTPS/mTLS support
- ✅ **Certificate Generation Script** for self-signed certs
- ✅ **Android App Integration** with certificate management
- ✅ **Production Guide** for Let's Encrypt certificates

## 🚀 Getting Started

### 1. Generate Certificates (Development)

```bash
cd backend
npm run generate-certs
```

This creates:
- `backend/certs/ca.crt` - Certificate Authority
- `backend/certs/server.crt` & `server.key` - Server certificates
- `backend/certs/client.crt` & `client.key` - Client certificates  
- `backend/certs/client.p12` - Android keystore (password: `changeit`)

### 2. Start Backend with mTLS

```bash
# Enable mTLS mode
USE_MTLS=true npm start

# Or development mode (no mTLS)
npm run dev
```

### 3. Setup Android App

1. Copy `backend/certs/client.p12` to `app/src/main/res/raw/client.p12`
2. Copy `backend/certs/ca.crt` to `app/src/main/res/raw/ca_pem.pem`
3. Add OkHttp/Retrofit dependencies
4. Implement `CertificateManager` and `ApiClient` classes
5. Use API client in your activities

## 📚 Documentation

### For Backend Setup
See: **[backend/MTLS_SETUP.md](backend/MTLS_SETUP.md)**

Topics covered:
- ✅ Development certificate generation
- ✅ Production setup with Let's Encrypt
- ✅ Auto-renewal configuration
- ✅ Testing with curl
- ✅ Troubleshooting

### For Android Implementation
See: **[ANDROID_MTLS_IMPLEMENTATION.md](ANDROID_MTLS_IMPLEMENTATION.md)**

Topics covered:
- ✅ Certificate integration into Android project
- ✅ OkHttp/Retrofit configuration
- ✅ Certificate manager implementation
- ✅ API service creation
- ✅ Testing and debugging

## 🔧 Configuration Files

### Backend
```
backend/
├── server.js                    # Updated with HTTPS/mTLS support
├── package.json                 # Added npm scripts
├── scripts/
│   └── generate-certs.js        # Certificate generation tool
├── MTLS_SETUP.md               # Detailed backend guide
└── certs/                       # Generated certificates (.gitignored)
    ├── ca.crt
    ├── ca.key
    ├── server.crt
    ├── server.key
    ├── client.crt
    ├── client.key
    └── client.p12               # For Android
```

### Android
```
app/src/main/
├── java/
│   └── com/fundifix/
│       ├── network/
│       │   ├── CertificateManager.kt      # Certificate loading
│       │   └── ApiClient.kt               # Retrofit/OkHttp setup
│       └── api/
│           └── FundiFix ApiService.kt     # API endpoints
└── res/
    └── raw/
        ├── client.p12                      # Client keystore
        └── ca_pem.pem                      # CA certificate
```

## 🔐 Security Important!

### Private Keys - Keep Secret!
```
backend/certs/ca.key              # DO NOT COMMIT
backend/certs/server.key          # DO NOT COMMIT
backend/certs/client.key          # DO NOT COMMIT
backend/certs/client.p12          # DO NOT COMMIT
```

✅ **All already added to `.gitignore`**

### Environment Variables

```bash
# Enable mTLS
export USE_MTLS=true

# Server port
export PORT=5000

# MongoDB connection
export MONGODB_URI=mongodb://127.0.0.1:27017/fundifix
```

## 🧪 Testing

### Test Backend

```bash
# Test without client cert (should fail)
curl -k https://localhost:5000/

# Test with client cert (should succeed)
curl --cert backend/certs/client.crt \
     --key backend/certs/client.key \
     --cacert backend/certs/ca.crt \
     https://localhost:5000/
```

### Test Android App

1. Copy certificates to `app/src/main/res/raw/`
2. Run app in emulator or device
3. Check Logcat for SSL errors:
   ```bash
   adb logcat | grep -i certificate
   ```

## 📱 API Endpoints (HTTPS)

```
https://api.fundifix.com:5000/

POST   /login
POST   /register
POST   /submit-request
GET    /active-requests
GET    /client-requests/:phone
POST   /auth/sms/send
POST   /auth/sms/verify
POST   /auth/whatsapp/send
POST   /auth/whatsapp/verify
```

## 🌐 Production Deployment

1. **Get Let's Encrypt certificates:**
   ```bash
   sudo certbot certonly --standalone -d api.fundifix.com
   ```

2. **Configure backend:**
   - Copy certificates to `backend/certs/`
   - Set `USE_MTLS=true`
   - Deploy with Docker/PM2

3. **Update Android app:**
   - Use production domain
   - Update `API_BASE_URL` in `ApiClient.kt`
   - Use production certificates

See full guide in: **[backend/MTLS_SETUP.md](backend/MTLS_SETUP.md)**

## ⚙️ Common Tasks

### Regenerate Certificates
```bash
rm -rf backend/certs/
npm run generate-certs
```

### Update Certificate Password
Edit `backend/scripts/generate-certs.js`:
```javascript
-passout pass:changeit  // Change 'changeit' to your password
```

And `app/src/main/java/com/fundifix/network/CertificateManager.kt`:
```kotlin
private const val KEYSTORE_PASSWORD = "changeit"  // Update here
```

### Check Certificate Validity
```bash
openssl x509 -in backend/certs/server.crt -text -noout | grep -A2 "Validity"
```

### Convert P12 to PEM
```bash
openssl pkcs12 -in backend/certs/client.p12 -out client.pem -nodes
```

## 🐛 Troubleshooting

### "CERTIFICATE_VERIFY_FAILED"
- Ensure CA certificate is in Android project
- Check certificate path in Logcat
- Verify certificate is properly loaded

### "CLIENT_CERT_REQUIRED"
- Ensure client.p12 is in `app/src/main/res/raw/`
- Check KEYSTORE_PASSWORD matches
- Verify keystore format is PKCS12

### "SSL_ERROR_BAD_CERT_DOMAIN"
- For development: use `localhost` or `127.0.0.1`
- For production: use actual domain name
- Regenerate certificates if domain changed

### SSL Debug Logs
```bash
# Node.js
NODE_DEBUG=tls npm start

# Android Logcat
adb logcat | grep -i ssl
```

## 📞 Support

Need help? Check:
1. **[backend/MTLS_SETUP.md](backend/MTLS_SETUP.md)** - Backend guide
2. **[ANDROID_MTLS_IMPLEMENTATION.md](ANDROID_MTLS_IMPLEMENTATION.md)** - Android guide
3. OpenSSL documentation: https://www.openssl.org/docs/
4. Android SSL guide: https://developer.android.com/training/articles/security-ssl

## 📦 Files Modified

- ✅ `backend/server.js` - Added HTTPS/mTLS support
- ✅ `backend/package.json` - Added scripts
- ✅ `backend/scripts/generate-certs.js` - Certificate generation
- ✅ `backend/MTLS_SETUP.md` - Backend documentation
- ✅ `ANDROID_MTLS_IMPLEMENTATION.md` - Android documentation
- ✅ `.gitignore` - Added cert files

## 🎉 Next Steps

1. Generate certificates: `npm run generate-certs`
2. Start backend: `USE_MTLS=true npm start`
3. Copy certs to Android: `app/src/main/res/raw/`
4. Implement `CertificateManager` and `ApiClient`
5. Test HTTPS connection
6. Deploy to production with Let's Encrypt

Happy coding! 🚀
