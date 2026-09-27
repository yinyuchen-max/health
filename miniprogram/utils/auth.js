/**
 * 登录态管理工具
 */

const { post, get } = require('./request')
const app = getApp()

/**
 * 微信登录
 * 流程：wx.login 获取 code → 后端 /api/user/wechat-login 换取 JWT
 */
async function wxLogin() {
  return new Promise((resolve, reject) => {
    wx.login({
      success: async (loginRes) => {
        if (!loginRes.code) {
          reject({ message: '微信登录失败，请重试' })
          return
        }
        try {
          // 调用后端微信登录接口
          const result = await post('/api/user/wechat-login', { code: loginRes.code })
          // result: { token, userInfo }
          if (result && result.token) {
            app.setLogin(result.token, result.userInfo || {})
            resolve(result)
          } else {
            reject({ message: '登录响应异常' })
          }
        } catch (err) {
          reject(err)
        }
      },
      fail: (err) => {
        reject({ message: '微信登录失败', originalError: err })
      }
    })
  })
}

/**
 * 获取当前用户信息
 */
async function fetchUserInfo() {
  try {
    const userInfo = await get('/api/user/info')
    if (userInfo) {
      app.globalData.userInfo = userInfo
      wx.setStorageSync('userInfo', JSON.stringify(userInfo))
    }
    return userInfo
  } catch (err) {
    console.error('获取用户信息失败:', err)
    return null
  }
}

/**
 * 退出登录
 */
function logout() {
  app.clearLogin()
  wx.redirectTo({ url: '/pages/login/login' })
}

/**
 * 检查是否已登录
 */
function isLoggedIn() {
  return !!app.globalData.token
}

module.exports = {
  wxLogin,
  fetchUserInfo,
  logout,
  isLoggedIn
}
