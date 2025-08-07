package com.hmdp.utils;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @author chake
 * @since 2025/8/7
 */
public class SimpleRedisLock implements ILock {

    private String name;
    private final String PREFIX = "lock:";
    private final StringRedisTemplate redisTemplate;

    public SimpleRedisLock(String s, StringRedisTemplate redisTemplate) {
        this.name = s;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSeconds) {
        // 获取当前线程游标
        long threadId = Thread.currentThread().getId();
        // 尝试获取锁
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(PREFIX + name, String.valueOf(threadId), timeoutSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        redisTemplate.delete(PREFIX + name);
    }
}
