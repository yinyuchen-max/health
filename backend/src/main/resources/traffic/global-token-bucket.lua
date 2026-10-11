-- KEYS[1]: 所有实例共享的桶；ARGV: 每秒补充速度、桶容量。
-- 使用 Redis 时钟，避免各实例时钟不同；整个判断与扣减原子执行。
local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local state = redis.call('HMGET', KEYS[1], 'tokens', 'updated')
local tokens = tonumber(state[1]) or capacity
local updated = tonumber(state[2]) or now
now = math.max(now, updated)
tokens = math.min(capacity, tokens + (now - updated) * rate / 1000)

local retryMillis = 0
if tokens >= 1 then
    tokens = tokens - 1
else
    retryMillis = math.ceil((1 - tokens) * 1000 / rate)
end
redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'updated', tostring(now))
-- 空闲到桶重新填满后即可清理；下一次访问以满桶开始。
redis.call('PEXPIRE', KEYS[1], math.max(1000, math.ceil(capacity * 1000 / rate)))
return retryMillis
