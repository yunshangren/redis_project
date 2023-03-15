package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    private ExecutorService threadPool = Executors.newSingleThreadExecutor();
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据店铺id查询，如果缓存未命中，则查询数据库，将查询结果写入缓存并设置超时时间
     *
     * @param id 店铺id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透的方法
        // return queryShopWithPassThrough(id);

        // 互斥锁解决缓存击穿
        // return queryWithMutex(id);
        return queryWithLogicalExpire(id);
    }

    private Result queryShopWithPassThrough(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 从redis查询, 可能查到的是一个空值，需要判断
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        if ("".equals(shopJson)) {
            return Result.fail("店铺不存在");
        }
        Shop shop = getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "",
                    RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    private Result queryWithMutex(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 从redis查询, 可能查到的是一个空值，需要判断
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        if ("".equals(shopJson)) {
            return Result.fail("店铺不存在");
        }
        Shop shop = null;
        try {
            // 获取互斥锁失败
            if (!tryLock(RedisConstants.LOCK_SHOP_KEY + id)) {
                TimeUnit.MILLISECONDS.sleep(50);
                return queryWithMutex(id);
            }
            // 获取锁成功，再次检测redis缓存是否存在，做DoubleCheck，如果存在则无需重建缓存
            shopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson)) {
                shop = JSONUtil.toBean(shopJson, Shop.class);
                return Result.ok(shop);
            }
            // DoubleCheck 发现还是没有缓存，此时重建缓存
            Thread.sleep(200);
            shop = getById(id);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "",
                        RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("店铺不存在");
            }

            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),
                    RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 解锁
            unlock(RedisConstants.LOCK_SHOP_KEY + id);
        }
        return Result.ok(shop);


    }

    private Result queryWithLogicalExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        // 未过期
        if (LocalDateTime.now().isBefore(expireTime)) {
            return Result.ok(shop);
        }

        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        try {
            if (isLock) {
                threadPool.execute(() -> saveShop2Redis(id, 10));
                Thread.sleep(200);
            }
        } catch (Exception e) {
            throw new RuntimeException();
        } finally {
            unlock(lockKey);
        }
        return Result.ok(shop);
    }

    public void saveShop2Redis(Long id, int expireMinutes) {
        // 查询店铺数据
        Shop shop = getById(id);
        // 封装到RedisData
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireMinutes));
        // 保存到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    private boolean tryLock(String key) {
        Boolean res = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(res);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 更新店铺信息
     * 先更新数据库，再删除缓存！
     *
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result updateShopById(Shop shop) {
        Long shopId = shop.getId();
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);
        String key = RedisConstants.CACHE_SHOP_KEY + shopId;
        stringRedisTemplate.delete(key);
        return Result.ok("更新店铺成功");

    }
}
