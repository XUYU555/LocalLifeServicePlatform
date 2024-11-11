package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author xy
 * @date 2024-03-05 15:25
 */

public class RefreshLoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate redisTemplate;

    public RefreshLoginInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        Map<Object, Object> loginUser = redisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        UserDTO userDTO = BeanUtil.fillBeanWithMap(loginUser, new UserDTO(), false);
        if (BeanUtil.isEmpty(userDTO)) {
            return true;
        }
        UserHolder.saveUser(userDTO);
        // 刷新有效时间
        redisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
