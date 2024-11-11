package com.hmdp.utils;

import org.springframework.data.redis.core.RedisTemplate;

/**
 * @author xy
 * @date 2024-03-18 18:15
 */
public interface ILock {

    // 尝试获取锁
    boolean tryLock(long timeSecond);

    // 释放锁
    void unLock();
}
