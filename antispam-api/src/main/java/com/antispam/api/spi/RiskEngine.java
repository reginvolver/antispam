package com.antispam.api.spi;

import com.antispam.api.model.RiskContext;
import com.antispam.api.model.RiskResponse;

/**
 * 风控引擎主入口 SPI。调用方通过此接口发起风险评估请求。
 *
 * <p>该方法是同步调用（阻塞直到返回），但内部因子 DAG 执行是并发的，
 * 总延迟受全局 timeout-ms 配置约束。
 */
public interface RiskEngine {

    /**
     * 对给定上下文执行完整的风险评估流程：
     * 因子计算 → 套餐规则评估 → 处罚执行 → 返回结果。
     *
     * @param context 请求上下文，不得为 null
     * @return 风险评估结果，不得返回 null；超时降级时 timedOut=true
     */
    RiskResponse evaluate(RiskContext context);
}
