const express = require('express');
const multer = require('multer');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
const debug = require('debug');

// 创建不同模块的调试器
const appDebug = debug('app:main');
const requestDebug = debug('app:request');
const responseDebug = debug('app:response');
const uploadDebug = debug('app:upload');
const perfDebug = debug('app:perf');

const app = express();
const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || '0.0.0.0';

// ============= 配置 =============
const config = {
    upload: {
        dest: path.join(__dirname, 'uploads'),
        limits: {
            fileSize: 10 * 1024 * 1024, // 10MB
            files: 5
        }
    },
    staticDir: path.join(__dirname, '../frontend/dist'), // React 构建产物目录（生产环境）
    perf: {
        slowRequest: 1000,
        slowMulter: 500
    }
};

// 确保上传目录存在
if (!fs.existsSync(config.upload.dest)) {
    fs.mkdirSync(config.upload.dest, { recursive: true });
}

// ============= 响应拦截中间件 =============
app.use((req, res, next) => {
    const originalJson = res.json;
    const originalSend = res.send;
    const originalDownload = res.download;

    res.json = function (data) {
        responseDebug(`📤 响应状态: ${res.statusCode}`);
        responseDebug(`📦 响应体 (JSON): ${JSON.stringify(data, null, 2)}`);
        return originalJson.call(this, data);
    };

    res.send = function (body) {
        let logBody = body;
        if (typeof body === 'string') {
            try {
                const json = JSON.parse(body);
                logBody = JSON.stringify(json, null, 2);
            } catch (e) {
                logBody = body.length > 500 ? body.substring(0, 500) + '...' : body;
            }
        } else if (Buffer.isBuffer(body)) {
            logBody = `<Buffer ${body.length} bytes>`;
        }
        responseDebug(`📤 响应状态: ${res.statusCode}`);
        responseDebug(`📦 响应体 (send): ${logBody}`);
        return originalSend.call(this, body);
    };

    res.download = function (filepath, filename, options, callback) {
        responseDebug(`📤 响应类型: 文件下载`);
        responseDebug(`📄 文件路径: ${filepath}`);
        responseDebug(`📄 文件名: ${filename || path.basename(filepath)}`);
        return originalDownload.call(this, filepath, filename, options, callback);
    };

    next();
});

// ============= 性能监控中间件 =============
app.use((req, res, next) => {
    const start = Date.now();
    req.startTime = start;

    res.on('finish', () => {
        const duration = Date.now() - start;
        const statusCode = res.statusCode;
        const method = req.method;
        const url = req.url;

        perfDebug(`${method} ${url} - ${duration}ms - ${statusCode}`);
        if (duration > config.perf.slowRequest) {
            console.warn(`⚠️  慢请求警告: ${method} ${url} 耗时 ${duration}ms`);
        }
    });
    next();
});

// ============= 请求详情中间件 =============
app.use((req, res, next) => {
    requestDebug(`📨 [${new Date().toISOString()}] ${req.method} ${req.url}`);
    requestDebug(`📡 Headers: ${JSON.stringify(req.headers, null, 2)}`);
    requestDebug(`🔗 Query params: ${JSON.stringify(req.query)}`);
    next();
});

// ============= 基础中间件 =============
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// ============= Multer 配置 =============
const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        uploadDebug(`📂 目标目录: ${config.upload.dest}`);
        cb(null, config.upload.dest);
    },
    filename: (req, file, cb) => {
        const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
        const filename = uniqueSuffix + '-' + file.originalname;
        uploadDebug(`📄 生成文件名: ${filename}`);
        uploadDebug(`   - 原始名: ${file.originalname}`);
        cb(null, filename);
    }
});

const fileFilter = (req, file, cb) => {
    uploadDebug(`🔍 文件过滤检查:`);
    uploadDebug(`   - 字段名: ${file.fieldname}`);
    uploadDebug(`   - 文件名: ${file.originalname}`);
    uploadDebug(`   - MIME类型: ${file.mimetype}`);
    uploadDebug(`   - 大小: ${file.size || '未知'} bytes`);
    cb(null, true);
};

const upload = multer({
    storage,
    limits: config.upload.limits,
    fileFilter
});

