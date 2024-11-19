package com.hmdp.listener;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.utils.RedisConstants;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RabbitMQConstants.*;

/**
 * @author xy
 * @date 2024-11-18 17:15
 */

@Component
public class ShopUpdateListener {

    @Resource
    StringRedisTemplate stringRedisTemplate;


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = SHOP_QUEUE, durable = "true",
                    arguments = @Argument(name = "x-queue-mode", value = "lazy")),
            exchange = @Exchange(name = SHOP_DIRECT),
            key = SHOP_CACHE_KEY
    ))
    public void shopUpdateListener(Shop shop){
        String shopJSON = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + shop.getId(), shopJSON,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
    }

}
