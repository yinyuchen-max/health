const { get, post, put, del } = require('../../utils/request')

Page({
  data: {
    records: [],
    latestRecord: null,
    showForm: false,
    editingId: null,
    saving: false,
    form: {
      recordDate: '',
      bloodPressureSystolic: '',
      bloodPressureDiastolic: '',
      heartRate: '',
      bloodSugar: '',
      weight: '',
      notes: ''
    }
  },

  onShow() {
    this.loadRecords()
  },

  async loadRecords() {
    try {
      const app = getApp()
      const userId = app.globalData.userInfo?.id
      if (!userId) return
      const res = await get(`/api/health/records/${userId}?pageNum=1&pageSize=100`)
      const records = res.records || []
      this.setData({
        records,
        latestRecord: records.length > 0 ? records[0] : null
      })
    } catch (err) {
      console.error('加载健康记录失败:', err)
    }
  },

  openForm() {
    this.setData({
      showForm: true,
      editingId: null,
      form: {
        recordDate: new Date().toISOString().slice(0, 10),
        bloodPressureSystolic: '',
        bloodPressureDiastolic: '',
        heartRate: '',
        bloodSugar: '',
        weight: '',
        notes: ''
      }
    })
  },

  closeForm() {
    this.setData({ showForm: false, editingId: null })
  },

  editRecord(e) {
    const id = e.currentTarget.dataset.id
    const record = this.data.records.find(r => r.id === id)
    if (!record) return

    this.setData({
      showForm: true,
      editingId: id,
      form: {
        recordDate: record.recordDate || '',
        bloodPressureSystolic: record.bloodPressureSystolic || '',
        bloodPressureDiastolic: record.bloodPressureDiastolic || '',
        heartRate: record.heartRate || '',
        bloodSugar: record.bloodSugar || '',
        weight: record.weight || '',
        notes: record.notes || ''
      }
    })
  },

  async deleteRecord(e) {
    const id = e.currentTarget.dataset.id
    const res = await new Promise(resolve => {
      wx.showModal({
        title: '确认删除',
        content: '确定要删除这条记录吗？',
        success: resolve
      })
    })

    if (!res.confirm) return

    try {
      await del(`/api/health/record/${id}`)
      wx.showToast({ title: '删除成功', icon: 'success' })
      this.loadRecords()
    } catch (err) {
      console.error('删除失败:', err)
    }
  },

  onDateChange(e) {
    this.setData({ 'form.recordDate': e.detail.value })
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
  },

  async submitForm() {
    const { form, editingId } = this.data
    if (!form.recordDate) {
      wx.showToast({ title: '请选择日期', icon: 'none' })
      return
    }

    this.setData({ saving: true })
    try {
      // 转换数字字段
      const data = {
        recordDate: form.recordDate,
        bloodPressureSystolic: form.bloodPressureSystolic ? Number(form.bloodPressureSystolic) : null,
        bloodPressureDiastolic: form.bloodPressureDiastolic ? Number(form.bloodPressureDiastolic) : null,
        heartRate: form.heartRate ? Number(form.heartRate) : null,
        bloodSugar: form.bloodSugar ? Number(form.bloodSugar) : null,
        weight: form.weight ? Number(form.weight) : null,
        notes: form.notes
      }

      console.log('提交数据:', data)

      if (editingId) {
        await put(`/api/health/record/${editingId}`, data)
        wx.showToast({ title: '更新成功', icon: 'success' })
      } else {
        await post('/api/health/record', data)
        wx.showToast({ title: '添加成功', icon: 'success' })
      }

      this.closeForm()
      this.loadRecords()
    } catch (err) {
      console.error('保存失败:', err)
      wx.showToast({ title: err.message || '保存失败', icon: 'none' })
    } finally {
      this.setData({ saving: false })
    }
  }
})
