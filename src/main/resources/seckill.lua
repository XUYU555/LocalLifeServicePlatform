-- 秒试卷id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]
-- 订单id
local orderId = ARGV[3]
-- 秒杀卷库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 用户下单key
local orderKey = 'seckill:order' .. voucherId
-- 判断库存是否充足
if (tonumber(redis.call('get', stockKey) ) <= 0) then
    return 1
end
-- 判断用户是否下单(set类型)
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end
-- 保存订单并减少库存
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
-- 将userid、voucherid、orderid，放入到消息队列中
-- XADD key [NOMKSTREAM] [MAXLEN|MINID [=|~] threshold [LIMIT count]] *|ID field value [field value ...]
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0

