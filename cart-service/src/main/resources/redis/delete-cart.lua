
-- KEYS[1] = cart key
-- KEYS[2] = checkout claim key
-- ARGV[1] = expected cart JSON ("" if missing)

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

redis.call('DEL', KEYS[1])

return 1