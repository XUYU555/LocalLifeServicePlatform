package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author xy
 * @date 2024-03-18 21:56
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redisClient() {
        Config config = new Config();
        // 没有使用Redis集群模式，单体服务
        config.useSingleServer().setAddress("redis://192.168.129.135:6379");
        return Redisson.create(config);
    }
}
