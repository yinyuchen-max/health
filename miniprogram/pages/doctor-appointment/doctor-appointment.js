const { post } = require('../../utils/request')

Page({
  data: {
    doctorId: null,
    doctorName: '',
    department: '',
    submitting: false,
    timeIndex: 0,
    timeOptions: ['08:00-09:00', '09:00-10:00', '10:00-11:00', '14:00-15:00', '15:00-16:00', '16:00-17:00'],
    form: {
      patientName: '',
      age: '',
      phone: '',
      date: '',
      time: '08:00-09:00'
    }
  },

  onLoad(options) {
    this.setData({
      doctorId: options.doctorId ? Number(options.doctorId) : null,
      doctorName: decodeURIComponent(options.doctorName || ''),
      department: decodeURIComponent(options.department || '')
    })
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
  },

  onDateChange(e) {
    this.setData({ 'form.date': e.detail.value })
  },

  onTimeChange(e) {
    const idx = e.detail.value
    this.setData({
      timeIndex: idx,
      'form.time': this.data.timeOptions[idx]
    })
  },

  async submitAppointment() {
    const { form, doctorId, doctorName, department } = this.data

    if (!form.patientName) {
      wx.showToast({ title: '请输入患者姓名', icon: 'none' })
      return
    }
    if (!form.phone) {
      wx.showToast({ title: '请输入联系电话', icon: 'none' })
      return
    }
    if (!form.date) {
      wx.showToast({ title: '请选择预约日期', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      // 组合预约时间
      const appointmentTime = `${form.date} ${form.time.split('-')[0]}:00:00`

      const data = {
        doctorId,
        patientName: form.patientName,
        age: form.age ? Number(form.age) : null,
        phone: form.phone,
        department,
        appointmentTime
      }

      await post('/api/doctor/appointment', data)
      wx.showToast({ title: '预约成功', icon: 'success' })

      setTimeout(() => {
        wx.navigateBack()
      }, 1500)
    } catch (err) {
      console.error('预约失败:', err)
    } finally {
      this.setData({ submitting: false })
    }
  }
})
