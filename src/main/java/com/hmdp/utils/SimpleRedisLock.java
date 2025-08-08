package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author chake
 * @since 2025/8/7
 */
public class SimpleRedisLock implements ILock {

    private String name;
    private final String KEY_PREFIX = "lock:";
    private final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private final StringRedisTemplate redisTemplate;

    public SimpleRedisLock(String s, StringRedisTemplate redisTemplate) {
        this.name = s;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSeconds) {
        // 获取当前线程游标
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 尝试获取锁
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁的标识
        String lockId = redisTemplate.opsForValue().get(KEY_PREFIX + name);
        //判断是否一致
        if (threadId.equals(lockId)) {
            // 释放锁
            redisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
