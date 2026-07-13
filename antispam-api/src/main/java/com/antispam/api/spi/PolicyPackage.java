package com.antispam.api.spi;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.PolicyResult;

import java.util.List;

/**
 * 套餐 SPI。描述一组风险规则，以及规则命中后的处罚动作。
 *
 * <p>实现类需注册为 Spring Bean（@Component）以被 PolicyRegistry 自动发现。
 *
 * <p>套餐与业务种类绑定：RiskEngine 根据 RiskContext.businessType 路由到对应套餐列表。
 */
public interface PolicyPackage {

    /** 套餐唯一 ID，例如 "loginRiskPolicy" */
    String policyId();

    /**
     * 绑定的业务种类，对应 RiskContext.businessType。
     * 同一 businessType 可有多个套餐，RiskEngine 会逐一评估并取最高风险级别。
     */
    String businessType();

    /**
     * 此套餐需要的因子 ID 列表。
     * GraphExecutor 将确保这些因子在 evaluate() 调用前全部完成（或超时降级）。
     */
    List<String> requiredFactors();

    /**
     * 基于已计算的因子值评估是否命中，并给出风险级别和处罚动作。
     *
     * @param facts 包含所有已计算因子结果的 FactorMap（线程安全，只读）
     * @return 评估结果；未命中时返回 {@link PolicyResult#noMatch()}
     */
    PolicyResult evaluate(FactorMap facts);
}
