package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        // 缓存穿透问题
        // Shop shop = queryByHCCT(id);
        // 缓存击穿问题(互斥锁)
        Shop shop = queryByHCJC(id);
        // 缓存击穿问题(逻辑过期时间)
        //Shop shop = queryByLJTIME(id);
        if (shop == null) {
            return Result.fail("商户不存在");
        }
        return Result.ok(shop);
    }

    private Shop queryByLJTIME(Long id) {
        // 查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (shopJson == null) {
            // 未命中
            return null;
        }
        RedisData re = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject jsonObject = (JSONObject) re.getData();
        Shop shop = JSONUtil.toBean(jsonObject, Shop.class);
        String lockKey = "lock:shop:" + id;
        // 命中， 判断是否过期
        if (!re.getExpireTime().isAfter(LocalDateTime.now())) {
            // 过期
            boolean isLock = tryLock(lockKey);
            if (isLock) {
                // 获取锁成功
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        // 重建缓存
                        shop2RedisData(id, 20L);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        unLock(lockKey);
                    }
                });
            } // 失败
            return shop;
        }
        // 未过期
        return shop;
    }

    public void shop2RedisData(Long id, Long expire) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expire));
        // 写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    private Shop queryByHCJC(Long id) {
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断空值
        if (shopJson != null) {
            return null;
        }
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try {
            // 获取互斥锁
            boolean lock = tryLock(lockKey);
            if (!lock) {
                // 获取失败，休眠在重试
                Thread.sleep(50);
                return queryByHCJC(id);
            } // 获取互斥锁成功
            // 做 DoubleCheck 获取互斥锁后在查询一遍缓存
            if (StrUtil.isNotBlank(stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id))) {
                return JSONUtil.toBean(stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id), Shop.class);
            }
            shop = getById(id);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", 2L, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 不管成功与否，都要释放锁
            unLock(lockKey);
        }
        return shop;
    }

    private Shop queryByHCCT(Long id) {
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断空值
        if (shopJson != null) {
            return null;
        }
        Shop shop = getById(id);
        if (shop == null) {
            // 设置一个空值
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", 2L, TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    private boolean tryLock(String lockKey) {
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "lock");
        return BooleanUtil.isTrue(lock);
    }

    private void unLock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("商户不存在");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 没有按照距离查询
        if(x == null && y == null) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        // 分页参数
        int form = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = "geo:shop:" + typeId.toString();
        // geosearch命令没有分页查询，只能逻辑分页查询到end，再在结果中跳过form
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        // 解析出shopId, 跳过form
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent()
                .stream().skip(form).collect(Collectors.toList());
        // 逻辑跳过后，为空则没有数据了
        if (content.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = new ArrayList<>(content.size());
        // 储存距离
        Map<String, Distance> distanceMap = new HashMap<>(content.size());
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoLocationGeoResult : content) {
            String id = geoLocationGeoResult.getContent().getName();
            ids.add(Long.valueOf(id));
            Distance distance = geoLocationGeoResult.getDistance();
            distanceMap.put(id, distance);
        }
        // 通过shopId获得Shop并保持有序
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        // 将distance写入shop中
        for (Shop shop : shops) {
            Distance distance = distanceMap.get(shop.getId().toString());
            shop.setDistance(distance.getValue());
        }
        return Result.ok(shops);
    }
}
