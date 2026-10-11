// 公开配置，修改 JSON 后重新编译/上传小程序。
const configuration = require('./runtime.json')
for (const name of ['requestTimeoutMs', 'smartHealthTimeoutMs', 'streamTimeoutMs']) {
  if (!Number.isSafeInteger(configuration[name]) || configuration[name] <= 0) {
    throw new Error(`小程序运行参数 ${name} 必须为正数毫秒`)
  }
}
module.exports = Object.freeze(configuration)
