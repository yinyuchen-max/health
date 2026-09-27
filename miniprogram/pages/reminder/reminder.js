const { get, post, put, del } = require('../../utils/request')

Page({
  data: {
    reminders: [],
    showForm: false,
    editingId: null,
    saving: false,
    typeIndex: 0,
    typeOptions: [
      { value: 'bloodPressure', label: '血压测量提醒' },
      { value: 'bloodSugar', label: '血糖检测提醒' },
      { value: 'weight', label: '体重记录提醒' },
      { value: 'exercise', label: '运动提醒' }
    ],
    typeLabels: {
      bloodPressure: '血压测量提醒',
      bloodSugar: '血糖检测提醒',
      weight: '体重记录提醒',
      exercise: '运动提醒'
    },
    form: {
      type: 'bloodPressure',
      time: '08:00',
      notes: '',
      enabled: true
    }
  },

  onShow() {
    this.loadReminders()
  },

  async loadReminders() {
    try {
      const app = getApp()
      const userId = app.globalData.userInfo?.id
      if (!userId) return
      const reminders = await get(`/api/reminder/preferences?userId=${userId}`)
      this.setData({ reminders: Array.isArray(reminders) ? reminders : [] })
    } catch (err) {
      console.error('加载提醒失败:', err)
    }
  },

  openForm() {
    this.setData({
      showForm: true,
      editingId: null,
      typeIndex: 0,
      form: {
        type: 'bloodPressure',
        time: '08:00',
        notes: '',
        enabled: true
      }
    })
  },

  closeForm() {
    this.setData({ showForm: false, editingId: null })
  },

  editReminder(e) {
    const id = e.currentTarget.dataset.id
    const reminder = this.data.reminders.find(r => r.id === id)
    if (!reminder) return

    const typeIndex = this.data.typeOptions.findIndex(t => t.value === reminder.type)
    this.setData({
      showForm: true,
      editingId: id,
      typeIndex: typeIndex >= 0 ? typeIndex : 0,
      form: {
        type: reminder.type,
        time: reminder.time || '08:00',
        notes: reminder.notes || '',
        enabled: reminder.enabled !== false
      }
    })
  },

  async deleteReminder(e) {
    const id = e.currentTarget.dataset.id
    const res = await new Promise(resolve => {
      wx.showModal({
        title: '确认删除',
        content: '确定要删除这条提醒吗？',
        success: resolve
      })
    })
    if (!res.confirm) return

    try {
      await del(`/api/reminder/preferences/${id}`)
      wx.showToast({ title: '删除成功', icon: 'success' })
      this.loadReminders()
    } catch (err) {
      console.error('删除失败:', err)
    }
  },

  async toggleReminder(e) {
    const id = e.currentTarget.dataset.id
    const enabled = e.detail.value
    try {
      await put(`/api/reminder/preferences/${id}/toggle`, enabled)
      this.loadReminders()
    } catch (err) {
      console.error('切换失败:', err)
    }
  },

  onTypeChange(e) {
    const idx = e.detail.value
    this.setData({
      typeIndex: idx,
      'form.type': this.data.typeOptions[idx].value
    })
  },

  onTimeChange(e) {
    this.setData({ 'form.time': e.detail.value })
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
  },

  onEnabledChange(e) {
    this.setData({ 'form.enabled': e.detail.value })
  },

  async submitForm() {
    const { form, editingId } = this.data
    if (!form.time) {
      wx.showToast({ title: '请选择时间', icon: 'none' })
      return
    }

    this.setData({ saving: true })
    try {
      const data = {
        type: form.type,
        time: form.time,
        notes: form.notes,
        enabled: form.enabled
      }

      if (editingId) {
        await put(`/api/reminder/preferences/${editingId}`, data)
        wx.showToast({ title: '更新成功', icon: 'success' })
      } else {
        await post('/api/reminder/preferences', data)
        wx.showToast({ title: '添加成功', icon: 'success' })
      }

      this.closeForm()
      this.loadReminders()
    } catch (err) {
      console.error('保存失败:', err)
    } finally {
      this.setData({ saving: false })
    }
  }
})
