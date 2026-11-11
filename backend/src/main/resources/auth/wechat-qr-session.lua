local action = ARGV[1]
if action == 'create' then
    if redis.call('EXISTS', KEYS[1]) == 1 then return 'INVALID' end
    redis.call('HSET', KEYS[1], 'proof', ARGV[2], 'status', 'PENDING')
    redis.call('EXPIRE', KEYS[1], ARGV[3])
    return 'PENDING'
end
if redis.call('EXISTS', KEYS[1]) == 0 then return 'EXPIRED' end
local status = redis.call('HGET', KEYS[1], 'status')
if action == 'confirm' then
    if status == 'CONFIRMED' and redis.call('HGET', KEYS[1], 'userId') == ARGV[3] then return status end
    if status ~= 'PENDING' then return 'INVALID' end
    redis.call('HSET', KEYS[1], 'status', 'CONFIRMED', 'userId', ARGV[3])
    return 'CONFIRMED'
end
if redis.call('HGET', KEYS[1], 'proof') ~= ARGV[2] then return 'INVALID' end
if action == 'status' then return status end
if action == 'consume' and status == 'CONFIRMED' then
    local userId = redis.call('HGET', KEYS[1], 'userId')
    redis.call('HSET', KEYS[1], 'status', 'CONSUMED')
    return userId
end
return 'INVALID'
