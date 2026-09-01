/**
 * FundiFix Backend Server v2.0 - Modernized & Secured
 * 
 * Upgrades:
 * ✅ Security: Helmet + Rate Limiting + Input Validation
 * ✅ Logging: Winston structured logging
 * ✅ Error Handling: Comprehensive error middleware
 * ✅ Environment Config: Dotenv configuration
 * ✅ Input Validation: Joi schemas
 * ✅ Performance: Database indexes + query optimization
 * ✅ mTLS Support: HTTPS + Mutual TLS
 * ✅ Monitoring: Health checks endpoint
 */

require('dotenv').config();
require('express-async-errors');

const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const crypto = require('crypto');
const path = require('path');
const fs = require('fs');
const https = require('https');
const http = require('http');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const Joi = require('joi');
const winston = require('winston');

// ===========================
// LOGGING CONFIGURATION
// ===========================
const logger = winston.createLogger({
	level: process.env.LOG_LEVEL || 'info',
	format: winston.format.combine(
		winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
		winston.format.errors({ stack: true }),
		winston.format.splat(),
		winston.format.json()
	),
	transports: [
		new winston.transports.Console({
			format: winston.format.combine(
				winston.format.colorize(),
				winston.format.simple()
			)
		}),
		new winston.transports.File({ filename: 'logs/error.log', level: 'error' }),
		new winston.transports.File({ filename: 'logs/combined.log' })
	]
});

if (!fs.existsSync('logs')) fs.mkdirSync('logs');

// ===========================
// CONFIGURATION
// ===========================
const PORT = process.env.PORT || 5000;
const USE_MTLS = process.env.USE_MTLS === 'true';
const CERT_DIR = path.join(__dirname, 'certs');
const mongoURI = process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/fundifix';
const ADMIN_DASHBOARD_KEY = process.env.ADMIN_DASHBOARD_KEY || 'fundifix-admin-local';

const OTP_EXPIRY_MS = 5 * 60 * 1000;
const MAX_ATTEMPTS = 5;
const RESEND_COOLDOWN_MS = 60 * 1000;

// ===========================
// EXPRESS APP
// ===========================
const app = express();

// Security Middleware
app.use(helmet());
app.use(cors({
	origin: process.env.ALLOWED_ORIGINS?.split(',') || '*',
	credentials: true
}));

// Rate Limiting
const globalLimiter = rateLimit({
	windowMs: 15 * 60 * 1000,
	max: 100,
	message: 'Umefanya maombi mengi kutoka kwa IP hii'
});

const authLimiter = rateLimit({
	windowMs: 15 * 60 * 1000,
	max: 10,
	message: 'Majaribio mengi ya login'
});

const otpLimiter = rateLimit({
	windowMs: 60 * 1000,
	max: 5,
	message: 'Umefanya maombi mengi'
});

app.use(globalLimiter);
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ limit: '10mb', extended: true }));
app.use('/admin', express.static(path.join(__dirname, 'admin')));

// ===========================
// VALIDATION SCHEMAS
// ===========================
const phoneRegex = /^(\+?(255|254)|0)\d{9}$/;

const loginSchema = Joi.object({
	phone: Joi.string().pattern(phoneRegex).required(),
	password: Joi.string().min(6).required()
});

const otpSchema = Joi.object({
	phone: Joi.string().pattern(phoneRegex).required(),
	otp: Joi.string().pattern(/^\d{6}$/).required()
});

const phoneSchema = Joi.object({
	phone: Joi.string().pattern(phoneRegex).required()
});

const validate = (schema) => (req, res, next) => {
	const { error, value } = schema.validate(req.body);
	if (error) {
		logger.warn('Validation error:', error.details[0].message);
		return res.status(400).json({ success: false, message: error.details[0].message });
	}
	req.validated = value;
	next();
};

// ===========================
// ERROR HANDLER
// ===========================
const errorHandler = (err, req, res, next) => {
	logger.error('Error:', { message: err.message, stack: err.stack, path: req.path });
	const statusCode = err.statusCode || 500;
	res.status(statusCode).json({
		success: false,
		message: statusCode === 500 ? 'Hitilafu imetokea' : err.message
	});
};

// ===========================
// DATABASE CONNECTION
// ===========================
mongoose.connect(mongoURI, {
	useNewUrlParser: true,
	useUnifiedTopology: true,
	retryWrites: true,
	w: 'majority'
})
	.then(() => logger.info('✅ MongoDB Connected'))
	.catch(err => {
		logger.error('❌ MongoDB Connection Error:', err.message);
		process.exit(1);
	});

