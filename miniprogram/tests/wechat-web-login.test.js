const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const path = require('node:path')
function harness({ syncFails = false } = {}) {
  const calls = []
  let page
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../pages/web-login/web-login.js'), 'utf8'), {
    Page: value => { page = value },
    require: file => file.endsWith('/auth') ? { wxLogin: async () => { calls.push('login') } }
      : file.endsWith('/request') ? { post: async url => { calls.push(url) } }
        : { syncWeRun: async () => { calls.push('sync'); if (syncFails) throw new Error('未授权') }, saveWechatProfile: async () => { calls.push('profile') } },
    wx: { switchTab() {} },
  })
  page.setData = value => Object.assign(page.data, value)
  return { page, calls }
}
test('scanning only prepares the page and cannot authorize a browser without a click', () => {
  const { page, calls } = harness()
  page.onLoad({ scene: 'a'.repeat(32) })
  assert.equal(page.data.valid, true)
  assert.equal(calls.length, 0)
})
test('confirmation logs in before saving profile, syncing and authorizing the browser', async () => {
  const { page, calls } = harness()
  page.onLoad({ scene: 'a'.repeat(32) })
  page.setData({ nickname: '用户选择的昵称' })
  await page.confirm()
  assert.deepEqual(calls, ['login', 'profile', 'sync', '/api/user/wechat/web-confirm'])
  assert.equal(page.data.done, true)
})
test('refusing exercise permission does not block browser login', async () => {
  const { page, calls } = harness({ syncFails: true })
  page.onLoad({ scene: 'a'.repeat(32) })
  await page.confirm()
  assert.equal(page.data.done, true)
  assert.match(page.data.message, /未授权/)
  assert.ok(calls.includes('/api/user/wechat/web-confirm'))
})
test('cancelled and invalid scans cannot authorize the browser', async () => {
  const { page, calls } = harness()
  page.onLoad({ scene: 'bad-ticket' })
  await page.confirm()
  page.onLoad({ scene: 'a'.repeat(32) })
  page.cancel()
  await page.confirm()
  assert.equal(calls.length, 0)
})
