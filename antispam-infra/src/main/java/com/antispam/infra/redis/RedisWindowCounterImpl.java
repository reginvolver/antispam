package com.antispam.infra.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis ZSet 和 Set 的滑动窗口计数器实现。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RedisWindowCounterImpl implements RedisWindowCounter {

    private final StringRedisTemplate redisTemplate;

    @Override
    public long count(String key, long windowStartMs, long windowEndMs) {
        try {
            Long count = redisTemplate.opsForZSet().count(key,
                    (double) windowStartMs, (double) windowEndMs);
            return count == null ? 0L : count;
        } catch (Exception e) {
            log.warn("[RedisWindowCounterImpl] Failed to count key={}: {}", key, e.getMessage());
            return 0L;
        }
    }

    @Override
    public void addEvent(String key, long score, long ttlSeconds) {
        try {
            String member = UUID.randomUUID().toString();
            redisTemplate.opsForZSet().add(key, member, (double) score);
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            // 清理比 TTL 更旧的数据
            long windowStart = score - ttlSeconds * 1000;
            redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, windowStart);
        } catch (Exception e) {
            log.warn("[RedisWindowCounterImpl] Failed to add event to key={}: {}", key, e.getMessage());
        }
    }

    @Override
    public void addToSet(String key, String member, long ttlSeconds) {
        try {
            redisTemplate.opsForSet().add(key, member);
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[RedisWindowCounterImpl] Failed to add to set key={}: {}", key, e.getMessage());
        }
    }

    @Override
    public long countSet(String key) {
        try {
            Long size = redisTemplate.opsForSet().size(key);
            return size == null ? 0L : size;
        } catch (Exception e) {
            log.warn("[RedisWindowCounterImpl] Failed to count set key={}: {}", key, e.getMessage());
            return 0L;
        }
    }
}
