-- 执行真实业务 Lua；这里仅模拟它用到的 Redis hash/TTL 命令。
local hashes, expiry, clock = {}, {}, 0
redis = { call = function(command, key, ...)
    local args = {...}
    if expiry[key] and expiry[key] <= clock then hashes[key], expiry[key] = nil, nil end
    if command == 'EXISTS' then return hashes[key] and 1 or 0 end
    if command == 'HSET' then
        hashes[key] = hashes[key] or {}
        for i = 1, #args, 2 do hashes[key][args[i]] = args[i+1] end
        return 1
    end
    if command == 'HGET' then return hashes[key] and hashes[key][args[1]] end
    if command == 'EXPIRE' then expiry[key] = clock + tonumber(args[1]); return 1 end
    error('Unexpected Redis command: ' .. command)
end }
local function run(action, proof, user)
    KEYS, ARGV = {'test-ticket'}, {action, proof or '', user or ''}
    return assert(loadfile('backend/src/main/resources/auth/wechat-qr-session.lua'))()
end
assert(run('create', 'proof-hash', '180') == 'PENDING')
assert(run('consume', 'proof-hash') == 'INVALID') -- 未确认不能登录
assert(run('status', 'wrong-browser-proof') == 'INVALID')
assert(run('confirm', '', '42') == 'CONFIRMED')
assert(run('confirm', '', '99') == 'INVALID') -- 另一账号不能替换身份
assert(run('confirm', '', '42') == 'CONFIRMED') -- 同一账号安全重试
assert(run('consume', 'wrong-browser-proof') == 'INVALID')
assert(run('consume', 'proof-hash') == '42')
assert(run('consume', 'proof-hash') == 'INVALID') -- 串行两次兑换只有一次成功
assert(run('confirm', '', '42') == 'INVALID')
assert(run('status', 'proof-hash') == 'CONSUMED')
clock = 181
assert(run('status', 'proof-hash') == 'EXPIRED')
assert(run('confirm', '', '42') == 'EXPIRED')
assert(run('consume', 'proof-hash') == 'EXPIRED')
print('WeChat QR Lua lifecycle, browser proof, single use, and expiry passed')
