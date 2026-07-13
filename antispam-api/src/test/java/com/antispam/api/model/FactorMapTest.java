package com.antispam.api.model;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FactorMapTest {

    @Test
    void put_and_getValue_returnsEffectiveValue_whenSuccess() {
        FactorMap map = new FactorMap();
        map.put("loginFreq", FactorResult.success(5L));

        Optional<Object> value = map.getValue("loginFreq");
        assertTrue(value.isPresent());
        assertEquals(5L, value.get());
    }

    @Test
    void getValue_forFailedFactor_returnsFallbackNotNull() {
        FactorMap map = new FactorMap();
        map.put("loginFreq", FactorResult.failure(0L, "redis timeout"));

        Optional<Object> value = map.getValue("loginFreq");
        assertTrue(value.isPresent(), "Failed factor should still return a value (fallback)");
        assertEquals(0L, value.get(), "Failed factor should return fallback value 0L");
    }

    @Test
    void getValue_forMissingFactor_returnsEmpty() {
        FactorMap map = new FactorMap();
        assertTrue(map.getValue("nonExistent").isEmpty());
    }

    @Test
    void toValueMap_containsAllEffectiveValues_includingFallbacks() {
        FactorMap map = new FactorMap();
        map.put("a", FactorResult.success(1L));
        map.put("b", FactorResult.failure(0L, "error"));

        Map<String, Object> valueMap = map.toValueMap();
        assertEquals(2, valueMap.size());
        assertEquals(1L, valueMap.get("a"));
        assertEquals(0L, valueMap.get("b"), "Fallback value should appear in toValueMap");
    }

    @Test
    void toValueMap_isImmutable() {
        FactorMap map = new FactorMap();
        map.put("x", FactorResult.success(1L));
        Map<String, Object> valueMap = map.toValueMap();

        assertThrows(UnsupportedOperationException.class,
                () -> valueMap.put("y", 2L),
                "toValueMap() should return an unmodifiable map");
    }

    @Test
    void contains_returnsFalse_beforePut() {
        FactorMap map = new FactorMap();
        assertFalse(map.contains("x"));
    }

    @Test
    void contains_returnsTrue_afterPut() {
        FactorMap map = new FactorMap();
        map.put("x", FactorResult.success("val"));
        assertTrue(map.contains("x"));
    }

    @Test
    void size_reflectsNumberOfPuts() {
        FactorMap map = new FactorMap();
        assertEquals(0, map.size());
        map.put("a", FactorResult.success(1L));
        assertEquals(1, map.size());
        map.put("b", FactorResult.success(2L));
        assertEquals(2, map.size());
    }

    @Test
    void factorResult_effectiveValue_returnsValueOnSuccess() {
        FactorResult result = FactorResult.success(42L);
        assertTrue(result.isSuccess());
        assertEquals(42L, result.effectiveValue());
        assertEquals(42L, result.getValue());
    }

    @Test
    void factorResult_effectiveValue_returnsFallbackOnFailure() {
        FactorResult result = FactorResult.failure(0L, "connection refused");
        assertFalse(result.isSuccess());
        assertEquals(0L, result.effectiveValue(), "effectiveValue() must return fallback on failure");
        assertNull(result.getValue(), "value must be null on failure");
        assertEquals("connection refused", result.getErrorMessage());
    }

    @Test
    void riskLevel_max_returnsHigherLevel() {
        assertEquals(RiskLevel.BLOCK, RiskLevel.PASS.max(RiskLevel.BLOCK));
        assertEquals(RiskLevel.BLOCK, RiskLevel.BLOCK.max(RiskLevel.REVIEW));
        assertEquals(RiskLevel.REVIEW, RiskLevel.PASS.max(RiskLevel.REVIEW));
        assertEquals(RiskLevel.PASS, RiskLevel.PASS.max(RiskLevel.PASS));
    }

    @Test
    void policyResult_noMatch_returnsFalseAndPass() {
        PolicyResult result = PolicyResult.noMatch();
        assertFalse(result.isMatched());
        assertEquals(RiskLevel.PASS, result.getSuggestedLevel());
        assertTrue(result.getPunishmentIds().isEmpty());
    }
}
