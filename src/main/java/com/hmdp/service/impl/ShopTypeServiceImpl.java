package com.hmdp.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        List<String> range = stringRedisTemplate.opsForList().range("cache:shop_type", 0, -1);
        if(range != null && !range.isEmpty()) {
            List<ShopType> shopTypes = range.stream().map(shopType -> JSONUtil.toBean(shopType, ShopType.class)).collect(Collectors.toList());
            return Result.ok(shopTypes);
        }
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        if (shopTypes == null) {
            return Result.fail("数据不存在");
        }
        List<String> shopTypeJson = shopTypes.stream().map(shopType -> JSONUtil.toJsonStr(shopType)).collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll("cache:shop_type", shopTypeJson);
        stringRedisTemplate.expire("cache:shop_type", 30L, TimeUnit.MINUTES);
        return Result.ok(shopTypes);
    }
}
