local state = KEYS[1]
local now = redis.call('TIME')
local nowms = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
local expired = redis.call('ZRANGEBYSCORE', state .. ':permits', '-inf', nowms)
for _, token in ipairs(expired) do
  local dimensions = redis.call('HGET', state .. ':owners', token)
  if dimensions then
    for dimension in string.gmatch(dimensions, '[^,]+') do redis.call('ZREM', state .. ':d:' .. dimension, token) end
  end
  redis.call('ZREM', state .. ':permits', token)
  redis.call('HDEL', state .. ':owners', token)
end
local count = tonumber(ARGV[3])
for i = 1, count do
  local used = redis.call('ZCARD', KEYS[i + 1])
  local limit = tonumber(ARGV[3 + i])
  if used >= limit then
    local reply = 'R:' .. i
    for j = 1, count do reply = reply .. ':' .. tonumber(ARGV[3 + j]) .. ',' .. math.max(0, tonumber(ARGV[3 + j]) - redis.call('ZCARD', KEYS[j + 1])) end
    return reply
  end
end
local expiry = nowms + tonumber(ARGV[2])
local dimensions = ''
for i = 1, count do
  local digest = ARGV[3 + count + i]
  dimensions = dimensions .. (i == 1 and '' or ',') .. digest
  redis.call('ZADD', KEYS[i + 1], expiry, ARGV[1])
  redis.call('PEXPIRE', KEYS[i + 1], tonumber(ARGV[2]))
end
redis.call('ZADD', state .. ':permits', expiry, ARGV[1])
redis.call('HSET', state .. ':owners', ARGV[1], dimensions)
redis.call('PEXPIRE', state .. ':permits', tonumber(ARGV[2]))
redis.call('PEXPIRE', state .. ':owners', tonumber(ARGV[2]))
local reply = 'G'
for i = 1, count do reply = reply .. ':' .. tonumber(ARGV[3 + i]) .. ',' .. (tonumber(ARGV[3 + i]) - redis.call('ZCARD', KEYS[i + 1])) end
return reply
