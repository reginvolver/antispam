package com.antispam.punishment.impl;

import com.antispam.api.model.*;
import com.antispam.infra.kafka.PunishmentEvent;
import com.antispam.infra.kafka.RiskKafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BanAccountPunishmentTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private RiskKafkaProducer kafkaProducer;

    private BanAccountPunishment punishment;

    @BeforeEach
    void setUp() {
        punishment = new BanAccountPunishment(redisTemplate, kafkaProducer);
    }

    @Test
    void punishmentId_isBanAccount() {
        assertEquals("banAccount", punishment.punishmentId());
    }

    @Test
    void type_isInternal() {
        assertEquals(PunishmentType.INTERNAL, punishment.type());
    }

    @Test
    void execute_writesBanKeyAndSendsKafkaEvent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        RiskContext ctx = RiskContext.builder()
                .userId("user1").businessType("ECOMMERCE").build();
        PunishmentContext pCtx = PunishmentContext.builder()
                .riskContext(ctx).level(RiskLevel.BLOCK).build();

        PunishmentResult result = punishment.execute(pCtx);

        // 验证写入 Redis ban key（24 小时）
        verify(valueOps).set(eq("antispam:ban:user1"), eq("BLOCK"), eq(86400L), eq(TimeUnit.SECONDS));

        // 验证推送 Kafka 事件
        ArgumentCaptor<PunishmentEvent> captor = ArgumentCaptor.forClass(PunishmentEvent.class);
        verify(kafkaProducer).sendPunishmentEvent(captor.capture());
        assertEquals("user1", captor.getValue().getUserId());
        assertEquals("banAccount", captor.getValue().getPunishmentId());

        assertTrue(result.isExecuted());
    }
}
