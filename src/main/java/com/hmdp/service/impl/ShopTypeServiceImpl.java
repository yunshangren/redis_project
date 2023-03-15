package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        List<String> shopTypes = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE, 0, -1);
        if(shopTypes != null && !shopTypes.isEmpty()){
            List<ShopType> res = new ArrayList<>();
            for (String shopType : shopTypes) {
                res.add(JSONUtil.toBean(shopType,ShopType.class));
            }
            return Result.ok(res);
        }

        List<ShopType> typeList = query().orderByAsc("sort").list();
        for (ShopType shopType : typeList) {
            stringRedisTemplate.opsForList().rightPush(RedisConstants.CACHE_SHOP_TYPE,JSONUtil.toJsonStr(shopType));
        }
        return Result.ok(typeList);
    }
}
