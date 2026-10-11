-- Execute production scripts in a real Lua interpreter with an isolated in-memory command fixture.
-- No application Redis data is accessed. Deployment smoke tests still require a real Redis instance.
local state = {}
redis = { call = function(command, key, ...)
    local args = {...}
    if command == 'GET' then return state[key] end
    if command == 'EXISTS' then return state[key] and 1 or 0 end
    if command == 'DEL' then state[key] = nil; return 1 end
    if command == 'SET' then state[key] = args[1]; return 'OK' end
    if command == 'HGET' then return state[key] and state[key][args[1]] end
    if command == 'HSET' then
        state[key] = state[key] or {}
        for i=1,#args,2 do state[key][args[i]] = args[i+1] end
        return 1
    end
    if command == 'PEXPIREAT' then return 1 end
    error('Unsupported fixture command: '..command)
end }
local root = arg[1]
local function run(name, keys, arguments)
    KEYS, ARGV = keys, arguments
    return assert(loadfile(root..'/'..name..'.lua'))()
end
local function create(client, expires)
    state = {}
    run('create-session', {'session','epoch'}, {'alice','user','first', tostring(expires or 10000),client or 'web','fingerprint'})
end
create()
assert(#run('validate-session', {'session','epoch'}, {'alice','1000'}) == 1)
assert(#run('validate-session', {'session','epoch'}, {'other','1000'}) == 0)
assert(#run('rotate-session', {'session','epoch','used:first'}, {'first','second','native','1000'}) == 0)
assert(#run('rotate-session', {'session','epoch','used:first'}, {'first','second','web','1000'}) == 3)
assert(state.session.hash == 'second' and state['used:first'] == '1')
assert(#run('rotate-session', {'session','epoch','used:random'}, {'random','third','web','1001'}) == 0)
assert(state.session, 'A guessed credential must not revoke a legitimate session')
assert(#run('rotate-session', {'session','epoch','used:first'}, {'first','third','web','1002'}) == 0)
assert(not state.session, 'Reusing consumed refresh must revoke its device session')
create()
state.epoch = '1'
assert(#run('validate-session', {'session','epoch'}, {'alice','1000'}) == 0)
assert(#run('rotate-session', {'session','epoch','used:first'}, {'first','second','web','1000'}) == 0)
assert(not state.session)
create('web', 1000)
assert(#run('validate-session', {'session','epoch'}, {'alice','1000'}) == 0)
assert(#run('rotate-session', {'session','epoch','used:first'}, {'first','second','web','1000'}) == 0)
print('Lua session rotation/replay/epoch/expiry/client-isolation checks passed')
