package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import com.hmdp.utils.ILock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author xy
 * @date 2024-03-18 18:17
 */
public class SampleRedisLock implements ILock {

    private final String name;

    private final StringRedisTemplate stringRedisTemplate;

    private static final String PREFIX = "lock:";
    // 添加线程特殊标识
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SampleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeSecond) {
        // 获取当前线程id
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean lock = stringRedisTemplate.opsForValue()
                .setIfAbsent( PREFIX + name, threadId, timeSecond, TimeUnit.SECONDS);
        // null也会返回false，不使用自动拆箱防止出现null
        return Boolean.TRUE.equals(lock);
    }

    @Override
    public void unLock() {
        // 使用Lua脚本，使查询与删除具有原子性
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
        // 在释放锁时判断
        /*String cur = ID_PREFIX + Thread.currentThread().getId();
        String threadId = stringRedisTemplate.opsForValue().get(PREFIX + name);
        if(cur.equals(threadId)) {
            stringRedisTemplate.delete("lock:" + name);
        }*/
    }
}
