const logger = require('../utils/logger');

/**
 * 请求详情日志中间件
 */
function requestLogger(req, res, next) {
    logger.request(`📨 [${new Date().toISOString()}] ${req.method} ${req.url}`);
    logger.request(`📡 Headers: ${JSON.stringify(req.headers, null, 2)}`);
    logger.request(`🔗 Query params: ${JSON.stringify(req.query)}`);
    
    // 如果有 body 且不是文件上传，也打印（文件上传会由 multer 单独处理）
    if (req.body && Object.keys(req.body).length > 0 && !req.is('multipart/form-data')) {
        logger.request(`📦 Body: ${JSON.stringify(req.body, null, 2)}`);
    }
    next();
}

module.exports = {
    requestLogger
};