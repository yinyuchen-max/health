import { defineStore } from 'pinia'
import request from '../utils/request'
import { useUserStore } from './user'

export const useReminderStore = defineStore('reminder', {
  state: () => ({
    preferences: [],
    notifications: [],
    history: [],
    loading: false,
    error: null
  }),

  getters: {
    activeReminders: (state) => state.preferences.filter(p => p.enabled),
    unreadCount: (state) => state.notifications.filter(n => !n.read).length,
    totalReminders: (state) => state.preferences.length,
    completionRate: (state) => {
      if (state.preferences.length === 0) return 0
      const completedToday = state.notifications.filter(n =>
        n.type && n.completed && new Date(n.date).toDateString() === new Date().toDateString()
      ).length
      return Math.round((completedToday / state.preferences.length) * 100)
    },
    reminderHistory: (state) => state.history,
    todayCompleted: (state) => {
      const today = new Date().toDateString()
      return state.history.filter(h =>
        h.completedAt && new Date(h.completedAt).toDateString() === today
      )
    },
    weeklyStats: (state) => {
      const weekAgo = new Date()
      weekAgo.setDate(weekAgo.getDate() - 7)

      return state.history
        .filter(h => new Date(h.scheduledAt) >= weekAgo)
        .reduce((stats, h) => {
          stats.total++
          if (h.completed) stats.completed++
          if (h.type) stats.byType[h.type] = (stats.byType[h.type] || 0) + 1
          return stats
        }, {
          total: 0,
          completed: 0,
          byType: {}
        })
    }
  },

  actions: {
    async fetchPreferences() {
      this.loading = true
      this.error = null
      try {
        // 从用户store获取用户ID
        const userStore = useUserStore()
        const userId = userStore.userInfo?.id || 1

        const response = await request.get('/reminder/preferences', {
          params: { userId }
        })
        this.preferences = response?.data || []
      } catch (error) {
        console.error('获取提醒偏好失败:', error)

        // 如果 API 请求失败，尝试从 localStorage 读取
        try {
          const existingData = localStorage.getItem('reminderPreferences')
          if (existingData) {
            const allPreferences = JSON.parse(existingData)
            // 过滤出当前用户的提醒
            this.preferences = allPreferences.filter(p => p.userId === userId)
          } else {
            // 如果没有本地数据，使用默认数据
            this.preferences = [
              {
                id: 1,
                userId: userId,
                type: 'bloodPressure',
                time: '08:00',
                frequency: 'daily',
                enabled: true,
                smartMode: false,
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString()
              }
            ]
          }
          this.error = null
        } catch (localError) {
          console.error('读取本地存储失败:', localError)
          this.error = '获取提醒设置失败，请检查网络连接'
          this.preferences = []
        }
      } finally {
        this.loading = false
      }
    },

    async savePreference(preference) {
      this.loading = true
      this.error = null
      try {
        let response

        if (preference.id) {
          // 更新现有提醒
          response = await request.put(`/reminder/preferences/${preference.id}`, preference)
        } else {
          // 创建新提醒
          console.log('发送保存请求，数据:', preference)
          response = await request.post('/reminder/preferences', preference)
        }

        console.log('保存提醒响应:', response)
        console.log('响应类型:', typeof response)
        console.log('响应包含code:', response && 'code' in response)

        if (response && response.code === 200) {
          // 更新本地状态
          const index = this.preferences.findIndex(p => p.id === preference.id)
          if (index !== -1 && response.data) {
            this.preferences[index] = response.data
          } else if (response.data) {
            this.preferences.push(response.data)
          } else {
            // 如果没有返回数据，使用本地数据
            this.preferences.push(preference)
          }
          return true
        } else {
          this.error = response?.message || '保存失败'
          return false
        }
      } catch (error) {
        console.error('保存提醒偏好失败:', error)

        // 保存到 localStorage 作为备用
        try {
          if (!preference.id) {
            preference.id = Date.now()
          }

          // 从 localStorage 读取现有数据
          const existingData = localStorage.getItem('reminderPreferences')
          let allPreferences = existingData ? JSON.parse(existingData) : []

          // 添加或更新这个提醒
          const index = allPreferences.findIndex(p => p.id === preference.id)
          if (index !== -1) {
            allPreferences[index] = preference
          } else {
            allPreferences.push(preference)
          }

          // 保存回 localStorage
          localStorage.setItem('reminderPreferences', JSON.stringify(allPreferences))

          // 更新内存中的数据
          this.preferences = allPreferences

          this.error = null
          return true
        } catch (localError) {
          console.error('本地存储失败:', localError)
          this.error = '保存失败，请稍后重试'
          return false
        }
      } finally {
        this.loading = false
      }
    },

    async deletePreference(id) {
      this.loading = true
      this.error = null
      try {
        const response = await request.delete(`/reminder/preferences/${id}`)

        if (response.success) {
          this.preferences = this.preferences.filter(p => p.id !== id)
          return true
        } else {
          this.error = response.message || '删除失败'
          return false
        }
      } catch (error) {
        console.error('删除提醒偏好失败:', error)

        // 从 localStorage 删除
        try {
          const existingData = localStorage.getItem('reminderPreferences')
          if (existingData) {
            let allPreferences = JSON.parse(existingData)
            allPreferences = allPreferences.filter(p => p.id !== id)
            localStorage.setItem('reminderPreferences', JSON.stringify(allPreferences))
            this.preferences = this.preferences.filter(p => p.id !== id)
          }
          return true
        } catch (localError) {
          console.error('本地删除失败:', localError)
          this.error = '删除失败，请稍后重试'
          return false
        }
      } finally {
        this.loading = false
      }
    },

    async toggleReminder(id, enabled) {
      const preference = this.preferences.find(p => p.id === id)
      if (!preference) return false

      this.loading = true
      try {
        const response = await request.put(`/reminder/preferences/${id}/toggle`, {
          enabled: enabled
        })

        if (response.success) {
          preference.enabled = enabled
          return true
        } else {
          this.error = response.message || '操作失败'
          return false
        }
      } catch (error) {
        console.error('切换提醒状态失败:', error)
        this.error = '操作失败，请稍后重试'
        return false
      } finally {
        this.loading = false
      }
    },

    async updateEffectivenessScore(id, score) {
      try {
        const response = await request.put(`/reminder/preferences/${id}/score`, {
          score: score
        })

        if (response.success) {
          const preference = this.preferences.find(p => p.id === id)
          if (preference) {
            preference.effectivenessScore = score
            preference.lastAdjusted = new Date().toISOString()
          }
          return true
        }
        return false
      } catch (error) {
        console.error('更新效果评分失败:', error)
        return false
      }
    },

    addNotification(notification) {
      this.notifications.unshift({
        ...notification,
        read: false,
        timestamp: new Date().toISOString()
      })
    },

    markNotificationAsRead(id) {
      const notification = this.notifications.find(n => n.id === id)
      if (notification) {
        notification.read = true
      }
    },

    clearNotifications() {
      this.notifications = []
    },

    clearError() {
      this.error = null
    },

    // 智能推荐功能
    async generateSmartRecommendations() {
      this.loading = true
      try {
        const userStore = useUserStore()
        const userId = userStore.userInfo?.id || 1
        const response = await request.get('/reminder/smart-recommendations', {
          params: { userId }
        })

        if (response?.code === 200 && Array.isArray(response.data)) {
          return response.data
        }
        return []
      } catch (error) {
        console.error('生成智能推荐失败:', error)
        return []
      } finally {
        this.loading = false
      }
    },

    // 批量操作
    async bulkUpdatePreferences(updates) {
      this.loading = true
      try {
        const response = await request.post('/reminder/preferences/bulk-update', {
          updates: updates
        })

        if (response.success) {
          // 更新本地数据
          updates.forEach(update => {
            const index = this.preferences.findIndex(p => p.id === update.id)
            if (index !== -1) {
              Object.assign(this.preferences[index], update)
            }
          })
          return true
        }
        return false
      } catch (error) {
        console.error('批量更新失败:', error)
        return false
      } finally {
        this.loading = false
      }
    },

    // 导入导出配置
    exportPreferences() {
      return JSON.stringify(this.preferences, null, 2)
    },

    importPreferences(data) {
      try {
        const imported = JSON.parse(data)
        if (Array.isArray(imported)) {
          this.preferences = imported
          return true
        }
        return false
      } catch (error) {
        console.error('导入偏好设置失败:', error)
        return false
      }
    },

    // 历史记录管理
    addHistoryRecord(record) {
      this.history.unshift({
        ...record,
        id: record.id || Date.now(),
        completedAt: new Date().toISOString()
      })

      // 限制历史记录数量
      if (this.history.length > 1000) {
        this.history.splice(1000)
      }
    },

    markHistoryAsCompleted(historyId, completed = true) {
      const record = this.history.find(h => h.id === historyId)
      if (record) {
        record.completed = completed
        record.completedAt = completed ? new Date().toISOString() : null
        return true
      }
      return false
    },

    getHistoryByDateRange(startDate, endDate) {
      return this.history.filter(h => {
        const recordDate = new Date(h.scheduledAt)
        return recordDate >= startDate && recordDate <= endDate
      })
    },

    getHistoryByType(type) {
      return this.history.filter(h => h.type === type)
    },

    exportHistory(startDate, endDate) {
      const filteredHistory = this.getHistoryByDateRange(
        startDate || new Date(Date.now() - 30 * 24 * 60 * 60 * 1000),
        endDate || new Date()
      )

      return JSON.stringify(filteredHistory, null, 2)
    },

    async importHistory(data) {
      try {
        const imported = JSON.parse(data)
        if (Array.isArray(imported)) {
          imported.forEach(record => {
            this.addHistoryRecord(record)
          })
          return true
        }
        return false
      } catch (error) {
        console.error('导入历史记录失败:', error)
        return false
      }
    },

    clearHistory() {
      this.history = []
      localStorage.removeItem('reminderHistory')
    },

    // 本地存储历史记录
    saveHistoryLocally() {
      try {
        localStorage.setItem('reminderHistory', JSON.stringify(this.history))
      } catch (error) {
        console.error('保存历史记录到本地失败:', error)
      }
    },

    loadHistoryFromLocal() {
      try {
        const stored = localStorage.getItem('reminderHistory')
        if (stored) {
          this.history = JSON.parse(stored)
        }
      } catch (error) {
        console.error('从本地加载历史记录失败:', error)
      }
    }
  }
})
