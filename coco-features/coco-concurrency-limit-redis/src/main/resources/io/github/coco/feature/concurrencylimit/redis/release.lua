local state = KEYS[1]
local owner = redis.call('HGET', state .. ':owners', ARGV[1])
if not owner then return 0 end
redis.call('HDEL', state .. ':owners', ARGV[1])
redis.call('ZREM', state .. ':permits', ARGV[1])
for i, dimension in ipairs(KEYS) do if i > 1 then redis.call('ZREM', dimension, ARGV[1]) end end
return 1
