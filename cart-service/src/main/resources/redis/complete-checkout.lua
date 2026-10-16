-- KEYS[1] = cart key
-- KEYS[2] = completed checkout key
-- KEYS[3] = durable claim key
-- KEYS[4] = execution lease key

-- ARGV[1] = expected cart JSON
-- ARGV[2] = checkout record JSON
-- ARGV[3] = checkout TTL in seconds
-- ARGV[4] = idempotency key
-- ARGV[5] = lease token

local claimJson = redis.call('GET', KEYS[3])

if not claimJson then
    return 0
end

local claim = cjson.decode(claimJson)

if claim.idempotencyKey ~= ARGV[4] then
    return 0
end

if claim.cartSnapshot ~= ARGV[1] then
    return 0
end

local currentLease = redis.call('GET', KEYS[4])

if currentLease ~= ARGV[5] then
    return 0
end

local currentCart = redis.call('GET', KEYS[1])

if currentCart ~= claim.cartSnapshot then
    return 0
end

redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])

redis.call('DEL', KEYS[1])
redis.call('DEL', KEYS[3])
redis.call('DEL', KEYS[4])

return 1