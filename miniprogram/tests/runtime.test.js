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
