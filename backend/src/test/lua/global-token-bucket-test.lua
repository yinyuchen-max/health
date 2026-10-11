-- Run: lua src/test/lua/global-token-bucket-test.lua src/main/resources/traffic/global-token-bucket.lua
-- Execute the production script with a deterministic Redis clock and command fixture.
local now = 100000
local state = {}
local expires = {}
redis = { call = function(command, key, ...)
    local args = {...}
    if command == 'TIME' then
        return {tostring(math.floor(now / 1000)), tostring((now % 1000) * 1000)}
    end
    if expires[key] and expires[key] <= now then state[key], expires[key] = nil, nil end
    if command == 'HMGET' then
        local value = state[key] or {}
        return {value[args[1]] or false, value[args[2]] or false}
    end
    if command == 'HSET' then
        state[key] = state[key] or {}
        for i = 1, #args, 2 do state[key][args[i]] = args[i+1] end
        return 1
    end
    if command == 'PEXPIRE' then expires[key] = now + args[1]; return 1 end
    error('Unsupported fixture command: '..command)
end }
local script = assert(loadfile(arg[1]))
local function acquire(key, rate, capacity)
    KEYS, ARGV = {key or 'shared'}, {tostring(rate or 2), tostring(capacity or 2)}
    return script()
end
assert(acquire() == 0 and acquire() == 0, 'Initial burst must fit capacity')
assert(acquire() == 500, 'All clients must share exhausted bucket')
now = now + 250
assert(acquire() == 250, 'Fractional refill must not admit a whole request')
now = now + 250
assert(acquire() == 0 and acquire() == 500, 'One replenished token admits exactly one request')
now = now + 4000
assert(acquire() == 0 and acquire() == 0 and acquire() == 500, 'Idle expiry must restore only capacity')
assert(expires.shared > now, 'Bucket state must expire')
-- Clock regression must not replenish twice when it recovers.
now = now - 250
assert(acquire() == 500)
now = now + 250
assert(acquire() == 500)
now = now + 500
assert(acquire() == 0)
assert(acquire('another') == 0, 'Independent deployment keys must be isolated')
assert(acquire('small', 3, 1) == 0 and acquire('small', 3, 1) == 334)
now = now + 334
assert(acquire('small', 3, 1) == 0, 'Retry delay must admit one token')
-- Both sides of a natural second share the bucket, rather than resetting a fixed window.
now = 109999
assert(acquire('boundary', 2, 1) == 0)
now = 110000
assert(acquire('boundary', 2, 1) == 499)
print('Global token bucket burst/refill/expiry/clock/shared-budget checks passed')
