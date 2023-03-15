package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author Acer
 * @create 2023/3/8 20:04
 */
@Component
@Slf4j
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value, Long logicalExpireTime, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(logicalExpireTime)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R getPassThrough(String keyPrefix, ID id, Class<R> type, Long time, TimeUnit timeUnit,
                                    Function<ID, R> dbFallback) {
        String key = keyPrefix + id;
        String valueJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(valueJson)) {
            return JSONUtil.toBean(valueJson, type);
        }
        if ("".equals(valueJson)) {
            return null;
        }
        R result = dbFallback.apply(id);
        if (result == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(result), time, timeUnit);
        return result;
    }
}
