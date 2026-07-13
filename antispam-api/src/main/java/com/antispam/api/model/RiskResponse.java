package com.antispam.api.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@ToString
public class RiskResponse {
    /** 最终风险级别（取所有命中套餐的最高级别） */
    private final RiskLevel level;
    /** 命中的套餐 ID 列表 */
    @Builder.Default
    private final List<String> matchedPolicies = Collections.emptyList();
    /** 已触发/执行的处罚结果列表 */
    @Builder.Default
    private final List<PunishmentResult> punishments = Collections.emptyList();
    /** 所有因子的计算结果（调试用：factorId -> effectiveValue） */
    @Builder.Default
    private final Map<String, Object> factorValues = Collections.emptyMap();
    /** 总耗时（毫秒） */
    private final long elapsedMs;
    /** 是否触发了全局超时降级（true 表示部分因子未完成即返回） */
    private final boolean timedOut;
}
