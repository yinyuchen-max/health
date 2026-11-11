const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const path = require('path')

function loadRequest(wxRequest) {
  const source = fs.readFileSync(path.join(__dirname, '../utils/request.js'), 'utf8')
  const sandbox = {
    module: { exports: {} },
    require: () => ({ requestTimeoutMs: 1000, streamTimeoutMs: 1000 }),
    getApp: () => ({ globalData: { baseUrl: 'https://test.invalid', token: 'token' } }),
    wx: { request: wxRequest, showLoading() {}, hideLoading() {}, showToast() {} },
    TextDecoder,
    console
  }
  vm.runInNewContext(source, sandbox)
  return sandbox.module.exports
}

test('stream decoder keeps a UTF-8 character split across chunks', async () => {
  let deliver
  const request = loadRequest(options => {
    const task = {
      abort() {},
      onChunkReceived(handler) { deliver = handler }
    }
    return task
  })
  const frames = []
  request.streamRequest({
    url: '/api/chat/send/stream',
    onChunk: frame => frames.push(frame)
  })
  const prefix = new TextEncoder().encode('data:')
  const you = new TextEncoder().encode('你')
  const head = new Uint8Array(prefix.length + 2)
  head.set(prefix)
  head.set(you.slice(0, 2), prefix.length)
  deliver({ data: head.buffer })
  const tail = new Uint8Array(1 + 2)
  tail.set(you.slice(2), 0)
  tail.set(new TextEncoder().encode('\n\n'), 1)
  deliver({ data: tail.buffer })
  assert.equal(frames.join(''), 'data:你\n\n')
})

test('stream 401 retries after aborting the first request', async () => {
  const aborted = []
  let calls = 0
  const app = { globalData: { baseUrl: 'https://test.invalid', token: 'old' }, ensureAccess: async () => 'new' }
  const source = fs.readFileSync(path.join(__dirname, '../utils/request.js'), 'utf8')
  let done = false
  const sandbox = {
    module: { exports: {} },
    require: () => ({ requestTimeoutMs: 1000, streamTimeoutMs: 1000 }),
    getApp: () => app,
    wx: {
      request: options => {
        const mine = ++calls
        const task = { abort() { aborted.push(mine) }, onChunkReceived() {} }
        queueMicrotask(() => options.success({ statusCode: mine === 1 ? 401 : 200 }))
        return task
      },
      showLoading() {}, hideLoading() {}, showToast() {}
    },
    TextDecoder,
    console
  }
  vm.runInNewContext(source, sandbox)
  await new Promise(resolve => {
    sandbox.module.exports.streamRequest({
      url: '/api/chat/send/stream',
      onDone: () => { done = true; resolve() },
      onError: () => resolve()
    })
  })
  assert.equal(done, true)
  assert.equal(calls, 2)
  assert.deepEqual(aborted, [1])
})
