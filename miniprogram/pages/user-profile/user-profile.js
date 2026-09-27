const { get, put } = require('../../utils/request')

Page({
  data: {
    userInfo: {},
    saving: false,
    changing: false,
    genderIndex: 0,
    genderOptions: ['未知', '男', '女'],
    passwordForm: {
      oldPassword: '',
      newPassword: '',
      confirmPassword: ''
    }
  },

  onLoad() {
    this.loadUserInfo()
  },

  async loadUserInfo() {
    try {
      const userInfo = await get('/api/user/info', {}, { showLoading: false })
      const genderMap = { 0: 0, 1: 1, 2: 2 }
      const genderIndex = genderMap[userInfo.gender] || 0

      this.setData({ userInfo, genderIndex })
    } catch (err) {
      console.error('加载用户信息失败:', err)
    }
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`userInfo.${field}`]: e.detail.value })
  },

  onPasswordInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`passwordForm.${field}`]: e.detail.value })
  },

  onGenderChange(e) {
    const idx = e.detail.value
    const genderMap = { 0: 0, 1: 1, 2: 2 }
    this.setData({
      genderIndex: idx,
      'userInfo.gender': genderMap[idx]
    })
  },

  async saveProfile() {
    const { userInfo } = this.data

    this.setData({ saving: true })
    try {
      const data = {
        email: userInfo.email,
        phone: userInfo.phone,
        gender: userInfo.gender,
        age: userInfo.age ? Number(userInfo.age) : null,
        height: userInfo.height ? Number(userInfo.height) : null,
        weight: userInfo.weight ? Number(userInfo.weight) : null
      }

      await put('/api/user/info', data)
      wx.showToast({ title: '保存成功', icon: 'success' })

      // 更新全局用户信息
      const app = getApp()
      app.globalData.userInfo = { ...app.globalData.userInfo, ...data }
    } catch (err) {
      console.error('保存失败:', err)
    } finally {
      this.setData({ saving: false })
    }
  },

  async changePassword() {
    const { passwordForm } = this.data

    if (!passwordForm.oldPassword) {
      wx.showToast({ title: '请输入当前密码', icon: 'none' })
      return
    }
    if (!passwordForm.newPassword) {
      wx.showToast({ title: '请输入新密码', icon: 'none' })
      return
    }
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      wx.showToast({ title: '两次密码不一致', icon: 'none' })
      return
    }

    this.setData({ changing: true })
    try {
      await put('/api/user/password', {
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })

      wx.showToast({ title: '密码修改成功，请重新登录', icon: 'success' })

      // 清除登录态，跳转登录页
      setTimeout(() => {
        const app = getApp()
        app.clearLogin()
        wx.redirectTo({ url: '/pages/login/login' })
      }, 1500)
    } catch (err) {
      console.error('修改密码失败:', err)
    } finally {
      this.setData({ changing: false })
    }
  }
})
