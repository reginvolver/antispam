package com.antispam.core.engine;

import com.antispam.api.model.*;
import com.antispam.api.spi.*;
import com.antispam.core.graph.GraphExecutor;
import com.antispam.core.registry.FactorRegistry;
import com.antispam.policy.registry.PolicyRegistry;
import com.antispam.punishment.executor.PunishmentExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * RiskEngine 的默认实现，协调 GraphExecutor、PolicyRegistry、PunishmentExecutor 并埋点监控指标。
 */
@Slf4j
@Service
public class DefaultRiskEngine implements RiskEngine {

    private final GraphExecutor graphExecutor;
    private final FactorRegistry factorRegistry;
    private final PolicyRegistry policyRegistry;
    private final PunishmentExecutor punishmentExecutor;
    private final MeterRegistry meterRegistry;
    private final long timeoutMs;

    public DefaultRiskEngine(
            GraphExecutor graphExecutor,
            FactorRegistry factorRegistry,
            PolicyRegistry policyRegistry,
            PunishmentExecutor punishmentExecutor,
            MeterRegistry meterRegistry,
            @Value("${antispam.engine.timeout-ms:500}") long timeoutMs) {
        this.graphExecutor = graphExecutor;
        this.factorRegistry = factorRegistry;
        this.policyRegistry = policyRegistry;
        this.punishmentExecutor = punishmentExecutor;
        this.meterRegistry = meterRegistry;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public RiskResponse evaluate(RiskContext context) {
        long start = System.currentTimeMillis();
        Objects.requireNonNull(context, "RiskContext must not be null");

        // 1. 加载此业务种类对应的所有套餐
        List<PolicyPackage> policies = policyRegistry.getByBusinessType(context.getBusinessType());

        // 2. 收集所有需要的因子（去重）
        Set<String> requiredFactorIds = new LinkedHashSet<>();
        policies.forEach(p -> requiredFactorIds.addAll(p.requiredFactors()));
        List<Factor> factors = factorRegistry.getFactorsByIds(new ArrayList<>(requiredFactorIds));

        // 3. 执行 DAG 因子图
        boolean timedOut = false;
        FactorMap factorMap;
        try {
            long graphStart = System.nanoTime();
            factorMap = graphExecutor.execute(factors, context, timeoutMs);
            long graphDuration = System.nanoTime() - graphStart;
            
            // 埋点 DAG 运行期延时
            Timer.builder("antispam.graph.execute.latency")
                    .description("Latency of DAG execution")
                    .tag("businessType", context.getBusinessType())
                    .register(meterRegistry)
                    .record(graphDuration, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.error("[DefaultRiskEngine] Graph execution failed: {}", e.getMessage());
            factorMap = new FactorMap();
            timedOut = true;
        }

        long graphElapsed = System.currentTimeMillis() - start;
        if (graphElapsed >= timeoutMs) {
            timedOut = true;
        }

        // 4. 评估套餐
        RiskLevel finalLevel = RiskLevel.PASS;
        List<String> matchedPolicies = new ArrayList<>();
        List<String> allPunishmentIds = new ArrayList<>();

        for (PolicyPackage policy : policies) {
            PolicyResult result = policy.evaluate(factorMap);
            if (result.isMatched()) {
                matchedPolicies.add(policy.policyId());
                finalLevel = finalLevel.max(result.getSuggestedLevel());
                allPunishmentIds.addAll(result.getPunishmentIds());
                log.info("[DefaultRiskEngine] Policy [{}] matched for userId={}, level={}",
                        policy.policyId(), context.getUserId(), result.getSuggestedLevel());
            }
        }

        // 5. 执行处罚
        List<PunishmentResult> punishmentResults = Collections.emptyList();
        if (!allPunishmentIds.isEmpty()) {
            PunishmentContext punishmentContext = PunishmentContext.builder()
                    .riskContext(context)
                    .level(finalLevel)
                    .build();
            punishmentResults = punishmentExecutor.execute(allPunishmentIds, punishmentContext);
        }

        long elapsed = System.currentTimeMillis() - start;

        // 埋点风控系统统计指标 (Counter + Timer)
        meterRegistry.counter("antispam.risk.requests",
                "businessType", context.getBusinessType(),
                "level", finalLevel.name(),
                "timedOut", String.valueOf(timedOut)
        ).increment();

        Timer.builder("antispam.risk.latency")
                .description("Total risk evaluation latency")
                .tag("businessType", context.getBusinessType())
                .register(meterRegistry)
                .record(elapsed, TimeUnit.MILLISECONDS);

        return RiskResponse.builder()
                .level(finalLevel)
                .matchedPolicies(matchedPolicies)
                .punishments(punishmentResults)
                .factorValues(factorMap.toValueMap())
                .elapsedMs(elapsed)
                .timedOut(timedOut)
                .build();
    }
}
