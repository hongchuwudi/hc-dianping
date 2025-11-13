package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1735689600L;
    private static final int COUNT_BITS = 32;
    @Resource private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix){
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2.自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.拼接并返回
        return timestamp << COUNT_BITS | count;
    }

    // 生成2025-1-1 00:00:00的秒数
    public static void main(String[] args) {
        long epochSecond = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
                .toEpochSecond(ZoneOffset.UTC);
        System.out.println(epochSecond);

    }
}
