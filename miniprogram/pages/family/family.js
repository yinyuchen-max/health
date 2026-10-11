const { get, post, put, del } = require('../../utils/request')
const types = [
  { value: 'bloodPressure', label: '量血压' }, { value: 'exercise', label: '运动' },
  { value: 'bloodSugar', label: '测血糖' }, { value: 'weight', label: '记体重' }
]
const confirm = (title, content) => new Promise(resolve => wx.showModal({
  title, content, success: res => resolve(res.confirm), fail: () => resolve(false)
}))

Page({
  data: { phone: '', relationship: '家人', active: [], pending: [], reminders: [], unread: 0,
    loading: false, busy: false, error: '', types },

  onShow() {
    if (!getApp().checkLogin()) return
    this._visible = true
    this.load()
  },
  onHide() { this._visible = false; this._sequence = (this._sequence || 0) + 1 },
  onUnload() { this.onHide() },
  async onPullDownRefresh() { try { await this.load() } finally { wx.stopPullDownRefresh() } },

  async load() {
    const sequence = this._sequence = (this._sequence || 0) + 1
    this.setData({ loading: true, error: '' })
    try {
      const [members, reminders] = await Promise.all([
        get('/api/family/members', {}, { showLoading: false, showError: false }),
        get('/api/family/reminders', {}, { showLoading: false, showError: false })
      ])
      if (sequence !== this._sequence || !this._visible) return
      this.setData({
        active: members.filter(m => m.status === 'ACTIVE'),
        pending: members.filter(m => m.status === 'PENDING'),
        reminders: reminders.map(r => ({ ...r,
          label: types.find(t => t.value === r.type)?.label || r.type,
          time: String(r.createdAt || '').replace('T', ' ').slice(0, 19) })),
        unread: reminders.filter(r => !r.readStatus).length
      })
    } catch (e) {
      if (sequence === this._sequence && this._visible)
        this.setData({ active: [], pending: [], reminders: [], unread: 0, error: e.message || '加载失败，请重试' })
    } finally { if (sequence === this._sequence && this._visible) this.setData({ loading: false }) }
  },

  input(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }) },
  goProfile() { wx.navigateTo({ url: '/pages/user-profile/user-profile' }) },

  async mutate(action, message) {
    if (this.data.busy) return
    this.setData({ busy: true })
    try {
      await action()
      wx.showToast({ title: message, icon: 'success' })
      if (this._visible) await this.load()
    } catch (e) { wx.showToast({ title: e.message || '操作失败', icon: 'none' }) }
    finally { this.setData({ busy: false }) }
  },

  async invite() {
    const phone = this.data.phone.trim(), relationship = this.data.relationship.trim()
    if (!/^1[3-9]\d{9}$/.test(phone)) return wx.showToast({ title: '请输入有效的11位手机号', icon: 'none' })
    if (!relationship) return wx.showToast({ title: '请填写对家人的称呼', icon: 'none' })
    await this.mutate(() => post('/api/family/invitations', { phone, relationship }, { showError: false }), '邀请已发送')
  },

  async decide(e) {
    const { id, accept } = e.currentTarget.dataset
    const member = this.data.pending.find(m => m.id === id)
    if (!member) return
    const agree = accept === true || accept === 'true'
    if (agree && !await confirm('确认健康报告共享', `确认认识“${member.memberName}”（${member.maskedPhone}）吗？同意后双方可以查看健康报告、发送提醒。`)) return
    await this.mutate(() => put(`/api/family/invitations/${id}`, { accept: agree }, { showError: false }), agree ? '已绑定家人' : '已拒绝邀请')
  },

  async remove(e) {
    const { id } = e.currentTarget.dataset
    const member = [...this.data.active, ...this.data.pending].find(m => m.id === id)
    if (!member) return
    if (!await confirm('确认操作', member.status === 'ACTIVE' ? '解除后双方将无法继续查看健康报告或发送新提醒。' : '确定撤回这份邀请吗？')) return
    await this.mutate(() => del(`/api/family/members/${id}`, {}, { showError: false }), '操作成功')
  },

  report(e) {
    wx.navigateTo({ url: `/pages/family-report/family-report?id=${e.currentTarget.dataset.id}` })
  },

  async remind(e) {
    const { id, type } = e.currentTarget.dataset
    const member = this.data.active.find(m => m.id === id)
    const reminderType = types.find(t => t.value === type)
    if (!member || !reminderType) return
    if (!await confirm('发送关怀提醒', `提醒“${member.memberName}”${reminderType.label}？对方可在家庭成员页面查看，同类提醒30分钟内只能发送一次。`)) return
    await this.mutate(() => post(`/api/family/members/${id}/reminders`, { type }, { showError: false }), '提醒已发送')
  },

  async acknowledge(e) {
    const { id, action } = e.currentTarget.dataset
    if (!['read', 'complete'].includes(action)) return
    await this.mutate(() => put(`/api/family/reminders/${id}/${action}`, {}, { showError: false }), action === 'complete' ? '已标记完成' : '已标记已读')
  }
})
