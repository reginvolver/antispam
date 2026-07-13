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
 * 登录频次因子：统计指定用户最近 1 分钟内的登录事件次数。
 * 依赖 Redis ZSet 滑动窗口，Redis 不可用时降级返回 0。
 */
@Component
@RequiredArgsConstructor
public class LoginFreqFactor implements Factor {

    /** 滑动窗口大小：1 分钟（毫秒） */
    private static final long WINDOW_MS = 60_000L;

    private final RedisWindowCounter redisWindowCounter;

    @Override
    public String factorId() {
        return "loginFreq1Min";
    }

    @Override
    public List<String> dependencies() {
        return List.of(); // 无上游依赖
    }

    @Override
    public FactorResult compute(RiskContext ctx, FactorMap upstream) {
        String key = RedisKeyHelper.loginFreqKey(ctx.getUserId());
        long now = ctx.getTimestamp();
        long windowStart = now - WINDOW_MS;

        long count = redisWindowCounter.count(key, windowStart, now);
        return FactorResult.success(count);
    }
}
