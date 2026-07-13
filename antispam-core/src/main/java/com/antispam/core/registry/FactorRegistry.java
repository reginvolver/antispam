package com.antispam.core.registry;

import com.antispam.api.spi.Factor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 因子注册中心。收集所有 Spring 容器中的 Factor Bean，并按 factorId 索引。
 * 通过构造注入自动发现所有实现了 Factor 接口的 Bean。
 */
@Slf4j
@Component
public class FactorRegistry implements InitializingBean {

    private final List<Factor> allFactors;
    private Map<String, Factor> factorMap = Collections.emptyMap();

    public FactorRegistry(List<Factor> allFactors) {
        this.allFactors = allFactors == null ? Collections.emptyList() : allFactors;
    }

    @Override
    public void afterPropertiesSet() {
        Map<String, Factor> map = new HashMap<>();
        for (Factor factor : allFactors) {
            String id = factor.factorId();
            if (map.containsKey(id)) {
                throw new IllegalStateException("Duplicate factorId detected: " + id);
            }
            map.put(id, factor);
        }
        this.factorMap = Collections.unmodifiableMap(map);
        log.info("[FactorRegistry] Registered {} factors: {}", map.size(), map.keySet());
    }

    public Optional<Factor> getFactorById(String factorId) {
        return Optional.ofNullable(factorMap.get(factorId));
    }

    public List<Factor> getFactorsByIds(List<String> factorIds) {
        return factorIds.stream()
                .map(id -> getFactorById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown factorId: " + id)))
                .collect(Collectors.toList());
    }

    public List<Factor> getAll() {
        return allFactors;
    }
}
