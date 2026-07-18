const express = require('express');
const path = require('path');
const fs = require('fs');
const config = require('../config');
const { getFileList } = require('../utils/fileHelper');
const logger = require('../utils/logger');

const router = express.Router();

// 静态文件服务（用于直接访问 public 目录下的文件，如 zip/png）
// express.static 默认支持 Range 请求（需要设置 acceptRanges: true）
router.use('/static', express.static(config.staticDir, { acceptRanges: true }));

// 获取上传文件列表（可下载的文件）
router.get('/files', (req, res) => {
    const files = getFileList(config.upload.dest);
    res.json({ code: 200, files });
});

// 下载文件（支持断点续传）
router.get('/:filename', (req, res) => {
    const filename = req.params.filename;
    const filepath = path.join(config.upload.dest, filename);
    
    fs.stat(filepath, (err, stats) => {
        if (err || !stats.isFile()) {
            return res.status(404).json({ code: 404, message: 'File not found' });
        }
        
        const fileSize = stats.size;
        const range = req.headers.range;
        
        // 如果没有 range 头，返回整个文件
        if (!range) {
            logger.main(`下载文件: ${filename} (完整)`);
            res.setHeader('Content-Length', fileSize);
            res.setHeader('Content-Type', 'application/octet-stream');
            res.setHeader('Accept-Ranges', 'bytes');
            const readStream = fs.createReadStream(filepath);
            readStream.pipe(res);
            return;
        }
        
        // 解析 Range 头，格式如: bytes=start-end
        const parts = range.replace(/bytes=/, "").split("-");
        const start = parseInt(parts[0], 10);
        const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;
        
        // 验证范围有效性
        if (start >= fileSize || end >= fileSize || start > end) {
            res.status(416).setHeader('Content-Range', `bytes */${fileSize}`);
            return res.json({ code: 416, message: 'Requested range not satisfiable' });
        }
        
        const chunkSize = (end - start) + 1;
        logger.main(`下载文件: ${filename} (范围: ${start}-${end}, 大小: ${chunkSize} bytes)`);
        
        res.status(206); // Partial Content
        res.setHeader('Content-Range', `bytes ${start}-${end}/${fileSize}`);
        res.setHeader('Content-Length', chunkSize);
        res.setHeader('Content-Type', 'application/octet-stream');
        res.setHeader('Accept-Ranges', 'bytes');
        
        const readStream = fs.createReadStream(filepath, { start, end });
        readStream.pipe(res);
    });
});

module.exports = router;