package com.antispam.api.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 因子计算结果聚合容器，线程安全。
 *
 * GraphExecutor 在执行过程中持续写入；
 * 规则引擎（Aviator）在所有因子完成（或超时降级）后读取。
 */
public class FactorMap {

    private final ConcurrentHashMap<String, FactorResult> results = new ConcurrentHashMap<>();

    public void put(String factorId, FactorResult result) {
        results.put(factorId, result);
    }

    public Optional<FactorResult> getResult(String factorId) {
        return Optional.ofNullable(results.get(factorId));
    }

    /**
     * 返回指定因子的有效值（成功→value，失败→fallbackValue）。
     */
    public Optional<Object> getValue(String factorId) {
        return getResult(factorId).map(FactorResult::effectiveValue);
    }

    /**
     * 将所有因子的有效值展平为 Map，供 Aviator 直接使用作为变量上下文。
     * Key = factorId，Value = effectiveValue
     */
    public Map<String, Object> toValueMap() {
        Map<String, Object> map = new HashMap<>();
        results.forEach((k, v) -> map.put(k, v.effectiveValue()));
        return Collections.unmodifiableMap(map);
    }

    public boolean contains(String factorId) {
        return results.containsKey(factorId);
    }

    public int size() {
        return results.size();
    }
}
