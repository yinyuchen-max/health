import { apiBaseUrl } from './auth'

export const displayName = user => user?.nickname || user?.username || '用户'
export function avatarUrl(user) {
  const value = user?.avatar || ''
  if (value.startsWith('/api/user/wechat/avatar/')) {
    return `${apiBaseUrl.replace(/\/$/, '').replace(/\/api$/, '')}${value}`
  }
  return /^https?:\/\//.test(value) ? value : ''
}
