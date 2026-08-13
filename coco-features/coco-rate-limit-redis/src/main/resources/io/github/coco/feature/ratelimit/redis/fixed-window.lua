redis.replicate_commands()

local function normalize_decimal(value)
    if type(value) ~= 'string' or not string.match(value, '^%d+$') then
        return nil
    end
    local normalized = string.match(value, '^0*(%d+)$')
    if normalized == nil then
        return '0'
    end
    return normalized
end

local function compare_decimal(left, right)
    if string.len(left) < string.len(right) then
        return -1
    end
    if string.len(left) > string.len(right) then
        return 1
    end
    if left < right then
        return -1
    end
    if left > right then
        return 1
    end
    return 0
end

local function increment_decimal(value)
    local digits = {}
    local carry = 1
    for index = string.len(value), 1, -1 do
        local digit = string.byte(value, index) - string.byte('0') + carry
        if digit >= 10 then
            digit = digit - 10
            carry = 1
        else
            carry = 0
        end
        table.insert(digits, 1, string.char(string.byte('0') + digit))
    end
    if carry == 1 then
        table.insert(digits, 1, '1')
    end
    return table.concat(digits)
end

local function subtract_decimal(left, right)
    local digits = {}
    local borrow = 0
    local right_index = string.len(right)
    for left_index = string.len(left), 1, -1 do
        local left_digit = string.byte(left, left_index) - string.byte('0') - borrow
        local right_digit = 0
        if right_index >= 1 then
            right_digit = string.byte(right, right_index) - string.byte('0')
            right_index = right_index - 1
        end
        if left_digit < right_digit then
            left_digit = left_digit + 10
            borrow = 1
        else
            borrow = 0
        end
        table.insert(digits, 1, string.char(string.byte('0') + left_digit - right_digit))
    end
    return normalize_decimal(table.concat(digits))
end

local limit = normalize_decimal(ARGV[1])
local reset_at = normalize_decimal(ARGV[2])
if limit == nil or limit == '0' or reset_at == nil or reset_at == '0' then
    return redis.error_reply('invalid rate-limit arguments')
end

local redis_time = redis.call('TIME')
local microseconds = redis_time[2]
while string.len(microseconds) < 6 do
    microseconds = '0' .. microseconds
end
local now_millis = normalize_decimal(redis_time[1] .. string.sub(microseconds, 1, 3))
if compare_decimal(reset_at, now_millis) <= 0 then
    return 'E:0'
end

local current = redis.call('GET', KEYS[1])
if current == false then
    redis.call('SET', KEYS[1], '1')
    redis.call('PEXPIREAT', KEYS[1], reset_at)
    return '1:' .. subtract_decimal(limit, '1')
end

current = normalize_decimal(current)
if current == nil then
    return redis.error_reply('invalid rate-limit counter')
end
if compare_decimal(current, limit) >= 0 then
    return '0:0'
end

local next_value = increment_decimal(current)
redis.call('INCR', KEYS[1])
return '1:' .. subtract_decimal(limit, next_value)
