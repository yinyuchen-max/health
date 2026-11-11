import { defineStore } from 'pinia'
import request from '../utils/request'

function recordsFromResponse(response, label) {
  if (response?.code !== 200 && response?.code !== 0) {
    throw new Error(response?.message || `${label}加载失败，请稍后重试`)
  }
  if (!Array.isArray(response.data?.records)) throw new Error(`${label}加载失败，请稍后重试`)
  return response.data.records
}

export const useAnalyticsStore = defineStore('analytics', {
  state: () => ({
    healthRecords: [],
    sportRecords: [],
    healthLoadedUserId: null,
    sportLoadedUserId: null
  }),

  actions: {
    async fetchHealthRecords(userId, params = {}) {
      const response = await request.get(`/health/records/${userId}`, { params })
      this.setHealthRecords(recordsFromResponse(response, '健康记录'), userId)
    },

    async fetchSportRecords(userId, params = {}) {
      const response = await request.get(`/sport/records/${userId}`, { params })
      this.setSportRecords(recordsFromResponse(response, '运动记录'), userId)
    },

    setHealthRecords(records, userId = null) {
      this.healthRecords = Array.isArray(records) ? records : []
      this.healthLoadedUserId = userId
    },

    setSportRecords(records, userId = null) {
      this.sportRecords = Array.isArray(records) ? records : []
      this.sportLoadedUserId = userId
    },

    upsertHealthRecord(record) {
      const index = this.healthRecords.findIndex(item => item.id === record.id)
      if (index === -1) {
        this.healthRecords.unshift(record)
      } else {
        this.healthRecords[index] = record
      }
    },

    removeHealthRecord(id) {
      this.healthRecords = this.healthRecords.filter(item => item.id !== id)
    },

    upsertSportRecord(record) {
      const index = this.sportRecords.findIndex(item => item.id === record.id)
      if (index === -1) {
        this.sportRecords.unshift(record)
      } else {
        this.sportRecords[index] = record
      }
    },

    removeSportRecord(id) {
      this.sportRecords = this.sportRecords.filter(item => item.id !== id)
    }
  }
})
