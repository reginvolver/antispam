package com.antispam.policy.registry;

import com.antispam.api.spi.PolicyPackage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 套餐注册中心。收集所有 Spring 容器中的 PolicyPackage Bean，按 businessType 索引。
 */
@Slf4j
@Component
public class PolicyRegistry implements InitializingBean {

    private final List<PolicyPackage> allPolicies;
    private Map<String, List<PolicyPackage>> byBusinessType = Collections.emptyMap();

    public PolicyRegistry(List<PolicyPackage> allPolicies) {
        this.allPolicies = allPolicies == null ? Collections.emptyList() : allPolicies;
    }

    @Override
    public void afterPropertiesSet() {
        Map<String, List<PolicyPackage>> map = new HashMap<>();
        for (PolicyPackage policy : allPolicies) {
            map.computeIfAbsent(policy.businessType(), k -> new ArrayList<>()).add(policy);
        }
        this.byBusinessType = Collections.unmodifiableMap(map);
        log.info("[PolicyRegistry] Registered {} policies across {} business types: {}",
                allPolicies.size(), map.size(), map.keySet());
    }

    /**
     * 获取指定业务种类下的所有套餐（按注册顺序）。
     */
    public List<PolicyPackage> getByBusinessType(String businessType) {
        return byBusinessType.getOrDefault(businessType, Collections.emptyList());
    }
}
