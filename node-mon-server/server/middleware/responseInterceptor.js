const logger = require('../utils/logger');

/**
 * 响应拦截中间件：记录 res.json、res.send、res.download 返回的内容
 */
function responseInterceptor(req, res, next) {
    // 保存原始方法
    const originalJson = res.json;
    const originalSend = res.send;
    const originalDownload = res.download;

    // 拦截 res.json
    res.json = function (data) {
        logger.response(`📤 响应状态: ${res.statusCode}`);
        logger.response(`📦 响应体 (JSON): ${JSON.stringify(data, null, 2)}`);
        return originalJson.call(this, data);
    };

    // 拦截 res.send
    res.send = function (body) {
        let logBody = body;
        if (typeof body === 'string') {
            try {
                // 尝试解析为 JSON 并美化
                const json = JSON.parse(body);
                logBody = JSON.stringify(json, null, 2);
            } catch (e) {
                // 不是 JSON，截断过长的字符串
                logBody = body.length > 500 ? body.substring(0, 500) + '...' : body;
            }
        } else if (Buffer.isBuffer(body)) {
            logBody = `<Buffer ${body.length} bytes>`;
        }
        logger.response(`📤 响应状态: ${res.statusCode}`);
        logger.response(`📦 响应体 (send): ${logBody}`);
        return originalSend.call(this, body);
    };

    // 拦截 res.download
    res.download = function (filepath, filename, options, callback) {
        logger.response(`📤 响应类型: 文件下载`);
        logger.response(`📄 文件路径: ${filepath}`);
        logger.response(`📄 文件名: ${filename || require('path').basename(filepath)}`);
        return originalDownload.call(this, filepath, filename, options, callback);
    };

    next();
}

module.exports = responseInterceptor;