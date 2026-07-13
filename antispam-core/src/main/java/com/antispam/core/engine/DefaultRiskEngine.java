package com.antispam.core.engine;

import com.antispam.api.model.*;
import com.antispam.api.spi.*;
import com.antispam.core.graph.GraphExecutor;
import com.antispam.core.registry.FactorRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * RiskEngine 的默认实现，协调 GraphExecutor、PolicyRegistry、PunishmentExecutor。
 * 完整的 PolicyRegistry 和 PunishmentExecutor 注入在后面的 Task 中完成。
 */
@Slf4j
@Service
public class DefaultRiskEngine implements RiskEngine {

    private final GraphExecutor graphExecutor;
    private final FactorRegistry factorRegistry;
    private final long timeoutMs;

    public DefaultRiskEngine(
            GraphExecutor graphExecutor,
            FactorRegistry factorRegistry,
            @Value("${antispam.engine.timeout-ms:500}") long timeoutMs) {
        this.graphExecutor = graphExecutor;
        this.factorRegistry = factorRegistry;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public RiskResponse evaluate(RiskContext context) {
        long start = System.currentTimeMillis();
        Objects.requireNonNull(context, "RiskContext must not be null");

        // 此处仅执行因子计算（Policy 和 Punishment 由后续 Task 接入）
        List<Factor> allFactors = factorRegistry.getAll();
        FactorMap factorMap = graphExecutor.execute(allFactors, context, timeoutMs);

        long elapsed = System.currentTimeMillis() - start;
        boolean timedOut = elapsed >= timeoutMs;

        return RiskResponse.builder()
                .level(RiskLevel.PASS)
                .factorValues(factorMap.toValueMap())
                .elapsedMs(elapsed)
                .timedOut(timedOut)
                .matchedPolicies(Collections.emptyList())
                .punishments(Collections.emptyList())
                .build();
    }
}