// 带性能监控的上传封装
const wrapMulterWithTiming = (multerMiddleware, fieldName, options = {}) => {
    return (req, res, next) => {
        const start = Date.now();
        uploadDebug(`🎯 开始处理文件上传 (字段: ${fieldName})`);
        multerMiddleware(req, res, (err) => {
            const duration = Date.now() - start;
            perfDebug(`⏱️  multer.${options.type || 'single'}("${fieldName}") 处理耗时: ${duration}ms`);
            if (duration > config.perf.slowMulter) {
                console.warn(`⚠️  multer 处理较慢: ${duration}ms`);
            }
            next(err);
        });
    };
};

// ============= API 路由 =============
// 简单的 JSON API
app.get('/api/data', (req, res) => {
    res.json({
        code: 200,
        message: 'success',
        data: {
            id: 1,
            name: 'Test Data',
            timestamp: Date.now()
        }
    });
});

// GET /api/echo (支持任意 query 参数)
app.get('/api/echo', (req, res) => {
    res.json({
        code: 200,
        message: 'Query parameters received',
        received: req.query,
        timestamp: new Date().toISOString()
    });
});

// GET /api/user/:id (路径参数 + 可选 query)
app.get('/api/user/:id', (req, res) => {
    const { id } = req.params;
    res.json({
        code: 200,
        message: 'User info',
        data: {
            userId: id,
            query: req.query,
            timestamp: Date.now()
        }
    });
});

// POST /api/json
app.post('/api/json', (req, res) => {
    res.json({
        code: 200,
        message: 'JSON received',
        received: req.body,
        timestamp: new Date().toISOString()
    });
});

// POST /api/form (application/x-www-form-urlencoded)
app.post('/api/form', (req, res) => {
    res.json({
        code: 200,
        message: 'Form data received',
        received: req.body,
        timestamp: new Date().toISOString()
    });
});

// POST /api/submit (legacy)
app.post('/api/submit', (req, res) => {
    res.json({
        code: 200,
        message: 'Data received (legacy)',
        received: req.body
    });
});

// ============= 上传路由 =============
// 单文件上传
app.post('/upload/single', wrapMulterWithTiming(upload.single('file'), 'file', { type: 'single' }), (req, res) => {
    const file = req.file;
    if (!file) {
        uploadDebug(`⚠️  没有收到文件`);
        return res.status(400).json({ code: 400, message: 'No file uploaded' });
    }
    uploadDebug(`✅ 文件上传成功:`);
    uploadDebug(`   - 原始名: ${file.originalname}`);
    uploadDebug(`   - 大小: ${file.size} bytes`);
    res.json({
        code: 200,
        message: 'File uploaded successfully',
        data: {
            filename: file.filename,
            originalName: file.originalname,
            size: file.size,
            mimetype: file.mimetype,
            path: file.path
        }
    });
});

// 多文件上传
app.post('/upload/multiple', wrapMulterWithTiming(upload.array('files', 5), 'files', { type: 'array' }), (req, res) => {
    const files = req.files || [];
    if (files.length === 0) {
        return res.status(400).json({ code: 400, message: 'No files uploaded' });
    }
    uploadDebug(`✅ 多文件上传成功: ${files.length} 个文件`);
    const fileInfos = files.map(f => ({
        filename: f.filename,
        originalName: f.originalname,
        size: f.size,
        mimetype: f.mimetype
    }));
    res.json({
        code: 200,
        message: `${files.length} file(s) uploaded successfully`,
        data: { files: fileInfos }
    });
});

// 混合上传（任意字段）
app.post('/upload/mixed', wrapMulterWithTiming(upload.any(), 'mixed', { type: 'any' }), (req, res) => {
    const files = req.files || [];
    const fields = req.body;
    let parsedJson = null;
    if (fields.jsonData) {
        try {
            parsedJson = JSON.parse(fields.jsonData);
        } catch (err) {
            uploadDebug(`⚠️ jsonData 解析失败: ${err.message}`);
        }
    }
    const fileInfos = files.map(f => ({
        fieldname: f.fieldname,
        filename: f.filename,
        originalName: f.originalname,
        size: f.size,
        mimetype: f.mimetype
    }));
    res.json({
        code: 200,
        message: 'Mixed multipart data received',
        data: {
            files: fileInfos,
            formFields: fields,
            content: fields.content || null,
            jsonData: parsedJson
        }
    });
});

// ============= 下载路由 =============
// 静态文件服务（用于 public 目录下的文件，如 zip/png）
app.use('/download/static', express.static(path.join(__dirname, '../public'), { acceptRanges: true }));

