const express = require('express');
const crypto = require('crypto');

module.exports = function createRegisterRouter(User, hashPassword, sendSMS) {
    const router = express.Router();

    // =====================================================
    // TEMPORARY REGISTRATION + OTP STORAGE
    // =====================================================

    const pendingRegistrations = new Map();

    const OTP_EXPIRY = 5 * 60 * 1000;       // dakika 5
    const MAX_ATTEMPTS = 5;
    const RESEND_COOLDOWN = 60 * 1000;      // sekunde 60

    // =====================================================
    // GENERATE OTP
    // =====================================================

    function generateOTP() {
        return crypto.randomInt(100000, 1000000).toString();
    }

    // =====================================================
    // HASH OTP
    // =====================================================

    function hashOTP(otp) {
        return crypto
            .createHash('sha256')
            .update(otp)
            .digest('hex');
    }

    // =====================================================
    // REGISTER - SEND OTP
    // =====================================================

    router.post('/', async (req, res) => {

        console.log('📥 Registration request:', req.body);

        try {

            const {
                phone,
                role,
                method,
                name,
                password
            } = req.body;

            // -----------------------------
            // CHECK PHONE
            // -----------------------------

            if (!phone) {

                return res.status(400).json({
                    success: false,
                    message: 'Namba ya simu inahitajika.'
                });
            }

            // -----------------------------
            // CHECK PASSWORD
            // -----------------------------

            if (
                method !== 'Google' &&
                !/^\d{6}$/.test(password || '')
            ) {

                return res.status(400).json({
                    success: false,
                    message: 'Password lazima iwe tarakimu 6.'
                });
            }

            // -----------------------------
            // CHECK EXISTING USER
            // -----------------------------

            const existingUser = await User.findOne({
                $or: [
                    { phone },
                    ...(name ? [{ name: name.trim() }] : [])
                ]
            });

            if (existingUser) {

                if (
                    !existingUser.passwordHash &&
                    password
                ) {

                    existingUser.passwordHash =
                        hashPassword(password);

                    await existingUser.save();

                    return res.status(200).json({
                        success: true,
                        message: 'Password imewekwa kwenye akaunti yako.'
                    });
                }

                const message =
                    existingUser.phone === phone
                        ? 'Namba hii tayari imesajiliwa. Tumia Ingia.'
                        : 'Jina hili tayari limesajiliwa. Tumia jina jingine.';

                return res.status(409).json({
                    success: false,
                    message
                });
            }

            // -----------------------------
            // CHECK RESEND COOLDOWN
            // -----------------------------

            const oldRegistration =
                pendingRegistrations.get(phone);

            if (
                oldRegistration &&
                Date.now() - oldRegistration.createdAt <
                RESEND_COOLDOWN
            ) {

                return res.status(429).json({
                    success: false,
                    message:
                        'Subiri sekunde 60 kabla ya kuomba OTP nyingine.'
                });
            }

            // -----------------------------
            // GENERATE OTP
            // -----------------------------

            const otp = generateOTP();

            // -----------------------------
            // SAVE PENDING REGISTRATION
            // -----------------------------

            pendingRegistrations.set(phone, {

                phone,

                role: role || 'CLIENT',

                method: method || 'SMS',

                name: name
                    ? name.trim()
                    : undefined,

                passwordHash:
                    password
                        ? hashPassword(password)
                        : undefined,

                otpHash: hashOTP(otp),

                createdAt: Date.now(),

                expiresAt:
                    Date.now() + OTP_EXPIRY,

                attempts: 0
            });

            // -----------------------------
            // SEND SMS
            // -----------------------------

            console.log(
                `📱 OTP generated for ${phone}: ${otp}`
            );

            if (typeof sendSMS === 'function') {

                await sendSMS(phone, otp);

            } else {

                // TEST MODE
                console.log(
                    `⚠️ SMS gateway haijaunganishwa kwenye router. OTP: ${otp}`
                );
            }

            // -----------------------------
            // RESPONSE
            // -----------------------------

            return res.status(200).json({

                success: true,

                otpRequired: true,

                message:
                    'OTP imetumwa kwenye namba yako.'
            });

        } catch (err) {

            console.error(
                '❌ Registration error:',
                err
            );

            return res.status(500).json({

                success: false,

                message:
                    'Hitilafu imetokea wakati wa usajili.'
            });
        }
    });

    // =====================================================
    // VERIFY OTP + CREATE ACCOUNT
    // =====================================================

    router.post('/verify', async (req, res) => {

        try {

            const {
                phone,
                otp
            } = req.body;

            // -----------------------------
            // CHECK DATA
            // -----------------------------

            if (!phone || !otp) {

                return res.status(400).json({

                    success: false,

                    message:
                        'Namba ya simu na OTP vinahitajika.'
                });
            }

            // -----------------------------
            // GET PENDING REGISTRATION
            // -----------------------------

            const registration =
                pendingRegistrations.get(phone);

            if (!registration) {

                return res.status(400).json({

                    success: false,

                    message:
                        'Hakuna usajili unaosubiri. Omba OTP mpya.'
                });
            }

            // -----------------------------
            // CHECK EXPIRY
            // -----------------------------

            if (
                Date.now() >
                registration.expiresAt
            ) {

                pendingRegistrations.delete(phone);

                return res.status(400).json({

                    success: false,

                    message:
                        'OTP ime-expire. Omba OTP mpya.'
                });
            }

            // -----------------------------
            // CHECK ATTEMPTS
            // -----------------------------

            if (
                registration.attempts >=
                MAX_ATTEMPTS
            ) {

                pendingRegistrations.delete(phone);

                return res.status(429).json({

                    success: false,

                    message:
                        'Majaribio yamezidi. Omba OTP mpya.'
                });
            }

            // -----------------------------
            // INCREASE ATTEMPT
            // -----------------------------

            registration.attempts++;

            // -----------------------------
            // VERIFY OTP
            // -----------------------------

            const incomingHash =
                hashOTP(String(otp));

            if (
                incomingHash !==
                registration.otpHash
            ) {

                return res.status(400).json({

                    success: false,

                    message:
                        'OTP sio sahihi.'
                });
            }

            // =================================================
            // OTP CORRECT
            // =================================================

            // -----------------------------
            // CREATE USER
            // -----------------------------

            const userData = {

                phone:
                    registration.phone,

                role:
                    registration.role,

                method:
                    registration.method
            };

            if (registration.name) {

                userData.name =
                    registration.name;
            }

            if (registration.passwordHash) {

                userData.passwordHash =
                    registration.passwordHash;
            }

            const newUser =
                new User(userData);

            await newUser.save();

            // -----------------------------
            // DELETE USED OTP
            // -----------------------------

            pendingRegistrations.delete(phone);

            console.log(
                `✅ User verified and saved: ${phone}`
            );

            // -----------------------------
            // SUCCESS
            // -----------------------------

            return res.status(200).json({

                success: true,

                verified: true,

                message:
                    'OTP Sahihi. Usajili umekamilika.'
            });

        } catch (err) {

            console.error(
                '❌ OTP verification error:',
                err
            );

            return res.status(500).json({

                success: false,

                message:
                    'Hitilafu imetokea wakati wa kuthibitisha OTP.'
            });
        }
    });

    // =====================================================
    // RESEND OTP
    // =====================================================

    router.post('/resend', async (req, res) => {

        try {

            const {
                phone
            } = req.body;

            if (!phone) {

                return res.status(400).json({

                    success: false,

                    message:
                        'Namba ya simu inahitajika.'
                });
            }

            const registration =
                pendingRegistrations.get(phone);

            if (!registration) {

                return res.status(404).json({

                    success: false,

                    message:
                        'Hakuna usajili unaosubiri. Anza usajili tena.'
                });
            }

            // -----------------------------
            // COOLDOWN
            // -----------------------------

            if (
                Date.now() -
                registration.createdAt <
                RESEND_COOLDOWN
            ) {

                return res.status(429).json({

                    success: false,

                    message:
                        'Subiri sekunde 60 kabla ya kutuma OTP tena.'
                });
            }

            // -----------------------------
            // NEW OTP
            // -----------------------------

            const otp =
                generateOTP();

            registration.otpHash =
                hashOTP(otp);

            registration.createdAt =
                Date.now();

            registration.expiresAt =
                Date.now() +
                OTP_EXPIRY;

            registration.attempts =
                0;

            // -----------------------------
            // SEND SMS
            // -----------------------------

            if (typeof sendSMS === 'function') {

                await sendSMS(phone, otp);

            } else {

                console.log(
                    `⚠️ TEST OTP: ${otp}`
                );
            }

            return res.status(200).json({

                success: true,

                message:
                    'OTP mpya imetumwa.'
            });

        } catch (err) {

            console.error(
                '❌ Resend OTP error:',
                err
            );

            return res.status(500).json({

                success: false,

                message:
                    'Imeshindwa kutuma OTP mpya.'
            });
        }
    });

    // =====================================================
    // CLEAN EXPIRED REGISTRATIONS
    // =====================================================

    setInterval(() => {

        const now =
            Date.now();

        for (
            const [phone, registration]
            of pendingRegistrations
        ) {

            if (
                now >
                registration.expiresAt
            ) {

                pendingRegistrations.delete(phone);
            }
        }

    }, 60 * 1000);

    return router;
};