// ===========================
// DATABASE SCHEMAS
// ===========================
const UserSchema = new mongoose.Schema({
	phone: { type: String, required: true, unique: true, index: true },
	role: { type: String, default: 'client', enum: ['client', 'fundi', 'admin'] },
	method: { type: String, default: 'sms', enum: ['sms', 'whatsapp'] },
	name: String,
	passwordHash: String,
	email: String,
	isActive: { type: Boolean, default: true },
	lastLogin: Date,
	createdAt: { type: Date, default: Date.now, index: true },
	updatedAt: { type: Date, default: Date.now }
}, { collection: 'users' });

const ServiceRequestSchema = new mongoose.Schema({
	id: { type: String, unique: true, index: true },
	service: String,
	desc: String,
	clientPhone: { type: String, required: true, index: true },
	price: { type: String, default: '0' },
	location: String,
	bookingDate: String,
	status: { type: String, default: 'pending', enum: ['pending', 'accepted', 'rejected', 'completed'], index: true },
	acceptedBy: String,
	createdAt: { type: Date, default: Date.now, index: true },
	updatedAt: { type: Date, default: Date.now }
}, { collection: 'service_requests' });

ServiceRequestSchema.index({ clientPhone: 1, createdAt: -1 });

const User = mongoose.model('User', UserSchema);
const ServiceRequest = mongoose.model('ServiceRequest', ServiceRequestSchema);

// ===========================
// UTILITIES
// ===========================
function hashPassword(password) {
	return crypto.createHash('sha256').update(password).digest('hex');
}

function isAdminRequest(req) {
	const key = req.get('x-admin-key') || req.query.key;
	if (!key) return false;
	const providedKey = Buffer.from(String(key));
	const configuredKey = Buffer.from(ADMIN_DASHBOARD_KEY);
	return providedKey.length === configuredKey.length && crypto.timingSafeEqual(providedKey, configuredKey);
}

function normalizePhone(phone) {
	if (!phone) return phone;
	let value = String(phone).trim().replace(/[\s-]/g, '');
	if (/^0\d{9}$/.test(value)) return `+255${value.slice(1)}`;
	if (/^255\d{9}$/.test(value)) return `+${value}`;
	if (/^254\d{9}$/.test(value)) return `+${value}`;
	if (/^\+255\d{9}$/.test(value) || /^\+254\d{9}$/.test(value)) return value;
	return value;
}

function generateOTP() {
	return crypto.randomInt(100000, 1000000).toString();
}

function hashOTP(otp) {
	return crypto.createHash('sha256').update(otp).digest('hex');
}

const otps = new Map();

function saveOTP(phone, otp) {
	const now = Date.now();
	const normalizedPhone = normalizePhone(phone);
	otps.set(normalizedPhone, { hash: hashOTP(otp), createdAt: now, expiresAt: now + OTP_EXPIRY_MS, attempts: 0 });
}

function verifyOTP(phone, otp) {
	const normalizedPhone = normalizePhone(phone);
	const record = otps.get(normalizedPhone);
	if (!record) return { success: false, message: 'OTP haipo. Omba OTP mpya.' };
	if (Date.now() > record.expiresAt) { otps.delete(normalizedPhone); return { success: false, message: 'OTP ime-expire' }; }
	if (record.attempts >= MAX_ATTEMPTS) { otps.delete(normalizedPhone); return { success: false, message: 'Majaribio mengi' }; }
	record.attempts++;
	if (hashOTP(otp) !== record.hash) return { success: false, message: 'OTP sio sahihi' };
	otps.delete(normalizedPhone);
	return { success: true, message: 'OTP Sahihi' };
}

async function sendSMS(phone, otp) {
	const normalizedPhone = normalizePhone(phone);
	const useMockOtp = process.env.SMS_PROVIDER === 'mock' || process.env.NODE_ENV !== 'production';
	logger.info('📱 SMS OTP Sent', { phone: normalizedPhone, otp: useMockOtp ? otp : '[hidden]' });
	return { success: true, mock: useMockOtp, otp: useMockOtp ? otp : undefined };
}

async function sendWhatsApp(phone, otp) {
	const normalizedPhone = normalizePhone(phone);
	const useMockOtp = process.env.WHATSAPP_PROVIDER === 'mock' || process.env.NODE_ENV !== 'production';
	logger.info('💬 WhatsApp OTP Sent', { phone: normalizedPhone, otp: useMockOtp ? otp : '[hidden]' });
	return { success: true, mock: useMockOtp, otp: useMockOtp ? otp : undefined };
}

