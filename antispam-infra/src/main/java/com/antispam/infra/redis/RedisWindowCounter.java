package com.antispam.infra.redis;

/**
 * ZSet 滑动窗口计数与 Set 计数接口。
 */
public interface RedisWindowCounter {
    long count(String key, long windowStartMs, long windowEndMs);
    void addEvent(String key, long score, long ttlSeconds);
    void addToSet(String key, String member, long ttlSeconds);
    long countSet(String key);
}
