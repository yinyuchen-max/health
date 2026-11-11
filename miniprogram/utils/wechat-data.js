const { get, post, put } = require('./request')
let syncing
let syncingWithPermission = false

function wxCall(method, options = {}) {
  return new Promise((resolve, reject) => wx[method]({ ...options, success: resolve, fail: reject }))
}

function syncWeRun({ requestPermission = false } = {}) {
  if (syncing) {
    // 自动读取尚在进行时，主动点击仍需有机会发起授权。
    if (requestPermission && !syncingWithPermission)
      return syncing.catch(() => {}).then(() => syncWeRun({ requestPermission: true }))
    return syncing
  }
  syncingWithPermission = requestPermission
  syncing = doSync(requestPermission).finally(() => { syncing = null })
  return syncing
}
async function doSync(requestPermission) {
  const app = getApp()
  const cached = await get('/api/user/wechat/steps', {}, { showLoading: false, showError: false })
  let user = app.globalData.userInfo || {}
  if (user.wechatBound === undefined) {
    user = await get('/api/user/info', {}, { showLoading: false, showError: false })
    app.globalData.userInfo = user
  }
  if (!user.wechatBound) {
    if (requestPermission) throw new Error('请先使用微信登录后同步微信运动')
    return cached
  }
  try {
    const setting = await wxCall('getSetting')
    if (!setting.authSetting['scope.werun']) {
      if (!requestPermission) return cached
      if (setting.authSetting['scope.werun'] === false) {
        const result = await wxCall('showModal', { title: '授权微信运动', content: '请在设置中允许微信运动权限，再返回同步。', confirmText: '打开设置' })
        if (!result.confirm) throw new Error('未授权微信运动，可稍后再同步')
        const settings = await wxCall('openSetting')
        if (!settings.authSetting['scope.werun']) throw new Error('未授权微信运动，可稍后再同步')
      } else await wxCall('authorize', { scope: 'scope.werun' })
    }
    // JWT 续期不会刷新微信 session_key，解密前单独验证当前微信身份并更新 session_key。
    const login = await wxCall('login')
    if (!login.code) throw new Error('微信身份获取失败，请重试')
    await post('/api/user/wechat/session', { code: login.code }, { showLoading: false, showError: false })
    const data = await wxCall('getWeRunData')
    await post('/api/user/wechat/decrypt-run-data', { encryptedData: data.encryptedData, iv: data.iv }, { showLoading: false, showError: false })
    return await get('/api/user/wechat/steps', {}, { showLoading: false, showError: false })
  } catch (error) {
    if (requestPermission) throw new Error(error.message || '微信运动同步失败，请确认权限后重试')
    return cached
  }
}

async function avatarBase64(path) {
  let filePath = path
  try { filePath = (await wxCall('compressImage', { src: path, quality: 70, compressedWidth: 256, compressedHeight: 256 })).tempFilePath }
  catch { /* 压缩不可用时由后端校验尺寸和文件大小 */ }
  return new Promise((resolve, reject) => {
    wx.getFileSystemManager().readFile({ filePath, encoding: 'base64', success: result => resolve(result.data), fail: reject })
  })
}
async function saveWechatProfile(nickname, avatarPath) {
  const data = {}
  if (nickname?.trim()) data.nickname = nickname.trim()
  if (avatarPath) data.avatarBase64 = await avatarBase64(avatarPath)
  if (Object.keys(data).length) await put('/api/user/wechat/profile', data, { showLoading: false })
  const user = await get('/api/user/info', {}, { showLoading: false })
  const app = getApp()
  app.globalData.userInfo = user
  wx.setStorageSync('userInfo', JSON.stringify(user))
  return user
}
function avatarUrl(value) {
  return value?.startsWith('/api/') ? `${getApp().globalData.baseUrl}${value}` : value || ''
}
module.exports = { syncWeRun, saveWechatProfile, avatarUrl }
