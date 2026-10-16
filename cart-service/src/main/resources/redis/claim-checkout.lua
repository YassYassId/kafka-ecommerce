-- KEYS[1] = cart key
-- KEYS[2] = durable claim key
-- KEYS[3] = execution lease key

-- ARGV[1] = expected cart JSON
-- ARGV[2] = idempotency key
-- ARGV[3] = lease token
-- ARGV[4] = lease TTL in milliseconds

local claimJson = redis.call('GET', KEYS[2])

if claimJson then
    local claim = cjson.decode(claimJson)

    if claim.idempotencyKey ~= ARGV[2] then
        return 4 -- CONFLICT
    end

    if redis.call('EXISTS', KEYS[3]) == 1 then
        return 3 -- BUSY
    end

    -- Recover only if the original cart is unchanged.
    local currentCart = redis.call('GET', KEYS[1])

    if currentCart ~= claim.cartSnapshot then
        return 5 -- CART_CHANGED
    end

    redis.call('SET', KEYS[3], ARGV[3], 'PX', ARGV[4])

    return 2 -- RESUMED
end

local currentCart = redis.call('GET', KEYS[1])

if not currentCart or currentCart ~= ARGV[1] then
    return 5 -- CART_CHANGED
end

if redis.call('EXISTS', KEYS[3]) == 1 then
    return 3 -- BUSY
end

local claim = cjson.encode({
    idempotencyKey = ARGV[2],
    cartSnapshot = currentCart
})

redis.call('SET', KEYS[2], claim)
redis.call('SET', KEYS[3], ARGV[3], 'PX', ARGV[4])

return 1 -- ACQUIRED