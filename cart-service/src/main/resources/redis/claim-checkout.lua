-- KEYS[1] = cart key
-- KEYS[2] = checkout claim key
-- ARGV[1] = expected cart JSON
-- ARGV[2] = idempotency key

local currentCart = redis.call('GET', KEYS[1])

if not currentCart or currentCart ~= ARGV[1] then
    return 0
end

local owner = redis.call('GET', KEYS[2])

if owner then
    -- An existing claim must be resolved first,
    -- even when the same key retries.
    return 0
end

redis.call('SET', KEYS[2], ARGV[2])

return 1