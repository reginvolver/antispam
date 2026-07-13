package com.antispam.punishment.impl;

import com.antispam.api.model.*;
import com.antispam.api.spi.Punishment;
import com.antispam.infra.redis.RedisKeyHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 验证码处罚：在 Redis 中为用户打标，下次请求时触发验证码验证。
 * Key: antispam:captcha:{userId}，TTL: 5 分钟（300 秒）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaptchaPunishment implements Punishment {

    private static final long TTL_SECONDS = 300L; // 5 分钟

    private final StringRedisTemplate redisTemplate;

    @Override
    public String punishmentId() {
        return "captcha";
    }

    @Override
    public PunishmentType type() {
        return PunishmentType.INTERNAL;
    }

    @Override
    public PunishmentResult execute(PunishmentContext ctx) {
        String userId = ctx.getRiskContext().getUserId();
        String key = RedisKeyHelper.captchaKey(userId);
        try {
            redisTemplate.opsForValue().set(key, "1", TTL_SECONDS, TimeUnit.SECONDS);
            log.info("[CaptchaPunishment] Captcha flag set for userId={}, ttl={}s", userId, TTL_SECONDS);
            return PunishmentResult.success(punishmentId());
        } catch (Exception e) {
            log.error("[CaptchaPunishment] Failed to set captcha flag for userId={}: {}", userId, e.getMessage());
            return PunishmentResult.failure(punishmentId(), e.getMessage());
        }
    }
}
