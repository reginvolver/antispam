package com.antispam.core.graph;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.FactorResult;
import com.antispam.api.model.RiskContext;
import com.antispam.api.spi.Factor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;

/**
 * 响应式异步图执行器（Reactive Async Graph / RAG）。
 *
 * <p>执行算法：Kahn's Algorithm（基于入度的拓扑驱动）
 * <ol>
 *   <li>构建 DAG：根据 Factor.dependencies() 建立有向边，计算入度</li>
 *   <li>校验无环：拓扑遍历，若有节点无法处理则检测到环</li>
 *   <li>启动：将入度=0 的节点立即并发提交到线程池</li>
 *   <li>传播：每个节点完成后，递减下游入度，入度变为0则立即提交下游</li>
 *   <li>超时：全局 timeoutMs 内等待所有 Future 完成；超时后取消未完成节点，
 *       返回当前 FactorMap（降级）</li>
 *   <li>异常：单个节点异常时捕获并写入 fallback 值，不阻断下游执行</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
public class GraphExecutor {

    private final ExecutorService threadPool;

    /**
     * 执行因子 DAG。
     *
     * @param factors   需要执行的因子列表（不得为 null）
     * @param ctx       请求上下文
     * @param timeoutMs 全局超时毫秒数；超时后降级返回已有结果
     * @return 包含所有已完成因子结果的 FactorMap（超时时为部分结果）
     * @throws IllegalStateException 如果因子依赖形成循环，或依赖了不存在的因子
     */
    public FactorMap execute(List<Factor> factors, RiskContext ctx, long timeoutMs) {
        if (factors == null || factors.isEmpty()) {
            return new FactorMap();
        }

        // Step 1: 建立 factorId -> GraphNode 映射（初始入度全为 0）
        Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
        for (Factor factor : factors) {
            String id = factor.factorId();
            if (nodeMap.containsKey(id)) {
                throw new IllegalStateException("Duplicate factorId in execution list: " + id);
            }
            nodeMap.put(id, new GraphNode(factor, 0));
        }

        // Step 2: 根据 dependencies() 建有向边，计算入度
        for (Factor factor : factors) {
            GraphNode downstream = nodeMap.get(factor.factorId());
            for (String depId : factor.dependencies()) {
                GraphNode upstream = nodeMap.get(depId);
                if (upstream == null) {
                    throw new IllegalStateException(
                            "Factor [" + factor.factorId() + "] depends on unknown factor [" + depId + "]");
                }
                upstream.addDownstream(downstream);
                downstream.getInDegree().incrementAndGet();
            }
        }

        // Step 3: 校验无环（Kahn's topological sort）
        validateNoCycle(nodeMap, factors.size());

        // Step 4: 执行图
        FactorMap factorMap = new FactorMap();
        // 用线程安全集合收集所有提交的 Future，以便全局等待
        List<CompletableFuture<Void>> allFutures = Collections.synchronizedList(new ArrayList<>());

        // 将入度为 0 的节点立即提交
        for (GraphNode node : nodeMap.values()) {
            if (node.getInDegree().get() == 0) {
                CompletableFuture<Void> f = submitNode(node, ctx, factorMap, allFutures);
                node.setFuture(f);
                allFutures.add(f);
            }
        }

        // Step 5: 等待所有节点完成或全局超时
        waitForCompletion(allFutures, timeoutMs, ctx);

        return factorMap;
    }

    /**
     * 异步提交单个节点到线程池执行。
     * 节点完成后自动触发下游节点。
     */
    private CompletableFuture<Void> submitNode(
            GraphNode node,
            RiskContext ctx,
            FactorMap factorMap,
            List<CompletableFuture<Void>> allFutures) {

        return CompletableFuture.runAsync(() -> {
            Factor factor = node.getFactor();
            FactorResult result;
            try {
                result = factor.compute(ctx, factorMap);
                if (result == null) {
                    log.warn("[GraphExecutor] Factor [{}] returned null, using fallback 0",
                            factor.factorId());
                    result = FactorResult.failure(0L, "factor returned null");
                }
            } catch (Exception e) {
                log.warn("[GraphExecutor] Factor [{}] threw exception: {}, using fallback 0",
                        factor.factorId(), e.getMessage());
                result = FactorResult.failure(0L, e.getMessage());
            }

            // 写入结果（ConcurrentHashMap，线程安全）
            factorMap.put(factor.factorId(), result);

            // 通知所有下游节点：入度减 1，如果变为 0 则立即提交
            for (GraphNode downstream : node.getDownstreams()) {
                int remaining = downstream.decrementInDegree();
                if (remaining == 0) {
                    CompletableFuture<Void> df =
                            submitNode(downstream, ctx, factorMap, allFutures);
                    downstream.setFuture(df);
                    allFutures.add(df);
                }
            }
        }, threadPool);
    }

    /**
     * 等待所有 Future 完成，或全局超时后取消并降级。
     */
    private void waitForCompletion(List<CompletableFuture<Void>> allFutures,
                                    long timeoutMs, RiskContext ctx) {
        try {
            // 注意：allFutures 可能在等待过程中被动态追加（下游节点），
            // 所以不能在开始前固定快照；使用轮询方式等待。
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                // 给当前快照 all-of 一个短暂等待机会
                List<CompletableFuture<Void>> snapshot;
                synchronized (allFutures) {
                    snapshot = new ArrayList<>(allFutures);
                }
                try {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) break;
                    CompletableFuture.allOf(snapshot.toArray(new CompletableFuture[0]))
                            .get(Math.min(remaining, 20), TimeUnit.MILLISECONDS);
                    // 所有已知节点完成，再检查一次是否有新提交的下游
                    synchronized (allFutures) {
                        if (allFutures.size() == snapshot.size()) {
                            // 没有新节点，真正完成
                            return;
                        }
                    }
                } catch (TimeoutException e) {
                    // 继续循环检查直到全局 deadline
                }
            }
            // 超时，取消所有未完成节点
            log.warn("[GraphExecutor] Timeout {}ms for userId={}, returning partial results",
                    timeoutMs, ctx != null ? ctx.getUserId() : "?");
            synchronized (allFutures) {
                allFutures.forEach(f -> f.cancel(true));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[GraphExecutor] Interrupted during execution");
        } catch (ExecutionException e) {
            // 单节点异常已在 submitNode 内处理，不应到达这里
            log.error("[GraphExecutor] Unexpected execution error", e.getCause());
        }
    }

    /**
     * 使用 Kahn's Algorithm 校验无环。
     * 拓扑排序后若仍有节点未处理，说明存在环形依赖。
     */
    private void validateNoCycle(Map<String, GraphNode> nodeMap, int totalFactors) {
        // 构建入度副本（不修改原 GraphNode 的 AtomicInteger）
        Map<String, Integer> inDegreeCopy = new HashMap<>();
        nodeMap.forEach((id, node) -> inDegreeCopy.put(id, node.getInDegree().get()));

        Queue<String> queue = new LinkedList<>();
        inDegreeCopy.forEach((id, deg) -> {
            if (deg == 0) queue.add(id);
        });

        int processed = 0;
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            processed++;
            for (GraphNode downstream : nodeMap.get(nodeId).getDownstreams()) {
                String downId = downstream.getFactor().factorId();
                int newDeg = inDegreeCopy.merge(downId, -1, Integer::sum);
                if (newDeg == 0) {
                    queue.add(downId);
                }
            }
        }

        if (processed < totalFactors) {
            throw new IllegalStateException(
                    "[GraphExecutor] Circular dependency detected among factors. " +
                    "Processed " + processed + " of " + totalFactors + " nodes.");
        }
    }
}
