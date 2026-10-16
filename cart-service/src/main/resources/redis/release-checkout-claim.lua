-- KEYS[1] = durable claim key
-- KEYS[2] = execution lease key

-- ARGV[1] = idempotency key
-- ARGV[2] = lease token

local claimJson = redis.call('GET', KEYS[1])

if not claimJson then
    return 0
end

local claim = cjson.decode(claimJson)

if claim.idempotencyKey ~= ARGV[1] then
    return 0
end

if redis.call('GET', KEYS[2]) ~= ARGV[2] then
    return 0
end

redis.call('DEL', KEYS[1])
redis.call('DEL', KEYS[2])

return 1