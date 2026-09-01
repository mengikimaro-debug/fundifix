const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const crypto = require('crypto');
const path = require('path');
const fs = require('fs');
const https = require('https');
const http = require('http');
const createRegisterRouter = require('./routes/register');

const app = express();
const PORT = process.env.PORT || 5000;

app.use(cors());
app.use(express.json());
app.use('/admin', express.static(path.join(__dirname, 'admin')));

const mongoURI = process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/fundifix';

mongoose.connect(mongoURI)
	.then(() => console.log('✅ MongoDB Connected! (FundiFix)'))
	.catch(err => console.error('❌ MongoDB Connection Error:', err));

const UserSchema = new mongoose.Schema({
	phone: { type: String, required: true, unique: true },
	role: { type: String, default: 'client' },
	method: { type: String, default: 'sms' },
	name: { type: String },
	passwordHash: { type: String },
	createdAt: { type: Date, default: Date.now }
});

const User = mongoose.model('User', UserSchema);

function hashPassword(password) {
	return crypto.createHash('sha256').update(password).digest('hex');
}

const ServiceRequestSchema = new mongoose.Schema({
	id: String,
	service: String,
	desc: String,
	clientPhone: String,
	price: { type: String, default: '0' },
	location: String,
	bookingDate: String,
	status: { type: String, default: 'pending' },
	createdAt: { type: Date, default: Date.now }
});

const ServiceRequest = mongoose.model('ServiceRequest', ServiceRequestSchema);

const ADMIN_DASHBOARD_KEY = process.env.ADMIN_DASHBOARD_KEY || 'fundifix-admin-local';

function isAdminRequest(req) {
	const key = req.get('x-admin-key') || req.query.key;
	if (!key) return false;
	const providedKey = Buffer.from(String(key));
	const configuredKey = Buffer.from(ADMIN_DASHBOARD_KEY);
	return providedKey.length === configuredKey.length
		&& crypto.timingSafeEqual(providedKey, configuredKey);
}

const OTP_EXPIRY_MS = 5 * 60 * 1000;
const MAX_ATTEMPTS = 5;
const RESEND_COOLDOWN_MS = 60 * 1000;
const otps = new Map();

function generateOTP() {
	return crypto.randomInt(100000, 1000000).toString();
}

function hashOTP(otp) {
	return crypto.createHash('sha256').update(otp).digest('hex');
}

function saveOTP(phone, otp) {
	const now = Date.now();
	otps.set(phone, {
		hash: hashOTP(otp),
		createdAt: now,
		expiresAt: now + OTP_EXPIRY_MS,
		attempts: 0
	});
}

function verifyOTP(phone, otp) {
	const record = otps.get(phone);

	if (!record) {
		return { success: false, message: 'OTP haipo. Omba OTP mpya.' };
	}

	if (Date.now() > record.expiresAt) {
		otps.delete(phone);
		return { success: false, message: 'OTP ime-expire. Omba OTP mpya.' };
	}

	if (record.attempts >= MAX_ATTEMPTS) {
		otps.delete(phone);
		return { success: false, message: 'Umefanya majaribio mengi. Omba OTP mpya.' };
	}

	record.attempts++;

	if (hashOTP(otp) !== record.hash) {
		return { success: false, message: 'OTP sio sahihi.' };
	}

	otps.delete(phone);
	return { success: true, message: 'OTP Sahihi' };
}

async function sendSMS(phone, otp) {
	console.log('================================');
	console.log('📱 SMS OTP TEST');
	console.log('Phone:', phone);
	console.log('OTP:', otp);
	console.log('================================');
	return { success: true, mode: 'test' };
}

async function sendWhatsApp(phone, otp) {
	console.log('================================');
	console.log('💬 WHATSAPP OTP TEST');
	console.log('Phone:', phone);
	console.log('OTP:', otp);
	console.log('================================');
	return { success: true, mode: 'test' };
}

app.get('/', (req, res) => {
	res.json({ success: true, app: 'FundiFix API', status: 'online', port: PORT });
});

