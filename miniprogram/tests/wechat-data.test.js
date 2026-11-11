const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const path = require('node:path')

function harness({ authorized = true, bound = true, failDecrypt = false } = {}) {
  const calls = []
  const cached = { todaySteps: null, history: [], lastSyncAt: null }
  const synced = { todaySteps: 0, history: [{ steps: 0 }], lastSyncAt: '2026-10-24T10:00:00' }
  let uploads = 0
  const app = { globalData: { userInfo: { wechatBound: bound }, baseUrl: 'https://api.test.invalid' } }
  const requests = {
    get: async url => { calls.push(url); return uploads ? synced : cached },
    post: async (url, data) => {
      calls.push(url)
      if (url.endsWith('decrypt-run-data')) { if (failDecrypt) throw new Error('decode failed'); uploads++ }
    }
  }
  const sandbox = { module: { exports: {} }, require: () => requests, getApp: () => app, Promise,
    wx: {
      getSetting: ({ success }) => success({ authSetting: { 'scope.werun': authorized } }),
      authorize: ({ success }) => { calls.push('wx.authorize'); success({}) },
      login: ({ success }) => { calls.push('wx.login'); success({ code: 'fresh-code' }) },
      getWeRunData: ({ success }) => { calls.push('wx.getWeRunData'); success({ encryptedData: 'encrypted', iv: 'iv' }) }
    }
  }
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../utils/wechat-data.js'), 'utf8'), sandbox)
  return { ...sandbox.module.exports, calls, cached, synced }
}
test('authorized sync refreshes WeChat session before reading encrypted steps and preserves zero', async () => {
  const app = harness()
  const result = await app.syncWeRun()
  assert.equal(result.todaySteps, 0)
  assert.ok(app.calls.indexOf('/api/user/wechat/session') < app.calls.indexOf('wx.getWeRunData'))
  assert.ok(app.calls.indexOf('wx.getWeRunData') < app.calls.indexOf('/api/user/wechat/decrypt-run-data'))
})
test('passive visits never prompt for missing permission and keep server history', async () => {
  const app = harness({ authorized: false })
  assert.equal(await app.syncWeRun(), app.cached)
  assert.equal(app.calls.includes('wx.login'), false)
})
test('password accounts do not acquire or attach WeChat data', async () => {
  const app = harness({ bound: false })
  assert.equal(await app.syncWeRun(), app.cached)
  await assert.rejects(app.syncWeRun({ requestPermission: true }), /微信登录/)
  assert.equal(app.calls.includes('wx.login'), false)
})
test('passive failure keeps prior readings and explicit retry reports the error', async () => {
  const app = harness({ failDecrypt: true })
  assert.equal(await app.syncWeRun(), app.cached)
  await assert.rejects(app.syncWeRun({ requestPermission: true }), /decode failed/)
})
test('concurrent pages share one WeChat synchronization', async () => {
  const app = harness()
  const first = app.syncWeRun(), second = app.syncWeRun()
  assert.equal(first, second)
  await Promise.all([first, second])
  assert.equal(app.calls.filter(value => value === 'wx.login').length, 1)
})
test('explicit sync during a passive visit still requests missing authorization', async () => {
  const app = harness({ authorized: null })
  const passive = app.syncWeRun()
  const manual = app.syncWeRun({ requestPermission: true })
  await passive
  assert.equal((await manual).todaySteps, 0)
  assert.ok(app.calls.includes('wx.authorize'))
})
