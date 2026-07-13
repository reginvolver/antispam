package com.antispam.policy.example;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.FactorResult;
import com.antispam.api.model.PolicyResult;
import com.antispam.api.model.RiskLevel;
import com.antispam.policy.aviator.AviatorRuleEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginRiskPolicyTest {

    private LoginRiskPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new LoginRiskPolicy(new AviatorRuleEvaluator());
    }

    @Test
    void evaluate_normalUser_returnsPass() {
        FactorMap factorMap = new FactorMap();
        factorMap.put("loginFreq1Min", FactorResult.success(1L));
        factorMap.put("deviceCount24h", FactorResult.success(1L));

        PolicyResult result = policy.evaluate(factorMap);
        assertFalse(result.isMatched());
        assertEquals(RiskLevel.PASS, result.getSuggestedLevel());
    }

    @Test
    void evaluate_highLoginFreqAndManyDevices_returnsReview() {
        FactorMap factorMap = new FactorMap();
        factorMap.put("loginFreq1Min", FactorResult.success(6L)); // > 5
        factorMap.put("deviceCount24h", FactorResult.success(4L)); // > 3

        PolicyResult result = policy.evaluate(factorMap);
        assertTrue(result.isMatched());
        assertEquals(RiskLevel.REVIEW, result.getSuggestedLevel());
        assertTrue(result.getPunishmentIds().contains("captcha"));
    }

    @Test
    void evaluate_extremeLoginFreq_returnsBlock() {
        FactorMap factorMap = new FactorMap();
        factorMap.put("loginFreq1Min", FactorResult.success(11L)); // > 10
        factorMap.put("deviceCount24h", FactorResult.success(1L));

        PolicyResult result = policy.evaluate(factorMap);
        assertTrue(result.isMatched());
        assertEquals(RiskLevel.BLOCK, result.getSuggestedLevel());
        assertTrue(result.getPunishmentIds().contains("banAccount"));
    }

    @Test
    void policyId_andBusinessType_areSet() {
        assertEquals("ECOMMERCE", policy.businessType());
        assertEquals("loginRiskPolicy", policy.policyId());
    }

    @Test
    void requiredFactors_containsBothFactors() {
        assertTrue(policy.requiredFactors().contains("loginFreq1Min"));
        assertTrue(policy.requiredFactors().contains("deviceCount24h"));
    }
}
