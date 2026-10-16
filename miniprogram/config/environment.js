// 地址集中配置；此文件会打包到客户端，只能放公开地址，不能放密钥。
const environments = {
  develop: 'https://api.zpew.top',
  trial: 'https://api.zpew.top',
  release: 'https://api.zpew.top'
}

// 仅开发版且运行在开发者工具时使用；开发版真机默认访问公网服务。
const devtoolsBaseUrl = 'http://localhost:8080'

function resolveBaseUrl(wxApi) {
  const version = wxApi.getAccountInfoSync().miniProgram.envVersion
  if (!Object.prototype.hasOwnProperty.call(environments, version)) {
    throw new Error(`未配置的小程序环境：${version}`)
  }
  if (version === 'develop' && wxApi.getDeviceInfo().platform === 'devtools') {
    return devtoolsBaseUrl
  }
  return environments[version]
}

const billingWebUrl = 'https://zpew.top/app/ai-settings'
module.exports = { resolveBaseUrl, billingWebUrl }
