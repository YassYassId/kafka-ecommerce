
-- KEYS[1] = cart key
-- KEYS[2] = checkout claim key
-- ARGV[1] = expected cart JSON ("" if missing)
-- ARGV[2] = updated cart JSON
-- ARGV[3] = cart TTL in seconds

if redis.call('EXISTS', KEYS[2]) == 1 then
    return 0
end

local current = redis.call('GET', KEYS[1])

if not current then
    current = ''
end

if current ~= ARGV[1] then
    return 2
end

redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])

return 1