// ===========================
// API ROUTES
// ===========================

app.get('/health', (req, res) => {
	res.json({ status: 'ok', timestamp: new Date(), uptime: process.uptime() });
});

app.get('/', (req, res) => {
	res.json({ success: true, app: 'FundiFix API v2.0', status: 'online', port: PORT });
});

app.get('/admin/overview', async (req, res, next) => {
	try {
		if (!isAdminRequest(req)) return res.status(401).json({ success: false, message: 'Admin key inahitajika' });
		const [users, requests] = await Promise.all([
			User.find({}, '-passwordHash').sort({ createdAt: -1 }).lean(),
			ServiceRequest.find({}).sort({ createdAt: -1 }).lean()
		]);
		res.json({
			success: true,
			stats: {
				totalUsers: users.length,
				clients: users.filter(u => u.role === 'client').length,
				fundis: users.filter(u => u.role === 'fundi').length,
				totalJobs: requests.length,
				jobsByStatus: { pending: requests.filter(r => r.status === 'pending').length, accepted: requests.filter(r => r.status === 'accepted').length }
			},
			users, requests
		});
	} catch (err) { next(err); }
});

app.post('/login', authLimiter, validate(loginSchema), async (req, res, next) => {
	try {
		const { phone, password } = req.validated;
		const cleanPhone = String(phone).replace(/[\s-]/g, '');
		const phoneOptions = cleanPhone.startsWith('+') ? [cleanPhone, cleanPhone.slice(1)] : [cleanPhone];
		const user = await User.findOne({ phone: { $in: phoneOptions } });
		if (!user || user.passwordHash !== hashPassword(password)) {
			return res.status(401).json({ success: false, message: 'Namba au password si sahihi' });
		}
		await User.updateOne({ _id: user._id }, { lastLogin: new Date() });
		logger.info('User logged in', { phone: user.phone });
		res.json({ success: true, phone: user.phone, role: user.role, method: user.method, name: user.name });
	} catch (err) { next(err); }
});

app.post('/submit-request', async (req, res, next) => {
	try {
		const { clientPhone, service, desc, location, bookingDate } = req.body;
		if (!clientPhone || !service || !location) return res.status(400).json({ success: false, message: 'Maelezo hayajajaza' });
		const request = new ServiceRequest({ id: crypto.randomUUID(), service, desc, clientPhone, location, bookingDate, status: 'pending' });
		await request.save();
		logger.info('Service request submitted', { phone: clientPhone });
		res.status(201).json({ success: true, message: 'Ombi limepokelewa', requestId: request.id });
	} catch (err) { next(err); }
});

app.get('/active-requests', async (req, res, next) => {
	try {
		const requests = await ServiceRequest.find({ status: 'pending' }).sort({ createdAt: -1 }).limit(50).lean();
		res.json(requests);
	} catch (err) { next(err); }
});

app.get('/client-requests/:phone', async (req, res, next) => {
	try {
		const requests = await ServiceRequest.find({ clientPhone: req.params.phone }).sort({ createdAt: -1 }).lean();
		res.json(requests);
	} catch (err) { next(err); }
});

app.post('/accept-request/:id', async (req, res, next) => {
	try {
		const result = await ServiceRequest.findOneAndUpdate({ id: req.params.id }, { status: 'accepted' }, { new: true });
		if (!result) return res.status(404).json({ success: false, message: 'Kazi haijapatikana' });
		res.json({ success: true, message: 'Kazi imekubaliwa', data: result });
	} catch (err) { next(err); }
});

app.post('/reject-request/:id', async (req, res, next) => {
	try {
		const result = await ServiceRequest.findOneAndUpdate({ id: req.params.id }, { status: 'rejected' }, { new: true });
		if (!result) return res.status(404).json({ success: false, message: 'Kazi haijapatikana' });
		res.json({ success: true, message: 'Kazi imekataliwa' });
	} catch (err) { next(err); }
});

app.post('/update-request-price/:id', async (req, res, next) => {
	try {
		const { price } = req.body;
		if (!price || isNaN(price)) return res.status(400).json({ success: false, message: 'Bei sio sahihi' });
		const result = await ServiceRequest.findOneAndUpdate({ id: req.params.id }, { price: String(price) }, { new: true });
		if (!result) return res.status(404).json({ success: false, message: 'Kazi haijapatikana' });
		res.json({ success: true, message: 'Bill imesasishwa', data: result });
	} catch (err) { next(err); }
});

