if redis.call('EXISTS', KEYS[1]) == 0 then return {} end
if redis.call('HGET', KEYS[1], 'epoch') ~= (redis.call('GET', KEYS[2]) or '0') then return {} end
if tonumber(redis.call('HGET', KEYS[1], 'expires')) <= tonumber(ARGV[2]) then return {} end
if redis.call('HGET', KEYS[1], 'username') ~= ARGV[1] then return {} end
return {redis.call('HGET', KEYS[1], 'credentials')}
