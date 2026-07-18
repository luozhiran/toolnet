const multer = require('multer');
const logger = require('../utils/logger');

/**
 * 全局错误处理中间件
 */
function errorHandler(err, req, res, next) {
    console.error(`💥 错误发生:`, err);
    logger.main(`错误堆栈: ${err.stack}`);
    
    // Multer 特定错误
    if (err instanceof multer.MulterError) {
        return res.status(400).json({
            code: 400,
            message: `Multer error: ${err.message}`,
            field: err.field,
            multerCode: err.code
        });
    }
    
    // 其他错误
    res.status(err.status || 500).json({
        code: err.status || 500,
        message: err.message || 'Internal server error'
    });
}

module.exports = errorHandler;