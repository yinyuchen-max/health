const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const path = require('node:path')

function harness() {
  const store = new Map()
  const calls = []
  let app
  const sandbox = {
    require: file => file.includes('environment') ? { resolveBaseUrl: () => 'https://api.test.invalid' } : { requestTimeoutMs: 15000 },
    App: value => { app = value }, Date, Promise,
    wx: {
      getStorageSync: key => store.get(key), setStorageSync: (key,value) => store.set(key,value), removeStorageSync: key => store.delete(key),
      request: options => { calls.push(options) }, redirectTo() {}
    }
  }
  vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../app.js'),'utf8'), sandbox)
  return { app, store, calls }
}
const tokens = { token: 'short.access.jwt', refreshToken: 'refresh-test-only', expiresIn: 900, refreshExpiresAt: Date.now()+604800000 }

test('mini program coalesces refresh and persists rotated credential', async () => {
  const { app, store, calls } = harness()
  app.setLogin(tokens, { username: 'alice' })
  app.globalData.expiresAt = 0
  const a = app.ensureAccess()
  const b = app.ensureAccess()
  assert.equal(calls.length,1)
  assert.equal(calls[0].header.Authorization,undefined)
  assert.equal(calls[0].url,'https://api.test.invalid/api/user/native-refresh')
  calls[0].success({ statusCode:200, data:{ code:200,data:{...tokens,token:'new.access.jwt',refreshToken:'new-refresh'} } })
  assert.deepEqual(await Promise.all([a,b]), ['new.access.jwt','new.access.jwt'])
  assert.equal(store.get('authSession').refreshToken,'new-refresh')
  assert.equal(store.has('token'),false)
})
test('mini program keeps state on 503 and clears it on invalid refresh', async () => {
  const { app, store, calls } = harness()
  app.setLogin(tokens,{})
  const unavailable = app.ensureAccess(true)
  calls[0].success({ statusCode:503 })
  await assert.rejects(unavailable)
  assert.equal(app.globalData.token,tokens.token)
  const invalid = app.ensureAccess(true)
  calls[1].success({ statusCode:401 })
  assert.equal(await invalid,'')
  assert.equal(store.has('authSession'),false)
})
test('clearing login prevents late refresh from restoring credentials', async () => {
  const { app, calls } = harness()
  app.setLogin(tokens,{})
  const pending = app.ensureAccess(true)
  app.clearLogin()
  calls[0].success({ statusCode:200,data:{code:200,data:tokens} })
  assert.equal(await pending,'')
  assert.equal(app.globalData.token,'')
})
test('legacy standalone JWT is removed at launch', () => {
  const { app, store } = harness()
  store.set('token','old.jwt.token')
  app.onLaunch()
  assert.equal(store.has('token'),false)
  assert.equal(app.globalData.token,'')
})
