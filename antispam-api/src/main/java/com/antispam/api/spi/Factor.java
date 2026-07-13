package com.antispam.api.spi;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.FactorResult;
import com.antispam.api.model.RiskContext;

import java.util.Collections;
import java.util.List;

/**
 * 因子 SPI。每个因子是响应式 DAG 中的一个节点。
 *
 * <p>实现类需注册为 Spring Bean（@Component）以被 FactorRegistry 自动发现。
 *
 * <p>依赖关系（{@link #dependencies()}）构成 DAG 的有向边：
 * GraphExecutor 保证上游因子全部完成后，才将其结果注入 upstream 并执行本因子。
 */
public interface Factor {

    /**
     * 因子唯一 ID，全局唯一。
     * 命名约定：驼峰，描述计算内容，如 "loginFreq1Min"、"deviceCount24h"。
     */
    String factorId();

    /**
     * 本因子依赖的上游因子 ID 列表。
     * 空列表表示无依赖，图执行开始时立即并发执行。
     * 注意：不得形成循环依赖，否则 GraphExecutor 构建图时抛出 IllegalStateException。
     */
    default List<String> dependencies() {
        return Collections.emptyList();
    }

    /**
     * 计算因子值。
     *
     * @param ctx      当前请求上下文（包含 userId、deviceId、ip 等）
     * @param upstream 已完成的上游因子结果（dependencies() 中列出的所有因子均已完成）
     * @return 计算结果，不得返回 null。失败时使用 {@link FactorResult#failure(Object, String)} 返回带 fallback 的结果。
     */
    FactorResult compute(RiskContext ctx, FactorMap upstream);
}
