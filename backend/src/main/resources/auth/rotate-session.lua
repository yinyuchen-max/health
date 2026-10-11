-- KEYS: session, user epoch, consumed refresh digest. ARGV: old/new digests, client, now.
if redis.call('EXISTS', KEYS[1]) == 0 then return {} end
if redis.call('HGET', KEYS[1], 'client') ~= ARGV[3] then return {} end
if redis.call('HGET', KEYS[1], 'epoch') ~= (redis.call('GET', KEYS[2]) or '0') then
    redis.call('DEL', KEYS[1]); return {}
end
local expires = tonumber(redis.call('HGET', KEYS[1], 'expires'))
if expires <= tonumber(ARGV[4]) then redis.call('DEL', KEYS[1]); return {} end
if redis.call('HGET', KEYS[1], 'hash') ~= ARGV[1] then
    -- A previously consumed refresh token revokes the entire device session.
    if redis.call('EXISTS', KEYS[3]) == 1 then redis.call('DEL', KEYS[1]) end
    return {}
end
redis.call('SET', KEYS[3], '1', 'PX', expires - tonumber(ARGV[4]))
redis.call('HSET', KEYS[1], 'hash', ARGV[2])
return {redis.call('HGET', KEYS[1], 'username'), redis.call('HGET', KEYS[1], 'role'), tostring(expires)}
