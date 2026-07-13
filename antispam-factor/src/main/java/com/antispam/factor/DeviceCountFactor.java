package com.antispam.factor;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.FactorResult;
import com.antispam.api.model.RiskContext;
import com.antispam.api.spi.Factor;
import com.antispam.infra.redis.RedisKeyHelper;
import com.antispam.infra.redis.RedisWindowCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 设备数量因子：统计指定用户最近 24 小时内使用过的不同设备数量。
 * 使用 Redis Set 存储设备 ID，SCARD 获取集合大小。
 * Redis 不可用时降级返回 0。
 */
@Component
@RequiredArgsConstructor
public class DeviceCountFactor implements Factor {

    private final RedisWindowCounter redisWindowCounter;

    @Override
    public String factorId() {
        return "deviceCount24h";
    }

    @Override
    public List<String> dependencies() {
        return List.of(); // 无上游依赖
    }

    @Override
    public FactorResult compute(RiskContext ctx, FactorMap upstream) {
        String key = RedisKeyHelper.deviceCountKey(ctx.getUserId());
        long count = redisWindowCounter.countSet(key);
        return FactorResult.success(count);
    }
}
