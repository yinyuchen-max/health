const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const path = require('node:path')

test('request wrapper applies configured default, per-request override, and stream timeout', async () => {
  const calls = []
  const policy = { requestTimeoutMs: 3210, smartHealthTimeoutMs: 6540, streamTimeoutMs: 9870 }
  const source = fs.readFileSync(path.join(__dirname, '../utils/request.js'), 'utf8')
  const sandbox = {
    module: { exports: {} },
    require: () => policy,
    getApp: () => ({ globalData: { baseUrl: 'https://test.invalid', token: 'test-token' } }),
    wx: {
      getStorageSync: () => '',
      request: options => {
        calls.push(options)
        if (!options.enableChunked) options.success({ statusCode: 200, data: { code: 200, data: [] } })
        return { onChunkReceived() {} }
      }
    },
    console
  }
  vm.runInNewContext(source, sandbox)
  const { get, streamRequest } = sandbox.module.exports
  await get('/api/doctor/departments', {}, { showLoading: false })
  await get('/api/smart-health/overview', {}, { showLoading: false, timeout: policy.smartHealthTimeoutMs })
  streamRequest({ url: '/api/chat/stream' })
  assert.deepEqual(calls.map(call => call.timeout), [3210, 6540, 9870])
})

test('invalid public runtime configuration is rejected before requests are sent', () => {
  const source = fs.readFileSync(path.join(__dirname, '../config/runtime.js'), 'utf8')
  for (const invalid of [0, -1, '15000', 1.5]) {
    assert.throws(() => vm.runInNewContext(source, {
      module: { exports: {} }, require: () => ({ requestTimeoutMs: invalid, smartHealthTimeoutMs: 10, streamTimeoutMs: 20 })
    }), /必须为正数毫秒/)
  }
})

test('application boots using only JavaScript modules supported by the mini program loader', () => {
  const root = path.resolve(__dirname, '..')
  const modules = new Map()
  let app
  const sandbox = {
    App: value => { app = value },
    wx: {
      getAccountInfoSync: () => ({ miniProgram: { envVersion: 'develop' } }),
      getDeviceInfo: () => ({ platform: 'devtools' })
    }
  }
  function loadModule(filename) {
    if (modules.has(filename)) return modules.get(filename).exports
    const module = { exports: {} }
    modules.set(filename, module)
    const source = fs.readFileSync(filename, 'utf8')
    const execute = vm.runInNewContext(`(function(require, module, exports) {\n${source}\n})`, sandbox)
    execute(request => {
      assert.equal(request.startsWith('.'), true, 'startup modules must be local')
      assert.equal(path.extname(request) === '' || path.extname(request) === '.js', true, `unsupported module: ${request}`)
      return loadModule(path.resolve(path.dirname(filename), request + (path.extname(request) ? '' : '.js')))
    }, module, module.exports)
    return module.exports
  }
  loadModule(path.join(root, 'app.js'))
  assert.equal(typeof app.onLaunch, 'function')
  assert.equal(app.globalData.baseUrl, 'http://localhost:8080')
  assert.equal(loadModule(path.join(root, 'config/runtime.js')).requestTimeoutMs, 15000)
})
