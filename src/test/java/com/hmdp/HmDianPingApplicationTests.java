package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    ShopServiceImpl shopService;

    @Resource
    RedisIdWorker redisIdWorker;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    private ExecutorService ex = Executors.newFixedThreadPool(500);

    @Test
    public void testShop2RedisData() {
        shopService.shop2RedisData(1L, 20L);
    }

    @Test
    public void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable runnable = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id= " + id);
            }
            latch.countDown();
        };

        for (int i = 0; i < 300; i++) {
            ex.submit(runnable);
        }
        latch.await();
    }

    @Resource
    RedissonClient redissonClient;

    @Test
    public void testRedisson() throws InterruptedException {
        // 获取锁对象
        RLock lock = redissonClient.getLock("lockName");
        // 尝试获取锁 参数1：获取锁失败的等待时间  参数2：超时释放时间   参数3：时间单位
        boolean tryLock = lock.tryLock(1, 10, TimeUnit.SECONDS);
        if (tryLock) {
            try {
                System.out.println("执行业务代码");
            } finally {
                // 释放锁
                lock.unlock();
            }
        }
    }

    // 将商铺的位置信息存入redis
    @Test
    public void loadShopData() {
        List<Shop> list = shopService.list();
        // 按照商铺类型分组
        Map<Long, List<Shop>> group = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> longListEntry : group.entrySet()) {
            Long typeId = longListEntry.getKey();
            String key = "geo:shop:" + typeId.toString();
            List<Shop> value = longListEntry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for (Shop shop : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>
                        (shop.getId().toString(), new Point(shop.getX(), shop.getY()))
                );
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

}
