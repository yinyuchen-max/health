const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

function load(name, request, agree = true) {
  let page
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, `../pages/${name}/${name}.js`), 'utf8'), {
    require: id => id.includes('runtime') ? { smartHealthTimeoutMs: 90000 } : request,
    Page: value => { page = value }, getApp: () => ({ checkLogin: () => true }),
    wx: { showToast() {}, showModal: options => options.success({ confirm: agree }), navigateTo() {}, stopPullDownRefresh() {} }
  })
  page.setData = values => Object.assign(page.data, values)
  page._visible = true
  return page
}
function deferred() { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }

test('mini program invitations require valid phone and do not send an owner id', async () => {
  const writes = []
  const p = load('family', { post: async (url, data) => writes.push({ url, data }), get: async () => [] })
  p.data.phone = 'abc'; await p.invite(); assert.equal(writes.length, 0)
  p.data.phone = '13900000002'; await p.invite()
  assert.deepEqual({ ...writes[0].data }, { phone: '13900000002', relationship: '家人' })
})

test('mini program approval cancellation does not grant sharing', async () => {
  let writes = 0
  const p = load('family', { put: async () => { writes++ } }, false)
  p.data.pending = [{ id: 7, memberName: '爸爸', maskedPhone: '139****0002' }]
  await p.decide({ currentTarget: { dataset: { id: 7, accept: true } } }); assert.equal(writes, 0)
})

test('mini program family report clears sensitive data when hidden and ignores late responses', async () => {
  const pending = deferred()
  const p = load('family-report', { get: async url => url.endsWith('/members') ? [{ id: 7, status: 'ACTIVE', memberName: '爸爸' }] : pending.promise })
  p.onLoad({ id: '7' }); const result = p.load()
  await Promise.resolve(); await Promise.resolve()
  p.onHide(); pending.resolve({ userId: 2, riskAssessments: [] }); await result
  assert.equal(p.data.overview, null); assert.equal(p.data.risks.length, 0)
})

test('mini program refuses pending or removed family report links before loading a report', async () => {
  const urls = []
  const p = load('family-report', { get: async url => { urls.push(url); return [{ id: 7, status: 'PENDING' }] } })
  p.onLoad({ id: '7' }); await p.load()
  assert.equal(urls.length, 1); assert.equal(p.data.overview, null); assert.match(p.data.error, /同意/)
})

test('mini program renders actual report field names and supports no-data state', async () => {
  const p = load('family-report', { get: async url => url.endsWith('/members') ? [{ id: 7, status: 'ACTIVE', memberName: '爸爸' }] : {
    dataStatus: 'AVAILABLE', riskAssessments: [{ assessmentType: 'BLOOD_PRESSURE', riskLevel: 'HIGH', summary: 'summary' }],
    exercisePlan: { goal: 'goal', weeklyPlan: ['plan'] }
  } })
  p.onLoad({ id: '7' }); await p.load()
  assert.equal(p.data.risks[0].label, '血压风险'); assert.equal(p.data.risks[0].levelLabel, '高')
  assert.equal(p.data.overview.exercisePlan.goal, 'goal')
})

test('mini program list load errors remove stale relationships and reminders', async () => {
  const p = load('family', { get: async () => { throw { message: '加载失败' } } })
  p.data.active = [{ id: 7 }]; p.data.reminders = [{ id: 1 }]
  await p.load(); assert.equal(p.data.active.length, 0); assert.equal(p.data.reminders.length, 0); assert.equal(p.data.error, '加载失败')
})
