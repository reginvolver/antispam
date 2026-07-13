package com.antispam.infra.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis ZSet 的滑动窗口计数器。
 * Score = 事件时间戳（毫秒），Member = 唯一事件 ID。
 * 使用 ZADD + ZCOUNT + ZREMRANGEBYSCORE 实现精准滑动窗口。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RedisWindowCounter {

    private final StringRedisTemplate redisTemplate;

    /**
     * 统计 [windowStartMs, windowEndMs] 时间范围内的事件数。
     *
     * @param key            Redis ZSet Key（由 RedisKeyHelper 生成）
     * @param windowStartMs  窗口起始时间戳（毫秒）
     * @param windowEndMs    窗口结束时间戳（毫秒）
     * @return 窗口内事件数，Redis 不可用时返回 0
     */
    public long count(String key, long windowStartMs, long windowEndMs) {
        try {
            Long count = redisTemplate.opsForZSet().count(key,
                    (double) windowStartMs, (double) windowEndMs);
            return count == null ? 0L : count;
        } catch (Exception e) {
            log.warn("[RedisWindowCounter] Failed to count key={}: {}", key, e.getMessage());
            return 0L;
        }
    }

    /**
     * 记录一次事件到滑动窗口。
     *
     * @param key        Redis ZSet Key
     * @param score      事件时间戳（毫秒），作为 ZSet score
     * @param ttlSeconds ZSet 的 TTL（秒），防止 key 永久增长
     */
    public void addEvent(String key, long score, long ttlSeconds) {
        try {
            String member = UUID.randomUUID().toString();
            redisTemplate.opsForZSet().add(key, member, (double) score);
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            // 清理比 TTL 更旧的数据（防止 ZSet 无限增长）
            long windowStart = score - ttlSeconds * 1000;
            redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, windowStart);
        } catch (Exception e) {
            log.warn("[RedisWindowCounter] Failed to add event to key={}: {}", key, e.getMessage());
        }
    }

    /**
     * 向 Redis Set 中添加一个成员（用于设备去重统计）。
     *
     * @param key        Redis Set Key
     * @param member     要添加的成员（如 deviceId）
     * @param ttlSeconds Set 的 TTL（秒）
     */
    public void addToSet(String key, String member, long ttlSeconds) {
        try {
            redisTemplate.opsForSet().add(key, member);
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[RedisWindowCounter] Failed to add to set key={}: {}", key, e.getMessage());
        }
    }

    /**
     * 统计 Redis Set 的成员数（用于设备数量统计）。
     */
    public long countSet(String key) {
        try {
            Long size = redisTemplate.opsForSet().size(key);
            return size == null ? 0L : size;
        } catch (Exception e) {
            log.warn("[RedisWindowCounter] Failed to count set key={}: {}", key, e.getMessage());
            return 0L;
        }
    }
}
