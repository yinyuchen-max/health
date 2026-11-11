import { defineStore } from 'pinia'
import request from '../utils/request'
import { useUserStore } from './user'

function responseData(response, fallback) {
  if (response?.code !== 200 && response?.code !== 0) throw new Error(response?.message || fallback)
  return response.data
}

function mutationSucceeded(response, fallback) {
  if (responseData(response, fallback) !== true) throw new Error(response?.message && response.message !== '操作成功' ? response.message : fallback)
}

export const useReminderStore = defineStore('reminder', {
  state: () => ({
    preferences: [],
    preferencesUserId: null,
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
      const userId = useUserStore().userInfo?.id
      if (this.preferencesUserId !== userId) this.preferences = []
      this.preferencesUserId = userId || null
      try {
        if (!userId) throw new Error('用户信息未就绪，请稍后重试')
        const response = await request.get('/reminder/preferences', { params: { userId } })
        const preferences = responseData(response, '加载提醒失败，请稍后重试')
        if (!Array.isArray(preferences) || preferences.some(item => !item || typeof item !== 'object' || item.id == null)) {
          throw new Error('提醒列表返回异常，请稍后重试')
        }
        if (useUserStore().userInfo?.id !== userId) return false
        this.preferences = preferences
        return true
      } catch (error) {
        if (useUserStore().userInfo?.id === userId) this.error = error.message || '加载提醒失败，请检查网络连接'
        return false
      } finally { this.loading = false }
    },

    async savePreference(preference) {
      this.loading = true
      this.error = null
      const userId = useUserStore().userInfo?.id
      try {
        if (!userId) throw new Error('用户信息未就绪，请稍后重试')
        const payload = {
          userId, type: preference.type, time: preference.time, frequency: preference.frequency,
          smartMode: !!preference.smartMode, enabled: preference.enabled !== false
        }
        const response = preference.id != null
          ? await request.put(`/reminder/preferences/${preference.id}`, payload)
          : await request.post('/reminder/preferences', payload)
        mutationSucceeded(response, '保存提醒失败，请稍后重试')
        if (useUserStore().userInfo?.id !== userId) return false
        // 保存接口只返回 Boolean。重新查询服务端，取得真正的提醒 ID 和完整列表。
        if (!await this.fetchPreferences()) this.error = '提醒已保存，但列表刷新失败，请点击刷新列表重试'
        return true
      } catch (error) {
        if (useUserStore().userInfo?.id === userId) this.error = error.message || '保存提醒失败，请检查网络连接'
        return false
      } finally { this.loading = false }
    },

    async deletePreference(id) {
      if (id == null || id === '' || id === 'undefined') return false
      this.loading = true
      this.error = null
      const userId = useUserStore().userInfo?.id
      try {
        if (!userId) throw new Error('用户信息未就绪，请稍后重试')
        const response = await request.delete(`/reminder/preferences/${id}`)
        mutationSucceeded(response, '删除提醒失败，请稍后重试')
        if (useUserStore().userInfo?.id !== userId) return false
        this.preferences = this.preferences.filter(item => String(item.id) !== String(id))
        return true
      } catch (error) {
        if (useUserStore().userInfo?.id === userId) this.error = error.message || '删除提醒失败，请检查网络连接'
        return false
      } finally { this.loading = false }
    },

    async toggleReminder(id, enabled) {
      const preference = this.preferences.find(p => p.id === id)
      if (!preference) return false

      this.loading = true
      this.error = null
      try {
        const response = await request.put(`/reminder/preferences/${id}/toggle`, enabled)

        if ((response.code === 200 || response.code === 0) && response.data === true) {
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
        const userId = userStore.userInfo?.id
        if (!userId) return []
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
