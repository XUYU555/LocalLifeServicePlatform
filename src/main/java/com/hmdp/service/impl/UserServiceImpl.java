package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            Result.fail("手机号无效");
        }
        // 生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 保存至session
        // session.setAttribute("code", code);
        // 保存到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 发送短信验证码
        log.debug("短信验证码为：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 验证手机号
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            Result.fail("手机号无效");
        }
        // 从session中获得code
        // Object code = session.getAttribute("code");
        // 从redis中获得code
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        // 验证验证码
        if(code == null || !code.equals(loginForm.getCode())) {
            Result.fail("验证码错误");
        }
        // 查询用户
        User user = query().eq("phone", loginForm.getPhone()).one();
        if (user == null) {
            // 不存在
            user = createUserWithPhone(loginForm.getPhone());
        }
        // 保存用户到session
        // session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));s
        // TODO 保存用户到redis
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> loginUser = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String token = UUID.randomUUID().toString(true);
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, loginUser);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 返回token
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = "sign:" + userId.toString() + format;
        // 获取日期，存入Redis中
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth-1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = "sign:" + userId.toString() + format;
        // 从Redis中查询，BITFIELD
        int dayOfMonth = now.getDayOfMonth();
        List<Long> list = stringRedisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));
        if (list == null || list.isEmpty()) {
            return Result.ok(0);
        }
        Long montySign = list.get(0);
        if(montySign == null || montySign == 0) {
            return Result.ok(0);
        }
        int count = 0;
        while (true) {
            if ((montySign & 1) == 0) {
                break;
            } else {
                // 右移一位，并抛弃。
                montySign >>>= 1;
                count++;
            }
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
