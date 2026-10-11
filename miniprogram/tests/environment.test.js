const test = require('node:test')
const assert = require('node:assert/strict')
const { resolveBaseUrl } = require('../config/environment')

function wxApi(envVersion, platform) {
  return {
    getAccountInfoSync: () => ({ miniProgram: { envVersion } }),
    getDeviceInfo: () => ({ platform })
  }
}

test('only developer tools in develop use localhost', () => {
  assert.equal(resolveBaseUrl(wxApi('develop', 'devtools')), 'http://localhost:8080')
  for (const platform of ['ios', 'android']) {
    assert.equal(resolveBaseUrl(wxApi('develop', platform)), 'https://api.zpew.top')
  }
})

test('trial and release use HTTPS even in developer tools', () => {
  for (const env of ['trial', 'release']) {
    for (const platform of ['ios', 'android', 'devtools']) {
      assert.equal(resolveBaseUrl(wxApi(env, platform)), 'https://api.zpew.top')
    }
  }
})

test('unknown environment fails instead of falling back to localhost', () => {
  assert.throws(() => resolveBaseUrl(wxApi('unknown', 'devtools')), /未配置/)
})
