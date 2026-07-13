package com.antispam.punishment.impl;

import com.antispam.api.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaptchaPunishmentTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private CaptchaPunishment punishment;

    @BeforeEach
    void setUp() {
        punishment = new CaptchaPunishment(redisTemplate);
    }

    @Test
    void punishmentId_isCaptcha() {
        assertEquals("captcha", punishment.punishmentId());
    }

    @Test
    void type_isInternal() {
        assertEquals(PunishmentType.INTERNAL, punishment.type());
    }

    @Test
    void execute_writesRedisKeyWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        RiskContext ctx = RiskContext.builder().userId("user1").build();
        PunishmentContext pCtx = PunishmentContext.builder()
                .riskContext(ctx).level(RiskLevel.REVIEW).build();

        PunishmentResult result = punishment.execute(pCtx);

        verify(valueOps).set(eq("antispam:captcha:user1"), eq("1"), eq(300L), eq(TimeUnit.SECONDS));
        assertTrue(result.isExecuted());
        assertEquals("captcha", result.getPunishmentId());
    }

    @Test
    void execute_whenRedisThrows_returnsFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("Redis down")).when(valueOps)
                .set(anyString(), anyString(), anyLong(), any());

        RiskContext ctx = RiskContext.builder().userId("user1").build();
        PunishmentContext pCtx = PunishmentContext.builder()
                .riskContext(ctx).level(RiskLevel.REVIEW).build();

        PunishmentResult result = punishment.execute(pCtx);

        assertFalse(result.isExecuted());
    }
}
