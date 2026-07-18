const debug = require('debug');

// 预定义的调试器实例，统一从 config 读取命名空间
let instances = {};

function getLogger(namespace) {
    if (!instances[namespace]) {
        instances[namespace] = debug(namespace);
    }
    return instances[namespace];
}

module.exports = {
    getLogger,
    // 快捷方法
    main: (...args) => getLogger('app:main')(...args),
    request: (...args) => getLogger('app:request')(...args),
    response: (...args) => getLogger('app:response')(...args),
    upload: (...args) => getLogger('app:upload')(...args),
    perf: (...args) => getLogger('app:perf')(...args)
};