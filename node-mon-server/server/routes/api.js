const express = require('express');
const crypto = require('crypto');
const router = express.Router();

const SECURE_FIELDS = ['phone', 'idCard', 'token'];
const SECURE_KEY = Buffer.from('0123456789abcdef0123456789abcdef', 'utf8');
const SECURE_IV = Buffer.from('abcdef9876543210', 'utf8');

function encryptValue(value) {
    const cipher = crypto.createCipheriv('aes-256-cbc', SECURE_KEY, SECURE_IV);
    return Buffer.concat([
        cipher.update(String(value), 'utf8'),
        cipher.final()
    ]).toString('base64');
}

function decryptValue(value) {
    const decipher = crypto.createDecipheriv('aes-256-cbc', SECURE_KEY, SECURE_IV);
    return Buffer.concat([
        decipher.update(String(value), 'base64'),
        decipher.final()
    ]).toString('utf8');
}

function transformSecureFields(value, transformer) {
    if (Array.isArray(value)) {
        return value.map(item => transformSecureFields(item, transformer));
    }
    if (!value || typeof value !== 'object') {
        return value;
    }
    return Object.fromEntries(
        Object.entries(value).map(([key, item]) => {
            if (SECURE_FIELDS.includes(key) && item !== null && item !== undefined) {
                return [key, transformer(item)];
            }
            return [key, transformSecureFields(item, transformer)];
        })
    );
}

function maskTail(value, count = 4) {
    const text = String(value || '');
    if (!text) return '';
    return `***${text.slice(-count)}`;
}

// ============= 原有 GET 示例（无参数） =============
router.get('/data', (req, res) => {
    const response = {
        code: 200,
        message: 'success',
        data: {
            id: 1,
            name: 'Test Data',
            timestamp: Date.now()
        }
    };
    res.json(response);
});

// ============= 新增：支持 Query 参数的 GET 方法 =============
// 示例：GET /api/echo?name=张三&age=25
router.get('/echo', (req, res) => {
    const queryParams = req.query; // 获取所有 query 参数
    res.json({
        code: 200,
        message: 'Query parameters received',
        received: queryParams,
        timestamp: new Date().toISOString()
    });
});

// ============= 新增：支持路径参数的 GET 方法 =============
// 示例：GET /api/user/123
router.get('/user/:id', (req, res) => {
    const { id } = req.params;
    const { name } = req.query; // 也可以同时接收 query 参数
    res.json({
        code: 200,
        message: 'User info',
        data: {
            userId: id,
            name: name || 'unknown',
            timestamp: Date.now()
        }
    });
});

// ============= 原有的 POST 方法 =============
router.post('/json', (req, res) => {
    const receivedJson = req.body;
    res.json({
        code: 200,
        message: 'JSON received successfully',
        received: receivedJson,
        timestamp: new Date().toISOString()
    });
});

router.post('/secure-profile', (req, res) => {
    let decryptedBody;
    try {
        decryptedBody = transformSecureFields(req.body || {}, decryptValue);
    } catch (err) {
        return res.status(400).json({
            code: 400,
            message: 'Secure field decrypt failed. Check AES key, IV and encrypted fields.',
            data: {
                secureFields: SECURE_FIELDS,
                error: err.message
            }
        });
    }

    const encryptedProfile = transformSecureFields({
        profileId: `profile-${Date.now()}`,
        name: decryptedBody.name || 'unknown',
        phone: decryptedBody.phone,
        idCard: decryptedBody.idCard,
        token: decryptedBody.token,
        serverTime: new Date().toISOString(),
        requestEncryptedEcho: req.body,
        decryptedSummary: {
            phoneTail: maskTail(decryptedBody.phone),
            idCardTail: maskTail(decryptedBody.idCard),
            tokenPrefix: String(decryptedBody.token || '').slice(0, 8)
        }
    }, encryptValue);

    res.json({
        code: 200,
        message: 'Secure profile accepted',
        data: encryptedProfile
    });
});

router.post('/form', (req, res) => {
    const receivedForm = req.body;
    res.json({
        code: 200,
        message: 'Form data received successfully',
        received: receivedForm,
        timestamp: new Date().toISOString()
    });
});

router.post('/submit', (req, res) => {
    res.json({
        code: 200,
        message: 'Data received (legacy endpoint)',
        received: req.body
    });
});

module.exports = router;
