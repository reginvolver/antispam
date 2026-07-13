package com.antispam.api.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
@ToString
public class PolicyResult {
    /** 是否命中任意规则 */
    private final boolean matched;
    /** 命中时建议的风险级别 */
    @Builder.Default
    private final RiskLevel suggestedLevel = RiskLevel.PASS;
    /** 命中后需要执行的处罚 ID 列表 */
    @Builder.Default
    private final List<String> punishmentIds = Collections.emptyList();
    /** 命中的规则描述（调试用） */
    private final String matchedRule;

    /** 未命中任何规则时的标准返回 */
    public static PolicyResult noMatch() {
        return PolicyResult.builder().matched(false).build();
    }
}
