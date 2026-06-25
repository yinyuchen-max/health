import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { notificationManager } from '@/utils/notification'

// 模拟Notification API
vi.stubGlobal('Notification', class Notification {
  constructor(title, options) {
    this.title = title
    this.options = options
    this.onclick = null
    this.onclose = null
    this.onerror = null
    this.permission = 'granted'
    return this
  }
})

describe('Notification Manager', () => {
  let originalNotification

  beforeEach(() => {
    // 保存原始Notification对象
    originalNotification = global.Notification

    // 重置localStorage
    localStorage.clear()

    // 模拟Notification.requestPermission
    global.Notification.requestPermission = vi.fn()
      .mockImplementation((callback) => {
        setTimeout(() => callback('granted'), 0)
      })
  })

  afterEach() {
    // 恢复原始Notification对象
    global.Notification = originalNotification
    localStorage.clear()
  }

  describe('Permission Management', () => {
    it('checks permission correctly', () => {
      const manager = new notificationManager.constructor()

      // 测试不支持的情况
      delete global.Notification
      expect(manager.checkPermission()).toBe('unsupported')

      // 恢复Notification
      global.Notification = class Notification {}

      // 测试不同权限状态
      Object.defineProperty(global.Notification, 'permission', {
        value: 'granted',
        writable: true
      })
      expect(manager.checkPermission()).toBe('granted')
    })

    it('requests permission successfully', async () => {
      const result = await notificationManager.requestPermission()

      expect(result).toBe(true)
      expect(global.Notification.requestPermission).toHaveBeenCalled()
    })
  })

  describe('Browser Notifications', () => {
    it('shows browser notification when permission granted', async () => {
      // 设置权限为已授予
      Object.defineProperty(global.Notification, 'permission', {
        value: 'granted',
        writable: true
      })

      const result = await notificationManager.showBrowserNotification(
        '测试标题',
        { body: '测试内容' }
      )

      expect(result).toBeDefined()
      expect(result.title).toBe('测试标题')
    })

    it('rejects when permission not granted', async () => {
      const result = notificationManager.showBrowserNotification('测试')
      await expect(result).rejects.toThrow('Notification permission not granted')
    })

    it('handles notification errors gracefully', () => {
      // 模拟Notification构造函数抛出错误
      const originalConstructor = global.Notification
      global.Notification = class Notification {
        constructor() {
          throw new Error('模拟错误')
        }
      }

      expect(() => {
        notificationManager.showBrowserNotification('测试')
      }).toThrow()

      // 恢复原始构造函数
      global.Notification = originalConstructor
    })
  })

  describe('In-App Notifications', () => {
    it('displays in-app notification and stores locally', () => {
      const notification = {
        id: 1,
        title: '测试通知',
        message: '测试内容',
        type: 'info'
      }

      const result = notificationManager.showInAppNotification(notification)

      expect(result).toEqual(expect.objectContaining(notification))

      // 检查是否存储到localStorage
      const stored = JSON.parse(localStorage.getItem('healthNotifications') || '[]')
      expect(stored).toHaveLength(1)
      expect(stored[0]).toEqual(expect.objectContaining({
        id: 1,
        read: false,
        timestamp: expect.any(String)
      }))
    })

    it('retrieves stored notifications', () => {
      const stored = [
        { id: 1, title: '通知1', read: false },
        { id: 2, title: '通知2', read: true }
      ]

      localStorage.setItem('healthNotifications', JSON.stringify(stored))

      const retrieved = notificationManager.getStoredNotifications()
      expect(retrieved).toEqual(stored)
    })

    it('marks notification as read', () => {
      const stored = [{ id: 1, title: '通知1', read: false }]
      localStorage.setItem('healthNotifications', JSON.stringify(stored))

      notificationManager.markAsRead(1)

      const updated = JSON.parse(localStorage.getItem('healthNotifications') || '[]')
      expect(updated[0].read).toBe(true)
    })

    it('clears all notifications', () => {
      localStorage.setItem('healthNotifications', JSON.stringify([{ id: 1 }]))

      notificationManager.clearAllNotifications()

      expect(localStorage.getItem('healthNotifications')).toBeNull()
    })
  })

  describe('Smart Notifications', () => {
    it('adjusts options based on user preferences', () => {
      // 设置用户偏好
      const preferences = {
        enabled: true,
        useBrowser: true,
        quietHours: { start: '22:00', end: '07:00' },
        frequency: 'delayed',
        sound: false,
        vibration: false
      }

      localStorage.setItem('notificationPreferences', JSON.stringify(preferences))

      const adjusted = notificationManager.adjustOptionsForUserPreferences(
        '测试',
        {},
        preferences
      )

      expect(adjusted.silent).toBe(true)
      expect(adjusted.delay).toBe(2000)
      expect(adjusted.vibrate).toBe(false)
    })

    it('gets user notification preferences', () => {
      const preferences = {
        reminder: { enabled: false, frequency: 'batch' },
        bloodPressure: { enabled: true },
        quietHours: { start: '23:00', end: '06:00' }
      }

      localStorage.setItem('notificationPreferences', JSON.stringify(preferences))

      const result = notificationManager.getUserNotificationPreferences('reminder')

      expect(result.enabled).toBe(false)
      expect(result.frequency).toBe('batch')
    })

    it('saves notification preferences', () => {
      const preferences = {
        reminder: { enabled: false },
        quietHours: { start: '23:00', end: '06:00' }
      }

      const success = notificationManager.saveNotificationPreferences(preferences)

      expect(success).toBe(true)

      const saved = JSON.parse(localStorage.getItem('notificationPreferences') || '{}')
      expect(saved.reminder.enabled).toBe(false)
      expect(saved.quietHours.start).toBe('23:00')
    })
  })

  describe('Utility Functions', () => {
    it('generates unique IDs', () => {
      const id1 = notificationManager.generateId()
      const id2 = notificationManager.generateId()

      expect(typeof id1).toBe('string')
      expect(id1.length).toBeGreaterThan(0)
      expect(id1).not.toBe(id2)
    })

    it('formats timestamps correctly', () => {
      const now = Date.now()

      // 测试"刚刚"
      expect(notificationManager.formatTimestamp(now)).toBe('刚刚')

      // 测试分钟
      expect(notificationManager.formatTimestamp(now - 300000)).toMatch(/分钟前/)

      // 测试小时
      expect(notificationManager.formatTimestamp(now - 7200000)).toMatch(/小时前/)

      // 测试日期
      expect(notificationManager.formatTimestamp(now - 86400000)).toMatch(/\w+\d+日/)
    })

    it('parses time strings correctly', () => {
      expect(notificationManager.parseTime('08:30')).toBeCloseTo(8.5)
      expect(notificationManager.parseTime('22:15')).toBeCloseTo(22.25)
      expect(notificationManager.parseTime('00:45')).toBeCloseTo(0.75)
    })

    it('delays execution', async () => {
      const start = Date.now()
      await notificationManager.delay(100)
      const end = Date.now()

      expect(end - start).toBeGreaterThanOrEqual(90)
      expect(end - start).toBeLessThan(200)
    })
  })

  describe('Event Handling', () => {
    it('listens for health-notification events', () => {
      const event = new CustomEvent('health-notification', {
        detail: { id: 1, title: 'Test' }
      })

      // 模拟事件监听
      window.dispatchEvent(event)

      // 检查通知是否被标记为已读
      const stored = JSON.parse(localStorage.getItem('healthNotifications') || '[]')
      expect(stored).toHaveLength(1)
      expect(stored[0].read).toBe(true)
    })

    it('listens for clear-health-notifications events', () => {
      localStorage.setItem('healthNotifications', JSON.stringify([{ id: 1 }]))

      const event = new CustomEvent('clear-health-notifications')
      window.dispatchEvent(event)

      expect(localStorage.getItem('healthNotifications')).toBeNull()
    })
  })
})