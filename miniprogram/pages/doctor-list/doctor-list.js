const { get } = require('../../utils/request')

Page({
  data: {
    doctors: [],
    deptList: [],
    currentDept: '',
    loading: false
  },

  onShow() {
    this.loadDepartments()
    this.loadDoctors()
  },

  async loadDepartments() {
    try {
      const departments = await get('/api/doctor/departments', {}, { showLoading: false })
      this.setData({ deptList: Array.isArray(departments) ? departments : [] })
    } catch (err) {
      console.error('加载科室列表失败:', err)
    }
  },

  async loadDoctors() {
    this.setData({ loading: true })
    try {
      const { currentDept } = this.data
      let url = '/api/doctor/list'
      if (currentDept) url += `?department=${encodeURIComponent(currentDept)}`

      const doctors = await get(url, {}, { showLoading: false })
      const doctorList = Array.isArray(doctors) ? doctors : []

      this.setData({ doctors: doctorList, loading: false })
    } catch (err) {
      console.error('加载医生列表失败:', err)
      this.setData({ loading: false })
    }
  },

  filterDept(e) {
    const dept = e.currentTarget.dataset.dept
    this.setData({ currentDept: dept })
    this.loadDoctors()
  },

  goAppointment(e) {
    const doctor = e.currentTarget.dataset.doctor
    wx.navigateTo({
      url: `/pages/doctor-appointment/doctor-appointment?doctorId=${doctor.id}&doctorName=${encodeURIComponent(doctor.realName)}&department=${encodeURIComponent(doctor.department)}`
    })
  }
})