// 获取上传文件列表
app.get('/download/files', (req, res) => {
    const uploadDir = config.upload.dest;
    if (!fs.existsSync(uploadDir)) {
        return res.json({ files: [] });
    }
    const files = fs.readdirSync(uploadDir).map(filename => {
        const stat = fs.statSync(path.join(uploadDir, filename));
        return {
            name: filename,
            size: stat.size,
            modified: stat.mtime
        };
    });
    res.json({ code: 200, files });
});

// 下载文件（支持断点续传）
app.get('/download/:filename', (req, res) => {
    const filename = req.params.filename;
    const filepath = path.join(config.upload.dest, filename);
    fs.stat(filepath, (err, stats) => {
        if (err || !stats.isFile()) {
            return res.status(404).json({ code: 404, message: 'File not found' });
        }
        const fileSize = stats.size;
        const range = req.headers.range;
        if (!range) {
            res.setHeader('Content-Length', fileSize);
            res.setHeader('Content-Type', 'application/octet-stream');
            res.setHeader('Accept-Ranges', 'bytes');
            const readStream = fs.createReadStream(filepath);
            readStream.pipe(res);
            return;
        }
        const parts = range.replace(/bytes=/, "").split("-");
        const start = parseInt(parts[0], 10);
        const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;
        if (start >= fileSize || end >= fileSize || start > end) {
            res.status(416).setHeader('Content-Range', `bytes */${fileSize}`);
            return res.json({ code: 416, message: 'Requested range not satisfiable' });
        }
        const chunkSize = (end - start) + 1;
        res.status(206);
        res.setHeader('Content-Range', `bytes ${start}-${end}/${fileSize}`);
        res.setHeader('Content-Length', chunkSize);
        res.setHeader('Content-Type', 'application/octet-stream');
        res.setHeader('Accept-Ranges', 'bytes');
        const readStream = fs.createReadStream(filepath, { start, end });
        readStream.pipe(res);
    });
});

// 删除文件
app.delete('/download/:filename', (req, res) => {
    const filename = req.params.filename;
    const filepath = path.join(config.upload.dest, filename);
    fs.stat(filepath, (err, stats) => {
        if (err || !stats.isFile()) {
            return res.status(404).json({ code: 404, message: 'File not found' });
        }
        fs.unlink(filepath, (err) => {
            if (err) {
                return res.status(500).json({ code: 500, message: 'Failed to delete file' });
            }
            appDebug(`文件已删除: ${filename}`);
            res.json({ code: 200, message: 'File deleted successfully', data: { filename } });
        });
    });
});

// ============= 生产环境：托管 React 前端 =============
// 判断是否为生产环境（通过环境变量 NODE_ENV）
if (process.env.NODE_ENV === 'production') {
    const frontendDist = path.join(__dirname, '../frontend/dist');
    // 检查构建产物是否存在
    if (fs.existsSync(frontendDist)) {
        app.use(express.static(frontendDist));
        // 所有非 API 路由返回 index.html（支持前端路由）
        app.get('*', (req, res) => {
            if (!req.path.startsWith('/api') && !req.path.startsWith('/upload') && !req.path.startsWith('/download')) {
                res.sendFile(path.join(frontendDist, 'index.html'));
            } else {
                next();
            }
        });
    } else {
        console.warn('⚠️ 前端构建产物不存在，请先运行 npm run build:client');
    }
}

// ============= 错误处理中间件 =============
app.use((err, req, res, next) => {
    console.error(`💥 错误发生:`, err);
    if (err instanceof multer.MulterError) {
        return res.status(400).json({
            code: 400,
            message: `Multer error: ${err.message}`,
            field: err.field,
            multerCode: err.code
        });
    }
    res.status(err.status || 500).json({
        code: err.status || 500,
        message: err.message || 'Internal server error'
    });
});

// ============= 启动服务器 =============
app.listen(PORT, HOST, () => {
    appDebug(`服务器启动在端口 ${PORT} +0ms`);
    appDebug(`调试模式已启用 +1ms`);
    console.log(`\n🚀 服务器已启动，访问 http://${HOST === '0.0.0.0' ? 'localhost' : HOST}:${PORT}`);
    console.log(`📁 上传目录: ${config.upload.dest}`);
    if (process.env.NODE_ENV === 'production') {
        console.log(`📦 前端静态文件: ${path.join(__dirname, '../frontend/dist')}`);
    } else {
        console.log(`💡 开发模式请运行前端开发服务器: cd frontend && npm run dev`);
    }
    console.log(`📊 调试输出: 设置环境变量 DEBUG=app:* 启用详细日志`);
});