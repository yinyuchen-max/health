const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

function loadPage(request) {
  let page
  const source = fs.readFileSync(path.join(__dirname, '../pages/health-record/health-record.js'), 'utf8')
  vm.runInNewContext(source, {
    require: () => request,
    getApp: () => ({ globalData: { userInfo: { id: 42 } } }),
    Page: value => { page = value },
    wx: { showToast() {} }, console: { error() {} }
  })
  page.setData = values => Object.assign(page.data, values)
  page.data.records = [{ id: 7, version: 0, recordDate: '2026-10-03', notes: 'original' }]
  page.editRecord({ currentTarget: { dataset: { id: 7 } } })
  page.data.form.notes = 'my draft'
  return page
}

test('mini program edits carry the record version including zero', async () => {
  const calls = []
  const page = loadPage({
    put: async (url, data) => calls.push({ url, data: { ...data } }),
    get: async () => ({ records: [] })
  })
  await page.submitForm()
  assert.equal(calls[0].url, '/api/health/record/7')
  assert.equal(calls[0].data.version, 0)
  assert.equal(calls[0].data.notes, 'my draft')
  assert.equal(page.data.showForm, false)
})

test('mini program conflicts preserve draft and old version while refreshing the list', async () => {
  let writes = 0
  const page = loadPage({
    put: async () => { writes++; throw { status: 409, message: 'record conflict' } },
    get: async () => ({ records: [{ id: 7, version: 1, notes: 'other edit' }] })
  })
  await page.submitForm()
  assert.equal(writes, 1)
  assert.equal(page.data.showForm, true)
  assert.equal(page.data.editingId, 7)
  assert.equal(page.data.editingVersion, 0)
  assert.equal(page.data.form.notes, 'my draft')
  assert.equal(page.data.records[0].version, 1)
})