// OTP Routes
app.post('/auth/sms/send', otpLimiter, validate(phoneSchema), async (req, res, next) => {
	try {
		const { phone } = req.validated;
		const normalizedPhone = normalizePhone(phone);
		const otpCode = generateOTP();
		saveOTP(normalizedPhone, otpCode);
		const smsResult = await sendSMS(normalizedPhone, otpCode);
		res.json({
			success: true,
			message: 'OTP Imetumwa kwa SMS',
			phone: normalizedPhone,
			...(smsResult.mock ? { otp: otpCode, debug: true } : {})
		});
	} catch (err) { next(err); }
});

app.post('/auth/whatsapp/send', otpLimiter, validate(phoneSchema), async (req, res, next) => {
	try {
		const { phone } = req.validated;
		const normalizedPhone = normalizePhone(phone);
		const otpCode = generateOTP();
		saveOTP(normalizedPhone, otpCode);
		const waResult = await sendWhatsApp(normalizedPhone, otpCode);
		res.json({
			success: true,
			message: 'OTP Imetumwa WhatsApp',
			phone: normalizedPhone,
			...(waResult.mock ? { otp: otpCode, debug: true } : {})
		});
	} catch (err) { next(err); }
});

app.post('/auth/sms/verify', validate(otpSchema), (req, res) => {
	const { phone, otp } = req.validated;
	const normalizedPhone = normalizePhone(phone);
	const result = verifyOTP(normalizedPhone, otp);
	res.status(result.success ? 200 : 400).json(result);
});

app.post('/auth/whatsapp/verify', validate(otpSchema), (req, res) => {
	const { phone, otp } = req.validated;
	const normalizedPhone = normalizePhone(phone);
	const result = verifyOTP(normalizedPhone, otp);
	res.status(result.success ? 200 : 400).json(result);
});

app.post('/auth/otp/resend', validate(phoneSchema), async (req, res, next) => {
	try {
		const { phone } = req.validated;
		const normalizedPhone = normalizePhone(phone);
		const { method = 'sms' } = req.body;
		const otpCode = generateOTP();
		saveOTP(normalizedPhone, otpCode);
		const resendResult = await (method === 'whatsapp' ? sendWhatsApp(normalizedPhone, otpCode) : sendSMS(normalizedPhone, otpCode));
		res.json({
			success: true,
			message: 'OTP imetumwa tena',
			phone: normalizedPhone,
			...(resendResult.mock ? { otp: otpCode, debug: true } : {})
		});
	} catch (err) { next(err); }
});

// OTP Cleanup
setInterval(() => {
	const now = Date.now();
	for (const [phone, record] of otps) {
		if (now > record.expiresAt) otps.delete(phone);
	}
}, 60 * 1000);

// Error Handlers
app.use((req, res) => res.status(404).json({ success: false, message: 'Endpoint haipo' }));
app.use(errorHandler);

// ===========================
// SERVER STARTUP
// ===========================
function startServer() {
	if (USE_MTLS && fs.existsSync(path.join(CERT_DIR, 'server.crt'))) {
		const httpsOptions = {
			key: fs.readFileSync(path.join(CERT_DIR, 'server.key')),
			cert: fs.readFileSync(path.join(CERT_DIR, 'server.crt')),
			ca: fs.readFileSync(path.join(CERT_DIR, 'ca.crt')),
			requestCert: true,
			rejectUnauthorized: true
		};
		https.createServer(httpsOptions, app).listen(PORT, '0.0.0.0', () => {
			logger.info('========================================');
			logger.info('🚀 FundiFix v2.0 Running on HTTPS/mTLS');
			logger.info(`🌐 Port: ${PORT}`);
			logger.info('🔒 Security: Helmet + Rate Limit + Validation');
			logger.info('📝 Logging: Winston');
			logger.info('========================================');
		});
	} else {
		http.createServer(app).listen(PORT, '0.0.0.0', () => {
			logger.info('========================================');
			logger.info('🚀 FundiFix v2.0 Running on HTTP');
			logger.info(`🌐 Port: ${PORT}`);
			logger.info('🔐 Security: Helmet + Rate Limit + Validation');
			logger.info('📝 Logging: Winston');
			logger.info('========================================');
		});
	}
}

startServer();

// Graceful Shutdown
process.on('SIGTERM', () => {
	logger.info('Shutting down gracefully...');
	mongoose.connection.close();
	process.exit(0);
});

module.exports = app;
