function positiveMilliseconds(name, raw, fallback) {
  if (raw === undefined || raw === '') return fallback
  const value = Number(raw)
  if (!Number.isSafeInteger(value) || value <= 0) {
    throw new Error(`${name} 必须为正数毫秒`)
  }
  return value
}

// Vite 构建时读取，浏览器中只有这些公开的数值，不能在 VITE_* 中存密钥。
export const runtime = Object.freeze({
  requestTimeoutMs: positiveMilliseconds('VITE_REQUEST_TIMEOUT_MS', import.meta.env.VITE_REQUEST_TIMEOUT_MS, 15000),
  smartHealthTimeoutMs: positiveMilliseconds('VITE_SMART_HEALTH_TIMEOUT_MS', import.meta.env.VITE_SMART_HEALTH_TIMEOUT_MS, 90000)
})
