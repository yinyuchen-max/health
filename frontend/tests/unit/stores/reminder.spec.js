import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useReminderStore } from '@/store/reminder'

// 模拟request模块
vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: [] })),
    post: vi.fn(() => Promise.resolve({ success: true, data: { id: 1 } })),
    put: vi.fn(() => Promise.resolve({ success: true, data: { id: 1 } })),
    delete: vi.fn(() => Promise.resolve({ success: true }))
  }
}))

describe('reminder store', () => {
  let store

  beforeEach(() => {
    setActivePinia(createPinia())
    store = useReminderStore()
  })

  it('initializes with correct default state', () => {
    expect(store.preferences).toEqual([])
    expect(store.notifications).toEqual([])
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('computes active reminders correctly', () => {
    // 添加一些测试数据
    store.preferences = [
      { id: 1, enabled: true, type: 'bloodPressure' },
      { id: 2, enabled: false, type: 'bloodSugar' },
      { id: 3, enabled: true, type: 'weight' }
    ]

    expect(store.activeReminders).toHaveLength(2)
    expect(store.activeReminders).toContainEqual(
      expect.objectContaining({ id: 1, enabled: true })
    )
    expect(store.activeReminders).toContainEqual(
      expect.objectContaining({ id: 3, enabled: true })
    )
  })

  it('calculates unread notification count', () => {
    store.notifications = [
      { id: 1, read: false },
      { id: 2, read: true },
      { id: 3, read: false }
    ]

    expect(store.unreadCount).toBe(2)
  })

  it('calculates total reminders', () => {
    store.preferences = [
      { id: 1, type: 'bloodPressure' },
      { id: 2, type: 'bloodSugar' }
    ]

    expect(store.totalReminders).toBe(2)
  })

  it('calculates completion rate', () => {
    store.preferences = [
      { id: 1, type: 'bloodPressure' },
      { id: 2, type: 'bloodSugar' }
    ]

    store.notifications = [
      { id: 1, completed: true, date: new Date().toISOString() },
      { id: 2, completed: false, date: new Date().toISOString() }
    ]

    expect(store.completionRate).toBe(50)
  })

  it('handles fetch preferences successfully', async () => {
    const mockResponse = [{ id: 1, type: 'bloodPressure' }]
    require('@/utils/request').default.get.mockResolvedValueOnce({
      data: mockResponse
    })

    await store.fetchPreferences()

    expect(store.preferences).toEqual(mockResponse)
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('handles fetch preferences failure', async () => {
    require('@/utils/request').default.get.mockRejectedValueOnce(
      new Error('Network error')
    )

    await store.fetchPreferences()

    expect(store.preferences).toEqual([])
    expect(store.loading).toBe(false)
    expect(store.error).toBe('获取提醒设置失败，请检查网络连接')
  })

  it('saves preference successfully', async () => {
    const mockPreference = { type: 'bloodPressure', time: '08:00' }
    require('@/utils/request').default.post.mockResolvedValueOnce({
      success: true,
      data: { id: 1, ...mockPreference }
    })

    const result = await store.savePreference(mockPreference)

    expect(result).toBe(true)
    expect(store.preferences).toHaveLength(1)
    expect(store.preferences[0]).toEqual(expect.objectContaining(mockPreference))
    expect(store.loading).toBe(false)
  })

  it('updates existing preference', async () => {
    const existingPreference = { id: 1, type: 'bloodPressure', time: '08:00' }
    store.preferences.push(existingPreference)

    require('@/utils/request').default.put.mockResolvedValueOnce({
      success: true,
      data: { id: 1, type: 'bloodSugar', time: '12:00' }
    })

    const updateData = { id: 1, type: 'bloodSugar', time: '12:00' }
    const result = await store.savePreference(updateData)

    expect(result).toBe(true)
    expect(store.preferences).toHaveLength(1)
    expect(store.preferences[0].type).toBe('bloodSugar')
  })

  it('deletes preference successfully', async () => {
    const preference = { id: 1, type: 'bloodPressure' }
    store.preferences.push(preference)

    require('@/utils/request').default.delete.mockResolvedValueOnce({
      success: true
    })

    const result = await store.deletePreference(1)

    expect(result).toBe(true)
    expect(store.preferences).toHaveLength(0)
  })

  it('toggles reminder status', async () => {
    store.preferences.push({ id: 1, enabled: true })

    require('@/utils/request').default.put.mockResolvedValueOnce({
      success: true
    })

    const result = await store.toggleReminder(1, false)

    expect(result).toBe(true)
    expect(store.preferences[0].enabled).toBe(false)
  })

  it('adds and manages notifications', () => {
    const notification = { id: 1, type: 'test', message: 'Test notification' }

    store.addNotification(notification)

    expect(store.notifications).toHaveLength(1)
    expect(store.notifications[0]).toMatchObject(notification)

    store.markNotificationAsRead(1)
    expect(store.notifications[0].read).toBe(true)

    store.clearNotifications()
    expect(store.notifications).toHaveLength(0)
  })

  it('exports and imports preferences', () => {
    store.preferences = [
      { id: 1, type: 'bloodPressure' },
      { id: 2, type: 'bloodSugar' }
    ]

    const exported = store.exportPreferences()
    expect(exported).toContain('"id": 1')
    expect(exported).toContain('"type": "bloodPressure"')

    const importData = '[{"id": 3, "type": "weight"}]'
    const success = store.importPreferences(importData)
    expect(success).toBe(true)
    expect(store.preferences).toHaveLength(1)
    expect(store.preferences[0].type).toBe('weight')
  })

  it('handles invalid import data', () => {
    const success = store.importPreferences('invalid json')
    expect(success).toBe(false)
    expect(store.preferences).toHaveLength(0)
  })

  it('clears errors', () => {
    store.error = 'Some error'
    store.clearError()
    expect(store.error).toBeNull()
  })
})