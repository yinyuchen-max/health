// 地址集中配置；此文件会打包到客户端，只能放公开地址，不能放密钥。
const environments = {
  // 同一 Wi-Fi 的开发版真机访问本机后端；电脑 IP 变化时更新这里并重新上传开发版。
  develop: 'http://10.101.6.151:8080',
  trial: 'https://api.zpew.top',
  release: 'https://api.zpew.top'
}

// 仅开发版且运行在开发者工具时使用；真机使用上面配置的局域网地址。
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
