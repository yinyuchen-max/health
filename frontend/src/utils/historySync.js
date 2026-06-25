const HISTORY_SYNC_EVENT = 'history-records-changed'
const HISTORY_SYNC_STORAGE_KEY = 'historyRecordsChangedAt'

export const notifyHistoryChanged = () => {
  const timestamp = String(Date.now())

  window.dispatchEvent(new CustomEvent(HISTORY_SYNC_EVENT, { detail: { timestamp } }))

  try {
    localStorage.setItem(HISTORY_SYNC_STORAGE_KEY, timestamp)
  } catch (error) {
    console.warn('Failed to persist history sync event:', error)
  }
}

export const bindHistorySync = (callback) => {
  if (typeof callback !== 'function') {
    return () => {}
  }

  const handleCustomEvent = () => callback()
  const handleStorageEvent = (event) => {
    if (event.key === HISTORY_SYNC_STORAGE_KEY) {
      callback()
    }
  }
  const handleVisibilityChange = () => {
    if (document.visibilityState === 'visible') {
      callback()
    }
  }

  window.addEventListener(HISTORY_SYNC_EVENT, handleCustomEvent)
  window.addEventListener('storage', handleStorageEvent)
  document.addEventListener('visibilitychange', handleVisibilityChange)

  return () => {
    window.removeEventListener(HISTORY_SYNC_EVENT, handleCustomEvent)
    window.removeEventListener('storage', handleStorageEvent)
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  }
}
