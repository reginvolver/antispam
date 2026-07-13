package com.antispam.factor;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.FactorResult;
import com.antispam.api.model.RiskContext;
import com.antispam.infra.redis.RedisWindowCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginFreqFactorTest {

    @Mock
    private RedisWindowCounter redisWindowCounter;

    private LoginFreqFactor factor;

    @BeforeEach
    void setUp() {
        factor = new LoginFreqFactor(redisWindowCounter);
    }

    @Test
    void factorId_isLoginFreq1Min() {
        assertEquals("loginFreq1Min", factor.factorId());
    }

    @Test
    void dependencies_isEmpty() {
        assertTrue(factor.dependencies().isEmpty());
    }

    @Test
    void compute_returnsLoginCount() {
        when(redisWindowCounter.count(eq("antispam:login_freq:user1"), anyLong(), anyLong()))
                .thenReturn(3L);

        RiskContext ctx = RiskContext.builder().userId("user1").build();
        FactorResult result = factor.compute(ctx, new FactorMap());

        assertTrue(result.isSuccess());
        assertEquals(3L, result.getValue());
    }

    @Test
    void compute_whenRedisReturnsZero_returnsZero() {
        when(redisWindowCounter.count(anyString(), anyLong(), anyLong())).thenReturn(0L);

        RiskContext ctx = RiskContext.builder().userId("u2").build();
        FactorResult result = factor.compute(ctx, new FactorMap());

        assertTrue(result.isSuccess());
        assertEquals(0L, result.getValue());
    }
}