app.get('/admin/overview', async (req, res) => {
	if (!isAdminRequest(req)) return res.status(401).json({ success: false, message: 'Admin key inahitajika.' });
	try {
		const [rawUsers, requests] = await Promise.all([
			User.find({}, '-passwordHash -__v').sort({ createdAt: -1 }).lean(),
			ServiceRequest.find({}, '-__v').sort({ createdAt: -1 }).lean()
		]);
		const users = [...new Map(rawUsers.map(user => [user.phone, user])).values()];
		const countBy = (items, field) => items.reduce((counts, item) => {
			const value = String(item[field] || 'unknown').toLowerCase();
			counts[value] = (counts[value] || 0) + 1;
			return counts;
		}, {});
		res.status(200).json({
			success: true,
			stats: {
				totalUsers: users.length,
				clients: users.filter(user => String(user.role).toLowerCase() === 'client').length,
				fundis: users.filter(user => String(user.role).toLowerCase() === 'fundi').length,
				totalJobs: requests.length,
				jobsByStatus: countBy(requests, 'status')
			},
			users,
			requests
		});
	} catch (err) {
		console.error('Admin overview error:', err);
		res.status(500).json({ success: false, message: 'Imeshindikana kupata dashboard.' });
	}
});

app.use('/register', createRegisterRouter(User, hashPassword));

app.post('/login', async (req, res) => {
	try {
		const { phone, password } = req.body;
		if (!phone || !password) return res.status(400).json({ success: false, message: 'Namba na password vinahitajika.' });
		const cleanPhone = String(phone).replace(/[\s-]/g, '');
		const phoneOptions = cleanPhone.startsWith('+')
			? [cleanPhone, cleanPhone.slice(1)]
			: cleanPhone.startsWith('0')
				? [cleanPhone, `255${cleanPhone.slice(1)}`, `+255${cleanPhone.slice(1)}`]
				: [cleanPhone, `+${cleanPhone}`];
		const user = await User.findOne({ phone: { $in: phoneOptions } });
		if (!user || !user.passwordHash || user.passwordHash !== hashPassword(password)) {
			return res.status(401).json({ success: false, message: 'Namba au password si sahihi.' });
		}
		res.status(200).json({ phone: user.phone, role: user.role, method: user.method, name: user.name || null });
	} catch (err) {
		console.error('Login error:', err);
		res.status(500).json({ success: false, message: 'Imeshindikana kuingia.' });
	}
});

app.post('/submit-request', async (req, res) => {
	try {
		await new ServiceRequest(req.body).save();
		res.status(200).json({ success: true, message: 'Ombi limepokelewa' });
	} catch (err) {
		console.error(err);
		res.status(500).json({ success: false, message: 'Imeshindwa kutuma ombi' });
	}
});

app.get('/active-requests', async (req, res) => {
	try {
		const requests = await ServiceRequest.find({ status: 'pending' }).sort({ createdAt: -1 });
		res.status(200).json(requests);
	} catch (err) {
		console.error(err);
		res.status(500).json([]);
	}
});

app.get('/client-requests/:phone', async (req, res) => {
	try {
		const requests = await ServiceRequest.find({ clientPhone: req.params.phone }).sort({ createdAt: -1 });
		res.status(200).json(requests);
	} catch (err) {
		console.error(err);
		res.status(500).json([]);
	}
});

async function updateRequestStatus(req, res, status, message, errorMessage) {
	try {
		const result = await ServiceRequest.findOneAndUpdate(
			{ id: req.params.id }, { status }, { new: true }
		);
		if (!result) return res.status(404).json({ success: false, message: 'Kazi haijapatikana' });
		res.status(200).json({ success: true, message });
	} catch (err) {
		console.error(err);
		res.status(500).json({ success: false, message: errorMessage });
	}
}

app.post('/accept-request/:id', (req, res) =>
	updateRequestStatus(req, res, 'accepted', 'Kazi imekubaliwa', 'Imeshindwa kukubali kazi'));
app.post('/reject-request/:id', (req, res) =>
	updateRequestStatus(req, res, 'rejected', 'Kazi imekataliwa', 'Imeshindwa kukataa kazi'));

app.post('/update-request-price/:id', async (req, res) => {
	try {
		const price = String(req.body.price || '').trim();
		if (!price) return res.status(400).json({ success: false, message: 'Bei inahitajika.' });
		const result = await ServiceRequest.findOneAndUpdate(
			{ id: req.params.id }, { price }, { new: true }
		);
		if (!result) return res.status(404).json({ success: false, message: 'Kazi haijapatikana' });
		res.status(200).json({ success: true, message: 'Bill imesasishwa kwa mteja.' });
	} catch (err) {
		console.error(err);
		res.status(500).json({ success: false, message: 'Imeshindikana kubadilisha bill.' });
	}
});

