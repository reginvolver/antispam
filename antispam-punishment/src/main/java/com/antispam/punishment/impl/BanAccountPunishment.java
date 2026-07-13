package com.antispam.punishment.impl;

import com.antispam.api.model.*;
import com.antispam.api.spi.Punishment;
import com.antispam.infra.kafka.PunishmentEvent;
import com.antispam.infra.kafka.RiskKafkaProducer;
import com.antispam.infra.redis.RedisKeyHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 封号处罚：将用户 ID 写入 Redis 黑名单，同时推送 Kafka 事件供下游系统消费。
 * Key: antispam:ban:{userId}，TTL: 24 小时（86400 秒，默认）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BanAccountPunishment implements Punishment {

    private static final long DEFAULT_BAN_SECONDS = 86_400L; // 24 小时

    private final StringRedisTemplate redisTemplate;
    private final RiskKafkaProducer kafkaProducer;

    @Override
    public String punishmentId() {
        return "banAccount";
    }

    @Override
    public PunishmentType type() {
        return PunishmentType.INTERNAL;
    }

    @Override
    public PunishmentResult execute(PunishmentContext ctx) {
        String userId = ctx.getRiskContext().getUserId();
        String key = RedisKeyHelper.banKey(userId);
        long banSeconds = (Long) ctx.getConfig().getOrDefault("banDurationSeconds", DEFAULT_BAN_SECONDS);

        try {
            // 1. 写 Redis 黑名单
            redisTemplate.opsForValue().set(key, ctx.getLevel().name(), banSeconds, TimeUnit.SECONDS);
            log.info("[BanAccountPunishment] Banned userId={} for {}s", userId, banSeconds);

            // 2. 异步推 Kafka
            kafkaProducer.sendPunishmentEvent(PunishmentEvent.builder()
                    .requestId(userId + "-" + System.currentTimeMillis())
                    .userId(userId)
                    .punishmentId(punishmentId())
                    .riskLevel(ctx.getLevel().name())
                    .businessType(ctx.getRiskContext().getBusinessType())
                    .timestamp(System.currentTimeMillis())
                    .build());

            return PunishmentResult.success(punishmentId());
        } catch (Exception e) {
            log.error("[BanAccountPunishment] Failed to ban userId={}: {}", userId, e.getMessage());
            return PunishmentResult.failure(punishmentId(), e.getMessage());
        }
    }
}
