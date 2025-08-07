package com.hmdp.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author chake
 * @since 2025/8/6
 */

@Component
@RequiredArgsConstructor
public class RedisIdWorker {
    // 开始时间
    // 2025-08-06 00:00:00
    private static final long BEGIN_TIMESTAMP = 1754481600L;
    // 系列号位数
    private static final int SEQUENCE_BIT = 32;

    private final StringRedisTemplate redisTemplate;

    public long nextId(String keyPrefix) {
        // 1. 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        // 2. 生成序列号
        // 2.1 获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2 自增长
        long count = redisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3. 拼接并返回
        return timestamp << SEQUENCE_BIT | count;
    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2025, 8, 6, 12, 0, 0);
        long timestamp = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(timestamp);
    }
}
