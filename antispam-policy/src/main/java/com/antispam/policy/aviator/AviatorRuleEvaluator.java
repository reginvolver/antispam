package com.antispam.policy.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Options;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 基于 Aviator 的规则表达式求值器。
 * Aviator 表达式示例：
 *   "loginFreq1Min > 5 && deviceCount24h > 3"
 *   "ipRiskScore >= 80"
 * 表达式中的变量名对应 FactorMap.toValueMap() 中的 key。
 */
@Slf4j
@Component
public class AviatorRuleEvaluator {

    private final AviatorEvaluatorInstance aviator;

    public AviatorRuleEvaluator() {
        this.aviator = AviatorEvaluator.newInstance();
        // 允许表达式访问 null 变量（未计算的因子默认为 null）
        this.aviator.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, false);
    }

    /**
     * 对给定变量环境求值 Aviator 表达式。
     *
     * @param expression Aviator 布尔表达式字符串
     * @param variables  变量 Map（通常来自 FactorMap.toValueMap()）
     * @return 表达式结果为 true 时返回 true；任何错误（包括解析失败）返回 false
     */
    public boolean evaluate(String expression, Map<String, Object> variables) {
        try {
            Object result = aviator.execute(expression, variables);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("[AviatorRuleEvaluator] Expression evaluation failed: [{}], error: {}",
                    expression, e.getMessage());
            return false; // 安全降级：规则求值失败不触发处罚
        }
    }
}
