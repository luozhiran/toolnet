const logger = require('../utils/logger');
const config = require('../config');

/**
 * 全局请求耗时监控中间件
 */
function performanceMonitor(req, res, next) {
    const start = Date.now();
    req.startTime = start;
    
    res.on('finish', () => {
        const duration = Date.now() - start;
        const statusCode = res.statusCode;
        const method = req.method;
        const url = req.url;
        
        logger.perf(`${method} ${url} - ${duration}ms - ${statusCode}`);
        if (duration > config.perf.slowRequest) {
            console.warn(`⚠️  慢请求警告: ${method} ${url} 耗时 ${duration}ms`);
        }
    });
    next();
}

/**
 * 包装 multer 中间件，监控其处理耗时
 */
function wrapMulterWithTiming(multerMiddleware, fieldName, options = {}) {
    return (req, res, next) => {
        const start = Date.now();
        logger.upload(`🎯 开始处理文件上传 (字段: ${fieldName})`);
        
        multerMiddleware(req, res, (err) => {
            const duration = Date.now() - start;
            logger.perf(`⏱️  multer.${options.type || 'single'}("${fieldName}") 处理耗时: ${duration}ms`);
            if (duration > config.perf.slowMulter) {
                console.warn(`⚠️  multer 处理较慢: ${duration}ms`);
            }
            next(err);
        });
    };
}

module.exports = {
    performanceMonitor,
    wrapMulterWithTiming
};