async function sendOTP(req, res, sender, successMessage, errorLabel) {
	try {
		const { phone } = req.body;
		if (!phone) return res.status(400).json({ success: false, message: 'Namba ya simu inahitajika.' });
		const oldOTP = otps.get(phone);
		if (oldOTP && Date.now() - oldOTP.createdAt < RESEND_COOLDOWN_MS) {
			return res.status(429).json({ success: false, message: 'Subiri dakika moja kabla ya kuomba OTP nyingine.' });
		}
		const otpCode = generateOTP();
		saveOTP(phone, otpCode);
		await sender(phone, otpCode);
		res.status(200).json({ success: true, message: successMessage });
	} catch (err) {
		console.error(`${errorLabel} OTP error:`, err);
		res.status(500).json({ success: false, message: 'Imeshindwa kutuma OTP.' });
	}
}

function verifyRoute(req, res) {
	try {
		const { phone, otp } = req.body;
		if (!phone || !otp) return res.status(400).json({ success: false, message: 'Phone na OTP vinahitajika.' });
		const result = verifyOTP(phone, otp);
		if (!result.success) return res.status(400).json(result);
		res.status(200).json(result);
	} catch (err) {
		console.error(err);
		res.status(500).json({ success: false, message: 'Hitilafu imetokea.' });
	}
}

app.post('/auth/whatsapp/send', (req, res) => sendOTP(req, res, sendWhatsApp, 'OTP Imetumwa WhatsApp', 'WhatsApp'));
app.post('/auth/whatsapp/verify', verifyRoute);
app.post('/auth/sms/send', (req, res) => sendOTP(req, res, sendSMS, 'OTP Imetumwa kwa SMS', 'SMS'));
app.post('/auth/sms/verify', verifyRoute);

app.post('/auth/otp/resend', async (req, res) => {
	try {
		const { phone, method = 'sms' } = req.body;
		if (!phone) return res.status(400).json({ success: false, message: 'Namba ya simu inahitajika.' });
		const oldOTP = otps.get(phone);
		if (oldOTP && Date.now() - oldOTP.createdAt < RESEND_COOLDOWN_MS) {
			return res.status(429).json({ success: false, message: 'Subiri dakika moja kabla ya kutuma tena.' });
		}
		const otpCode = generateOTP();
		saveOTP(phone, otpCode);
		await (method === 'whatsapp' ? sendWhatsApp : sendSMS)(phone, otpCode);
		res.status(200).json({ success: true, message: 'OTP imetumwa tena.' });
	} catch (err) {
		console.error(err);
		res.status(500).json({ success: false, message: 'Imeshindwa kutuma OTP.' });
	}
});

setInterval(() => {
	const now = Date.now();
	for (const [phone, record] of otps.entries()) {
		if (now > record.expiresAt) otps.delete(phone);
	}
}, 60 * 1000);

// =============== HTTPS/mTLS Configuration ===============
const USE_MTLS = process.env.USE_MTLS === 'true';
const CERT_DIR = path.join(__dirname, 'certs');

function startServer() {
	if (USE_MTLS && fs.existsSync(path.join(CERT_DIR, 'server.crt'))) {
		// Start with HTTPS + mTLS (client certificate required)
		const httpsOptions = {
			key: fs.readFileSync(path.join(CERT_DIR, 'server.key')),
			cert: fs.readFileSync(path.join(CERT_DIR, 'server.crt')),
			ca: fs.readFileSync(path.join(CERT_DIR, 'ca.crt')),
			requestCert: true,
			rejectUnauthorized: true
		};

		https.createServer(httpsOptions, app).listen(PORT, '0.0.0.0', () => {
			console.log('========================================');
			console.log('🚀 FundiFix Server is running');
			console.log(`🌐 HTTPS Port: ${PORT}`);
			console.log('🔒 mTLS: ENABLED (Client certificates required)');
			console.log('🔐 OTP System: ACTIVE');
			console.log('📱 SMS Mode: TEST');
			console.log('💬 WhatsApp Mode: TEST');
			console.log('========================================');
		});
	} else {
		// Start with HTTP (development/testing)
		http.createServer(app).listen(PORT, '0.0.0.0', () => {
			console.log('========================================');
			console.log('🚀 FundiFix Server is running');
			console.log(`🌐 HTTP Port: ${PORT}`);
			console.log('🔐 OTP System: ACTIVE');
			console.log('📱 SMS Mode: TEST');
			console.log('💬 WhatsApp Mode: TEST');
			console.log('⚠️  mTLS: NOT ENABLED (Development mode)');
			console.log('========================================');
		});
	}
}

startServer();
