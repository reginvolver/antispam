package com.antispam.policy.aviator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AviatorRuleEvaluatorTest {

    private AviatorRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AviatorRuleEvaluator();
    }

    @Test
    void evaluate_simpleComparison_returnsTrue() {
        boolean result = evaluator.evaluate("loginFreq1Min > 5",
                Map.of("loginFreq1Min", 6L));
        assertTrue(result);
    }

    @Test
    void evaluate_simpleComparison_returnsFalse() {
        boolean result = evaluator.evaluate("loginFreq1Min > 5",
                Map.of("loginFreq1Min", 3L));
        assertFalse(result);
    }

    @Test
    void evaluate_andExpression_requiresBothConditions() {
        boolean result = evaluator.evaluate(
                "loginFreq1Min > 5 && deviceCount24h > 3",
                Map.of("loginFreq1Min", 6L, "deviceCount24h", 4L));
        assertTrue(result);

        boolean resultFalse = evaluator.evaluate(
                "loginFreq1Min > 5 && deviceCount24h > 3",
                Map.of("loginFreq1Min", 6L, "deviceCount24h", 2L));
        assertFalse(resultFalse);
    }

    @Test
    void evaluate_invalidExpression_returnsFalse() {
        // 不抛出异常，安全降级
        boolean result = evaluator.evaluate("INVALID_EXPR ###", Map.of());
        assertFalse(result);
    }
}
