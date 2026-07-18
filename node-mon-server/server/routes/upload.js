const express = require('express');
const multer = require('multer');
const path = require('path');
const config = require('../config');
const logger = require('../utils/logger');
const { ensureDir, generateUniqueFilename } = require('../utils/fileHelper');
const { wrapMulterWithTiming } = require('../middleware/performance');

const router = express.Router();

// 确保上传目录存在
ensureDir(config.upload.dest);

// 配置 multer 存储
const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        logger.upload(`📂 目标目录: ${config.upload.dest}`);
        cb(null, config.upload.dest);
    },
    filename: (req, file, cb) => {
        const filename = generateUniqueFilename(file.originalname);
        logger.upload(`📄 生成文件名: ${filename}`);
        logger.upload(`   - 原始名: ${file.originalname}`);
        cb(null, filename);
    }
});

// 文件过滤器（用于记录和限制类型）
const fileFilter = (req, file, cb) => {
    logger.upload(`🔍 文件过滤检查:`);
    logger.upload(`   - 字段名: ${file.fieldname}`);
    logger.upload(`   - 文件名: ${file.originalname}`);
    logger.upload(`   - MIME类型: ${file.mimetype}`);
    logger.upload(`   - 大小: ${file.size || '未知'} bytes`);
    cb(null, true);
};

const upload = multer({
    storage,
    limits: config.upload.limits,
    fileFilter
});

// 单文件上传
router.post('/single', wrapMulterWithTiming(upload.single('file'), 'file', { type: 'single' }), (req, res) => {
    const file = req.file;
    if (!file) {
        logger.upload(`⚠️  没有收到文件`);
        return res.status(400).json({ code: 400, message: 'No file uploaded' });
    }
    
    logger.upload(`✅ 文件上传成功:`);
    logger.upload(`   - 原始名: ${file.originalname}`);
    logger.upload(`   - 大小: ${file.size} bytes`);
    logger.upload(`   - 保存路径: ${file.path}`);
    
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
router.post('/multiple', wrapMulterWithTiming(upload.array('files', 5), 'files', { type: 'array' }), (req, res) => {
    const files = req.files || [];
    if (files.length === 0) {
        return res.status(400).json({ code: 400, message: 'No files uploaded' });
    }
    
    logger.upload(`✅ 多文件上传成功: ${files.length} 个文件`);
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

// 混合字段上传（普通字段 + 文件）
router.post('/with-fields', wrapMulterWithTiming(upload.fields([
    { name: 'avatar', maxCount: 1 },
    { name: 'gallery', maxCount: 3 }
]), 'multiple fields', { type: 'fields' }), (req, res) => {
    const files = req.files;
    const fields = req.body;
    res.json({
        code: 200,
        message: 'Mixed upload successful',
        data: { fields, files: Object.keys(files).reduce((acc, key) => {
            acc[key] = files[key].map(f => f.originalname);
            return acc;
        }, {}) }
    });
});

// ============= 新增：多功能混合上传（支持文件+文本+JSON+表单） =============
// 使用 .any() 允许接收任意字段的文件和文本
router.post('/mixed', wrapMulterWithTiming(upload.any(), 'mixed', { type: 'any' }), (req, res) => {
    const files = req.files || [];
    const fields = req.body; // 所有非文件的字段，包括 text、JSON 字符串等
    
    logger.upload(`🎯 混合上传请求处理`);
    logger.upload(`📎 文件数量: ${files.length}`);
    logger.upload(`📝 表单字段: ${JSON.stringify(fields)}`);
    
    // 处理 JSON 字段：如果 fields 中存在 jsonData 字段，尝试解析为 JSON
    let parsedJson = null;
    if (fields.jsonData) {
        try {
            parsedJson = JSON.parse(fields.jsonData);
            logger.upload(`🔧 解析 jsonData 成功: ${JSON.stringify(parsedJson)}`);
        } catch (err) {
            logger.upload(`⚠️ jsonData 解析失败: ${err.message}`);
        }
    }
    
    // 处理 content 字段（纯文本内容）
    const contentText = fields.content || null;
    if (contentText) {
        logger.upload(`📄 接收 content 文本: ${contentText.substring(0, 100)}${contentText.length > 100 ? '...' : ''}`);
    }
    
    // 构建响应数据
    const fileInfos = files.map(f => ({
        fieldname: f.fieldname,
        filename: f.filename,
        originalName: f.originalname,
        size: f.size,
        mimetype: f.mimetype
    }));
    
    const responseData = {
        code: 200,
        message: 'Mixed multipart data received successfully',
        data: {
            files: fileInfos,
            formFields: fields,
            content: contentText,
            jsonData: parsedJson
        }
    };
    
    logger.upload(`✅ 混合上传处理完成，返回响应`);
    res.json(responseData);
});

module.exports = router;