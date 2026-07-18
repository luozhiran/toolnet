const express = require('express');
const router = express.Router();

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