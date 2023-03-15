package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author Acer
 * @create 2023/3/14 14:36
 */
@Component
public class RedisWorker {
    private static final long BEGIN_TIME_STAMP = 1640995200L;
    private static final int COUNT_BITS = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String prefix) {
        // 1. 生成时间戳
        long timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - BEGIN_TIME_STAMP;

        // 2. 生成序列号
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        String key = "icr:" + prefix + ":" + date;
        // 如果redis不存在这个key,会自动创建一个key
        long increment = stringRedisTemplate.opsForValue().increment(key);

        // 3. 拼接并返回
        return (timestamp << COUNT_BITS) | increment;
    }


}
