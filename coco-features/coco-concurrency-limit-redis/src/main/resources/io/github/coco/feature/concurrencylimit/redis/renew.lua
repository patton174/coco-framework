local state = KEYS[1]
local owner = redis.call('HGET', state .. ':owners', ARGV[1])
if not owner or redis.call('ZSCORE', state .. ':permits', ARGV[1]) == false then return 0 end
local now = redis.call('TIME')
local expiry = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000) + tonumber(ARGV[2])
redis.call('ZADD', state .. ':permits', expiry, ARGV[1])
redis.call('PEXPIRE', state .. ':permits', tonumber(ARGV[2]))
redis.call('PEXPIRE', state .. ':owners', tonumber(ARGV[2]))
for i, dimension in ipairs(KEYS) do
  if i > 1 and redis.call('ZSCORE', dimension, ARGV[1]) ~= false then
    redis.call('ZADD', dimension, expiry, ARGV[1]); redis.call('PEXPIRE', dimension, tonumber(ARGV[2]))
  end
end
return 1
