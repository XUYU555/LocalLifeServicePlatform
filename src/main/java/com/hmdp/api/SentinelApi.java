package com.hmdp.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xy
 * @date 2024-04-23 21:45
 */

@RestController
public class SentinelApi {

    @Autowired
    StringRedisTemplate stringRedisTemplate;


    @GetMapping(value = "/get/{key}")
    public String getKey(@PathVariable String key) {
        String s = stringRedisTemplate.opsForValue().get(key);
        return s;
    }

    @GetMapping (value = "/set/{key}/{value}")
    public boolean setKey(@PathVariable String key,@PathVariable String value) {
        stringRedisTemplate.opsForValue().set(key, value);
        return true;
    }

}
