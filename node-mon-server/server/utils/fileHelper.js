const fs = require('fs');
const path = require('path');

/**
 * 确保目录存在，若不存在则创建
 */
function ensureDir(dirPath) {
    if (!fs.existsSync(dirPath)) {
        fs.mkdirSync(dirPath, { recursive: true });
    }
}

/**
 * 获取目录下的文件列表（包含大小、修改时间）
 */
function getFileList(dirPath) {
    if (!fs.existsSync(dirPath)) {
        return [];
    }
    const files = fs.readdirSync(dirPath);
    return files.map(filename => {
        const fullPath = path.join(dirPath, filename);
        const stat = fs.statSync(fullPath);
        return {
            name: filename,
            size: stat.size,
            modified: stat.mtime
        };
    });
}

/**
 * 生成唯一文件名
 */
function generateUniqueFilename(originalName) {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    return `${uniqueSuffix}-${originalName}`;
}

module.exports = {
    ensureDir,
    getFileList,
    generateUniqueFilename
};