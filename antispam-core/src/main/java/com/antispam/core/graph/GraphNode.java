package com.antispam.core.graph;

import com.antispam.api.spi.Factor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DAG 中的一个执行节点，对应一个 Factor。
 * 包级私有，仅供 GraphExecutor 使用。
 */
@Getter
class GraphNode {
    private final Factor factor;
    /** 该节点的入度（还需等待多少个上游节点完成） */
    private final AtomicInteger inDegree;
    /** 以该节点为上游的下游节点列表 */
    private final List<GraphNode> downstreams = new ArrayList<>();
    /** 该节点的异步执行 Future，由 GraphExecutor 在提交时赋值 */
    private volatile CompletableFuture<Void> future;

    GraphNode(Factor factor, int initialInDegree) {
        this.factor = factor;
        this.inDegree = new AtomicInteger(initialInDegree);
    }

    void setFuture(CompletableFuture<Void> future) {
        this.future = future;
    }

    void addDownstream(GraphNode downstream) {
        downstreams.add(downstream);
    }

    /**
     * 上游节点完成时调用，将入度原子减 1。
     * @return 减 1 后的入度值（0 表示所有上游均已完成，可以立即提交执行）
     */
    int decrementInDegree() {
        return inDegree.decrementAndGet();
    }
}
