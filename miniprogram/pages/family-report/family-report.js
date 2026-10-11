const { get } = require('../../utils/request')
const runtime = require('../../config/runtime')
const riskLabels = { BMI: 'BMI风险', BLOOD_PRESSURE: '血压风险', DIABETES: '血糖风险', CARDIO: '心血管风险' }
const levels = { HIGH: '高', MEDIUM: '中', LOW: '低' }

Page({
  data: { memberName: '家人', overview: null, risks: [], insights: [], loading: false, error: '' },
  onLoad(options) { this._bindingId = /^\d+$/.test(options.id || '') ? options.id : null },
  onShow() {
    if (!getApp().checkLogin()) return
    this._visible = true
    this.load()
  },
  onHide() {
    this._visible = false
    this._sequence = (this._sequence || 0) + 1
    this.setData({ overview: null, risks: [], insights: [] })
  },
  onUnload() { this.onHide() },
  async load() {
    const sequence = this._sequence = (this._sequence || 0) + 1
    this.setData({ loading: true, error: '', overview: null, risks: [], insights: [] })
    try {
      if (!this._bindingId) throw new Error('家庭关系无效，请返回家庭成员页面')
      const members = await get('/api/family/members', {}, { showLoading: false, showError: false })
      if (sequence !== this._sequence || !this._visible) return
      const member = members.find(m => String(m.id) === this._bindingId && m.status === 'ACTIVE')
      if (!member) throw new Error('请先获得家人同意并完成绑定')
      this.setData({ memberName: member.memberName })
      const overview = await get(`/api/family/members/${this._bindingId}/report`, {}, {
        showLoading: false, showError: false, timeout: runtime.smartHealthTimeoutMs
      })
      if (sequence !== this._sequence || !this._visible) return
      this.setData({ overview,
        risks: (overview.riskAssessments || []).map(r => ({ ...r,
          label: riskLabels[r.assessmentType] || r.assessmentType,
          levelLabel: levels[r.riskLevel] || r.riskLevel })),
        insights: [{ label: '睡眠分析', value: overview.sleepInsight }, { label: '压力分析', value: overview.stressInsight }].filter(i => i.value)
      })
    } catch (e) { if (sequence === this._sequence && this._visible) this.setData({ error: e.message || '报告加载失败，请重试' }) }
    finally { if (sequence === this._sequence && this._visible) this.setData({ loading: false }) }
  }
})
