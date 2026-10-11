import { defineStore } from 'pinia'
import { authState, ensureSession, login, logout, clearSession } from '../utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({ auth: authState, userInfo: {} }),
  getters: { token: state => state.auth.token },
  actions: {
    login(credentials) { return login(credentials) },
    ensureSession(force = false) { return ensureSession(force) },
    setUserInfo(userInfo) { this.userInfo = userInfo || {} },
    updateUserInfo(partial) { this.userInfo = { ...this.userInfo, ...partial } },
    clearSession() { clearSession(); this.userInfo = {} },
    async logout() { await logout(); this.userInfo = {} }
  }
})
