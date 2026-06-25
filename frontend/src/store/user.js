import { defineStore } from 'pinia'

const isLikelyJwt = (value) => {
  if (typeof value !== 'string') return false
  const token = value.trim()
  return token.split('.').length === 3
}

const normalizeToken = (value) => {
  if (!value) return ''

  if (typeof value === 'string') {
    const token = value.trim()

    if (!token || token === '[object Object]') {
      return ''
    }

    if (token.startsWith('Bearer ')) {
      const strippedToken = token.slice(7).trim()
      return isLikelyJwt(strippedToken) ? strippedToken : ''
    }

    return isLikelyJwt(token) ? token : ''
  }

  if (typeof value === 'object') {
    return normalizeToken(value.data ?? value.token ?? '')
  }

  return ''
}

// Performance optimization: Cache localStorage data in memory
let cachedToken = normalizeToken(localStorage.getItem('token'))
let cachedUserInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')

if (cachedToken) {
  localStorage.setItem('token', cachedToken)
} else {
  localStorage.removeItem('token')
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: cachedToken,
    userInfo: cachedUserInfo
  }),
  actions: {
    setToken(token) {
      const normalizedToken = normalizeToken(token)
      this.token = normalizedToken
      cachedToken = normalizedToken

      if (normalizedToken) {
        localStorage.setItem('token', normalizedToken)
      } else {
        localStorage.removeItem('token')
      }
    },
    setUserInfo(userInfo) {
      this.userInfo = userInfo
      cachedUserInfo = userInfo
      localStorage.setItem('userInfo', JSON.stringify(userInfo))
    },
    updateUserInfo(partialUserInfo) {
      const nextUserInfo = {
        ...(this.userInfo || {}),
        ...(partialUserInfo || {})
      }
      this.userInfo = nextUserInfo
      cachedUserInfo = nextUserInfo
      localStorage.setItem('userInfo', JSON.stringify(nextUserInfo))
    },
    logout() {
      this.token = ''
      this.userInfo = {}
      cachedToken = ''
      cachedUserInfo = {}
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    },
    // Method to refresh cache from localStorage if needed
    refreshFromStorage() {
      cachedToken = normalizeToken(localStorage.getItem('token'))
      cachedUserInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
      this.token = cachedToken
      this.userInfo = cachedUserInfo

      if (cachedToken) {
        localStorage.setItem('token', cachedToken)
      } else {
        localStorage.removeItem('token')
      }
    }
  }
})
