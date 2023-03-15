package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author Acer
 * @create 2023/3/15 14:04
 */
public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "distributedLock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true);

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 为了解决锁误删的问题
        // 在获取锁的时候，存入线程id作为锁的标识
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name,
                ID_PREFIX + Thread.currentThread().getId(),
                timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock() {
        // 判断当前线程id是否和锁标识一致，一致才可以释放锁
        String threadId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if ((ID_PREFIX + Thread.currentThread().getId()).equals(threadId)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
