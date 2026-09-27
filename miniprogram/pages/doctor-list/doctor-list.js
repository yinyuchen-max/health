const { get } = require('../../utils/request')

Page({
  data: {
    doctors: [],
    deptList: [],
    currentDept: '',
    loading: false
  },

  onShow() {
    this.loadDoctors()
  },

  async loadDoctors() {
    this.setData({ loading: true })
    try {
      const { currentDept } = this.data
      let url = '/api/doctor/list'
      if (currentDept) url += `?department=${encodeURIComponent(currentDept)}`

      const doctors = await get(url, {}, { showLoading: false })
      const doctorList = Array.isArray(doctors) ? doctors : []

      // 提取科室列表
      const deptSet = new Set(doctorList.map(d => d.department).filter(Boolean))
      const deptList = [...deptSet]

      this.setData({ doctors: doctorList, deptList, loading: false })
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
