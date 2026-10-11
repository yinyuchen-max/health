-- KEYS: session, user epoch. All credentials/refresh tokens are stored as digests.
local epoch = redis.call('GET', KEYS[2]) or '0'
redis.call('HSET', KEYS[1], 'username', ARGV[1], 'role', ARGV[2], 'hash', ARGV[3],
    'expires', ARGV[4], 'client', ARGV[5], 'epoch', epoch, 'credentials', ARGV[6])
redis.call('PEXPIREAT', KEYS[1], ARGV[4])
return epoch
