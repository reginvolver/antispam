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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceCountFactorTest {

    @Mock
    private RedisWindowCounter redisWindowCounter;

    private DeviceCountFactor factor;

    @BeforeEach
    void setUp() {
        factor = new DeviceCountFactor(redisWindowCounter);
    }

    @Test
    void factorId_isDeviceCount24h() {
        assertEquals("deviceCount24h", factor.factorId());
    }

    @Test
    void dependencies_isEmpty() {
        assertTrue(factor.dependencies().isEmpty());
    }

    @Test
    void compute_returnsDeviceCount() {
        when(redisWindowCounter.countSet("antispam:device_count:user1")).thenReturn(2L);

        RiskContext ctx = RiskContext.builder().userId("user1").deviceId("dev1").build();
        FactorResult result = factor.compute(ctx, new FactorMap());

        assertTrue(result.isSuccess());
        assertEquals(2L, result.getValue());
    }
}
