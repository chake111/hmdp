package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author chake
 * @since 2025/8/7
 */
public class SimpleRedisLock implements ILock {

    private final StringRedisTemplate redisTemplate;
    private String name;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);

    }

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
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
        // 调用lua脚本
        redisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                Collections.singletonList(ID_PREFIX + Thread.currentThread().getId()));
    }
}
