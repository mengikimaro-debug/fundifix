#!/usr/bin/env node

/**
 * Certificate Generation Script for mTLS Setup
 * 
 * This script generates certificates for mTLS (Mutual TLS) setup.
 * For production with Let's Encrypt, see the MTLS_SETUP.md guide.
 * 
 * Usage:
 *   node scripts/generate-certs.js
 *   npm run generate-certs
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const CERT_DIR = path.join(__dirname, '..', 'certs');

// Ensure certs directory exists
if (!fs.existsSync(CERT_DIR)) {
	fs.mkdirSync(CERT_DIR, { recursive: true });
	console.log(`✅ Created certificates directory: ${CERT_DIR}`);
}

console.log('🔐 Generating self-signed certificates for development/testing...\n');

try {
	// Generate CA key and certificate
	if (!fs.existsSync(path.join(CERT_DIR, 'ca.key'))) {
		console.log('📝 Generating CA private key...');
		execSync(`openssl genrsa -out "${path.join(CERT_DIR, 'ca.key')}" 2048`, { stdio: 'inherit' });
	}

	if (!fs.existsSync(path.join(CERT_DIR, 'ca.crt'))) {
		console.log('📝 Generating CA certificate...');
		execSync(`openssl req -new -x509 -days 3650 -key "${path.join(CERT_DIR, 'ca.key')}" -out "${path.join(CERT_DIR, 'ca.crt')}" -subj "/CN=FundiFix-CA"`, { stdio: 'inherit' });
	}

	// Generate server key and certificate
	if (!fs.existsSync(path.join(CERT_DIR, 'server.key'))) {
		console.log('📝 Generating server private key...');
		execSync(`openssl genrsa -out "${path.join(CERT_DIR, 'server.key')}" 2048`, { stdio: 'inherit' });
	}

	if (!fs.existsSync(path.join(CERT_DIR, 'server.csr'))) {
		console.log('📝 Generating server CSR...');
		execSync(`openssl req -new -key "${path.join(CERT_DIR, 'server.key')}" -out "${path.join(CERT_DIR, 'server.csr')}" -subj "/CN=localhost"`, { stdio: 'inherit' });
	}

	if (!fs.existsSync(path.join(CERT_DIR, 'server.crt'))) {
		console.log('📝 Signing server certificate with CA...');
		execSync(`openssl x509 -req -days 365 -in "${path.join(CERT_DIR, 'server.csr')}" -CA "${path.join(CERT_DIR, 'ca.crt')}" -CAkey "${path.join(CERT_DIR, 'ca.key')}" -CAcreateserial -out "${path.join(CERT_DIR, 'server.crt')}"`, { stdio: 'inherit' });
	}

	// Generate client key and certificate
	if (!fs.existsSync(path.join(CERT_DIR, 'client.key'))) {
		console.log('📝 Generating client private key...');
		execSync(`openssl genrsa -out "${path.join(CERT_DIR, 'client.key')}" 2048`, { stdio: 'inherit' });
	}

	if (!fs.existsSync(path.join(CERT_DIR, 'client.csr'))) {
		console.log('📝 Generating client CSR...');
		execSync(`openssl req -new -key "${path.join(CERT_DIR, 'client.key')}" -out "${path.join(CERT_DIR, 'client.csr')}" -subj "/CN=android-client"`, { stdio: 'inherit' });
	}

	if (!fs.existsSync(path.join(CERT_DIR, 'client.crt'))) {
		console.log('📝 Signing client certificate with CA...');
		execSync(`openssl x509 -req -days 365 -in "${path.join(CERT_DIR, 'client.csr')}" -CA "${path.join(CERT_DIR, 'ca.crt')}" -CAkey "${path.join(CERT_DIR, 'ca.key')}" -CAcreateserial -out "${path.join(CERT_DIR, 'client.crt')}"`, { stdio: 'inherit' });
	}

	// Create P12 file for Android
	if (!fs.existsSync(path.join(CERT_DIR, 'client.p12'))) {
		console.log('📝 Creating PKCS12 keystore for Android...');
		execSync(`openssl pkcs12 -export -in "${path.join(CERT_DIR, 'client.crt')}" -inkey "${path.join(CERT_DIR, 'client.key')}" -out "${path.join(CERT_DIR, 'client.p12')}" -name android-client -passout pass:changeit`, { stdio: 'inherit' });
	}

	console.log('\n✅ Certificate generation complete!\n');
	console.log('📁 Generated files:');
	console.log(`   - ${CERT_DIR}/ca.crt (CA certificate)');
	console.log(`   - ${CERT_DIR}/ca.key (CA private key)`);
	console.log(`   - ${CERT_DIR}/server.crt (Server certificate)`);
	console.log(`   - ${CERT_DIR}/server.key (Server private key)`);
	console.log(`   - ${CERT_DIR}/client.crt (Client certificate)`);
	console.log(`   - ${CERT_DIR}/client.key (Client private key)`);
	console.log(`   - ${CERT_DIR}/client.p12 (Android keystore - password: changeit)\n`);
	console.log('⚠️  DEVELOPMENT ONLY - These are self-signed certificates!\n');
	console.log('📚 For production with Let\'s Encrypt, see: MTLS_SETUP.md\n');

} catch (error) {
	console.error('❌ Error generating certificates:', error.message);
	console.log('\n⚠️  Make sure OpenSSL is installed:');
	console.log('   Ubuntu/Debian: sudo apt-get install openssl');
	console.log('   macOS: brew install openssl');
	console.log('   Windows: Download from https://slproweb.com/products/Win32OpenSSL.html\n');
	process.exit(1);
}
