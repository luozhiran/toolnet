const path = require('path');

module.exports = {
    // 服务器配置
    port: process.env.PORT || 3000,
    host: process.env.HOST || '0.0.0.0',
    
    // 文件上传配置
    upload: {
        dest: path.join(__dirname, '../uploads'),
        limits: {
            fileSize: 10 * 1024 * 1024, // 10MB
            files: 5
        },
        allowedMimeTypes: ['image/png', 'image/jpeg', 'application/zip', 'application/pdf']
    },
    
    // 静态文件目录
    staticDir: path.join(__dirname, '../public'),
    
    // 性能阈值（ms）
    perf: {
        slowRequest: 1000,   // 慢请求阈值
        slowMulter: 500      // multer 处理慢阈值
    },
    
    // 调试模块命名空间
    debugNamespaces: {
        main: 'app:main',
        request: 'app:request',
        response: 'app:response',
        upload: 'app:upload',
        perf: 'app:perf'
    }
};