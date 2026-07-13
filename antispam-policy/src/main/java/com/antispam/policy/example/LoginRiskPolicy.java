package com.antispam.policy.example;

import com.antispam.api.model.*;
import com.antispam.api.spi.PolicyPackage;
import com.antispam.policy.aviator.AviatorRuleEvaluator;
import com.antispam.policy.aviator.PolicyRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 示例套餐：登录风险策略。
 * 业务种类：ECOMMERCE，事件类型：LOGIN。
 *
 * 规则（按严重程度降序评估，第一条命中即返回）：
 *   1. loginFreq1Min > 10                                → BLOCK  + banAccount
 *   2. loginFreq1Min > 5 && deviceCount24h > 3          → REVIEW + captcha
 *   3. deviceCount24h > 5                               → REVIEW + captcha + rateLimit
 */
@Component
@RequiredArgsConstructor
public class LoginRiskPolicy implements PolicyPackage {

    private static final List<PolicyRule> RULES = List.of(
            PolicyRule.builder()
                    .expression("loginFreq1Min > 10")
                    .level(RiskLevel.BLOCK)
                    .punishmentIds(List.of("banAccount"))
                    .description("极高频登录直接封号")
                    .build(),
            PolicyRule.builder()
                    .expression("loginFreq1Min > 5 && deviceCount24h > 3")
                    .level(RiskLevel.REVIEW)
                    .punishmentIds(List.of("captcha"))
                    .description("高频登录且多设备，弹验证码")
                    .build(),
            PolicyRule.builder()
                    .expression("deviceCount24h > 5")
                    .level(RiskLevel.REVIEW)
                    .punishmentIds(List.of("captcha", "rateLimit"))
                    .description("设备数异常，弹验证码+限速")
                    .build()
    );

    private final AviatorRuleEvaluator ruleEvaluator;

    @Override
    public String policyId() {
        return "loginRiskPolicy";
    }

    @Override
    public String businessType() {
        return "ECOMMERCE";
    }

    @Override
    public List<String> requiredFactors() {
        return List.of("loginFreq1Min", "deviceCount24h");
    }

    @Override
    public PolicyResult evaluate(FactorMap facts) {
        Map<String, Object> variables = facts.toValueMap();

        for (PolicyRule rule : RULES) {
            if (ruleEvaluator.evaluate(rule.getExpression(), variables)) {
                return PolicyResult.builder()
                        .matched(true)
                        .suggestedLevel(rule.getLevel())
                        .punishmentIds(rule.getPunishmentIds())
                        .matchedRule(rule.getDescription())
                        .build();
            }
        }

        return PolicyResult.noMatch();
    }
}
