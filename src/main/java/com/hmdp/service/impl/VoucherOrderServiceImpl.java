package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    RedisIdWorker redisIdWorker;

    @Resource
    ISeckillVoucherService service;

    @Resource
    RedissonClient redissonClient;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    private final static DefaultRedisScript<Long> REDIS_SCRIPT;
    static {
        REDIS_SCRIPT = new DefaultRedisScript<>();
        REDIS_SCRIPT.setResultType(Long.class);
        REDIS_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    }
    // 获取单个线程
    private final static ExecutorService VOUCHER_ORDER_EXECUTORS = Executors.newSingleThreadExecutor();
    private IVoucherOrderService proxy;
    // 添加注释，在类被初始化是就执行
    @PostConstruct
    private void init() {
        VOUCHER_ORDER_EXECUTORS.submit(new VoucherHandler());
    }
    private class VoucherHandler implements Runnable {
        private final String streamKey = "stream.orders" ;
        @Override
        public void run() {
            while (true) {
                try {
                    // 从消息队列中获得未处理的消息
                    // XREADGROUP GROUP group consumer [COUNT count] [BLOCK milliseconds] [NOACK] STREAMS key [key ...] ID [ID
                    List<MapRecord<String, Object, Object>> read = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                    );
                    // 判断是否有消息
                    if (read == null || read.isEmpty()) {
                        // 没有消息，则进入下一循环
                        continue;
                    }
                    // 有消息，获得数据创建订单
                    Map<Object, Object> value = read.get(0).getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    handlerVoucherOrder(voucherOrder);
                    // 确认消息
                    stringRedisTemplate.opsForStream().acknowledge(streamKey, "g1", read.get(0).getId());
                } catch (Exception e) {
                    log.error(e.getMessage());
                    // 出现异常，表示消息未处理完，加入了padding-list中待处理
                    handPaddingList();
                }
            }
        }

        private void handPaddingList() {
            while (true) {
                try {
                    // 从padding-list中获得未处理的消息
                    // XREADGROUP GROUP group consumer [COUNT count] [BLOCK milliseconds] [NOACK] STREAMS key [key ...] ID [ID
                    // 使用其他表示，表示从padding-list中获取数据，不需要阻塞了
                    List<MapRecord<String, Object, Object>> read = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(streamKey, ReadOffset.from("0"))
                    );
                    // 判断是否有消息
                    if (read == null || read.isEmpty()) {
                        // paddinglist中没有消息，直接结束循环
                        break;
                    }
                    // 有消息，获得数据创建订单
                    Map<Object, Object> value = read.get(0).getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    handlerVoucherOrder(voucherOrder);
                    // 确认消息
                    stringRedisTemplate.opsForStream().acknowledge(streamKey, "g1", read.get(0).getId());
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }
        }
    }

    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        RLock lock = redissonClient.getLock("lock:order:" + voucherOrder.getUserId());
        boolean tryLock = lock.tryLock();
        if (!tryLock) {
            log.error("获取锁失败");
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result addOrder(Long voucherId) {
        long orderId = redisIdWorker.nextId("order");
        // 使用lua脚本，具有原子性
        Long execute = stringRedisTemplate
                .execute(REDIS_SCRIPT, Collections.emptyList(),
                        voucherId.toString(),
                        UserHolder.getUser().getId().toString(),
                        String.valueOf(orderId)
                );
        if (execute.intValue() != 0) {
            return Result.fail("库存不足或已达购买上限");
        }
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }


    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 一人一单
        int count = query().eq("voucher_id", voucherOrder.getVoucherId())
                .eq("user_id", voucherOrder.getUserId()).count();
        if(count > 0) {
            log.error("用户已达购买上限");
        }
        boolean success = service.update()
                .setSql("stock = stock -1")
                // 优化后的乐观锁（原本乐观锁成功率低）
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if(!success) {
            log.error("库存不足");
        }
        save(voucherOrder);
    }

    /*@Override
    public Result addOrder(Long voucherId) {
        SeckillVoucher voucher = service.getById(voucherId);
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("活动未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("活动已结束");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("库存以售罄");
        }
        Long userId = UserHolder.getUser().getId();
        // 获取锁对象
        SampleRedisLock lock = new SampleRedisLock("order:" + userId, stringRedisTemplate);
        // 使用userid作为锁对象，缩小锁的范围
        boolean isLock = lock.tryLock(10);
        if(!isLock) {
            // 返回错误或者是重试
            return Result.fail("用户已达到购买上限");
        }
        // 获取代理对象（事务）
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 不管是否出错，都要释放锁
            lock.unLock();
        }
    }

    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 一人一单
        int count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
        if(count > 0) {
            return Result.fail("你已达到购买上限");
        }
        boolean success = service.update()
                .setSql("stock = stock -1")
                // 优化后的乐观锁（原本乐观锁成功率低）
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if(!success) {
            return Result.fail("库存不足");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        long id = redisIdWorker.nextId("order");
        voucherOrder.setId(id);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        save(voucherOrder);
        return Result.ok(id);
    }*/
}
