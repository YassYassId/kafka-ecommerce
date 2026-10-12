-- KEYS[1] = cart key
-- KEYS[2] = checkout result key
-- KEYS[3] = checkout claim key
-- ARGV[1] = expected cart JSON
-- ARGV[2] = checkout result JSON
-- ARGV[3] = result TTL seconds
-- ARGV[4] = idempotency key

local owner = redis.call('GET', KEYS[3])

if owner ~= ARGV[4] then
    return 0
end

local currentCart = redis.call('GET', KEYS[1])

if currentCart ~= ARGV[1] then
    return 0
end

redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
redis.call('DEL', KEYS[1])
redis.call('DEL', KEYS[3])

return 1