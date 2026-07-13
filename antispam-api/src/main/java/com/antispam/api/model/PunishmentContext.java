package com.antispam.api.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

@Getter
@Builder
public class PunishmentContext {
    private final RiskContext riskContext;
    private final RiskLevel level;
    /** 处罚配置参数，来自套餐配置（如 banDurationSeconds、webhookUrl 等） */
    @Builder.Default
    private final Map<String, Object> config = Collections.emptyMap();
}
