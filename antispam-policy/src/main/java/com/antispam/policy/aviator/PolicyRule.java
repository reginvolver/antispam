package com.antispam.policy.aviator;

import com.antispam.api.model.RiskLevel;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 单条规则：Aviator 表达式 + 命中后的风险级别 + 处罚 ID 列表。
 * 一个套餐（PolicyPackage）通常包含多条规则，按顺序（严重程度降序）评估。
 */
@Getter
@Builder
public class PolicyRule {
    /** Aviator 布尔表达式 */
    private final String expression;
    /** 命中时建议的风险级别 */
    private final RiskLevel level;
    /** 命中时需要执行的处罚 ID 列表 */
    private final List<String> punishmentIds;
    /** 规则描述（调试用） */
    private final String description;
}
