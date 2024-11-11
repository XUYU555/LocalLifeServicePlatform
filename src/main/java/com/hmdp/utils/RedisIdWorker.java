package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author xy
 * @date 2024-03-16 11:59
 */
@Component
public class RedisIdWorker {

    /*
    * 从那一时间起步
    */
    private static final long BEGIN_TIME = 1704067200L;

    /*
    * 多少位为时间戳位
    */
    private static final int TIME_BITS = 32;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        // 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long currentSecond = nowSecond - BEGIN_TIME;
        // 生成序列号
        String format = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + format);
        // 拼接并返回
        return currentSecond << TIME_BITS | count;
    }
}
