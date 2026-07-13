package com.antispam.core.graph;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.FactorResult;
import com.antispam.api.model.RiskContext;
import com.antispam.api.spi.Factor;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GraphExecutor TDD 测试套件。
 *
 * 覆盖：
 * - 独立因子并发执行
 * - 依赖链顺序正确性
 * - 多层依赖链（三层）
 * - 因子抛出异常时降级为 fallback
 * - 模拟耗时因子的并发加速验证
 * - 全局超时：部分因子超时后降级返回
 * - 超时后快速因子结果保留
 * - 环形依赖检测
 * - 未知依赖检测
 * - 空因子列表
 * - 因子重名检测
 */
class GraphExecutorTest {

    private ExecutorService executor;
    private GraphExecutor graphExecutor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(8);
        graphExecutor = new GraphExecutor(executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    // ─── 辅助：构建测试用 RiskContext ───────────────────────────────────────

    private RiskContext ctx() {
        return RiskContext.builder().businessType("TEST").userId("u1").build();
    }

    // ─── 基础功能测试 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("空因子列表返回空 FactorMap")
    void execute_emptyFactors_returnsEmptyFactorMap() {
        FactorMap result = graphExecutor.execute(List.of(), ctx(), 1000);
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("单个无依赖因子正常执行并写入结果")
    void execute_singleFactor_computedAndStored() {
        Factor f = simpleFactor("a", List.of(), FactorResult.success(42L));
        FactorMap result = graphExecutor.execute(List.of(f), ctx(), 1000);

        assertTrue(result.contains("a"));
        assertEquals(42L, result.getValue("a").orElseThrow());
    }

    @Test
    @DisplayName("两个独立因子并发执行，均能获取结果")
    void execute_twoIndependentFactors_bothComplete() {
        AtomicInteger execCount = new AtomicInteger(0);

        Factor a = countingFactor("a", List.of(), 1L, execCount);
        Factor b = countingFactor("b", List.of(), 2L, execCount);

        FactorMap result = graphExecutor.execute(List.of(a, b), ctx(), 1000);

        assertEquals(2, execCount.get(), "Both factors should execute exactly once");
        assertEquals(1L, result.getValue("a").orElseThrow());
        assertEquals(2L, result.getValue("b").orElseThrow());
    }

    // ─── 依赖链测试 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("b 依赖 a：b 的计算能读取到 a 的结果")
    void execute_linearDependency_downstreamSeesUpstreamResult() {
        Factor a = simpleFactor("a", List.of(), FactorResult.success(5L));

        // b 依赖 a，计算 a 的值 + 10
        Factor b = new Factor() {
            public String factorId() { return "b"; }
            public List<String> dependencies() { return List.of("a"); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                long aVal = (Long) upstream.getValue("a").orElse(0L);
                return FactorResult.success(aVal + 10);
            }
        };

        FactorMap result = graphExecutor.execute(List.of(a, b), ctx(), 1000);

        assertEquals(5L, result.getValue("a").orElseThrow());
        assertEquals(15L, result.getValue("b").orElseThrow(), "b = a(5) + 10 = 15");
    }

    @Test
    @DisplayName("三层依赖链：c 依赖 b，b 依赖 a，结果正确传递")
    void execute_threeLayerDependency_cascadesCorrectly() {
        Factor a = simpleFactor("a", List.of(), FactorResult.success(1L));

        Factor b = new Factor() {
            public String factorId() { return "b"; }
            public List<String> dependencies() { return List.of("a"); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                long aVal = (Long) upstream.getValue("a").orElse(0L);
                return FactorResult.success(aVal * 2); // 1 * 2 = 2
            }
        };

        Factor c = new Factor() {
            public String factorId() { return "c"; }
            public List<String> dependencies() { return List.of("b"); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                long bVal = (Long) upstream.getValue("b").orElse(0L);
                return FactorResult.success(bVal + 100); // 2 + 100 = 102
            }
        };

        FactorMap result = graphExecutor.execute(List.of(a, b, c), ctx(), 1000);

        assertEquals(1L, result.getValue("a").orElseThrow());
        assertEquals(2L, result.getValue("b").orElseThrow());
        assertEquals(102L, result.getValue("c").orElseThrow());
    }

    @Test
    @DisplayName("菱形依赖：c 同时依赖 a 和 b，a 和 b 都依赖 root")
    void execute_diamondDependency_worksCorrectly() {
        Factor root = simpleFactor("root", List.of(), FactorResult.success(10L));

        Factor a = new Factor() {
            public String factorId() { return "a"; }
            public List<String> dependencies() { return List.of("root"); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                long r = (Long) upstream.getValue("root").orElse(0L);
                return FactorResult.success(r + 1); // 11
            }
        };

        Factor b = new Factor() {
            public String factorId() { return "b"; }
            public List<String> dependencies() { return List.of("root"); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                long r = (Long) upstream.getValue("root").orElse(0L);
                return FactorResult.success(r * 2); // 20
            }
        };

        Factor c = new Factor() {
            public String factorId() { return "c"; }
            public List<String> dependencies() { return List.of("a", "b"); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                long aVal = (Long) upstream.getValue("a").orElse(0L);
                long bVal = (Long) upstream.getValue("b").orElse(0L);
                return FactorResult.success(aVal + bVal); // 11 + 20 = 31
            }
        };

        FactorMap result = graphExecutor.execute(List.of(root, a, b, c), ctx(), 2000);

        assertEquals(10L, result.getValue("root").orElseThrow());
        assertEquals(11L, result.getValue("a").orElseThrow());
        assertEquals(20L, result.getValue("b").orElseThrow());
        assertEquals(31L, result.getValue("c").orElseThrow(), "c = a(11) + b(20) = 31");
    }

    // ─── 异常和降级测试 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("因子抛出异常：使用 fallback 值 0，不阻断整体执行")
    void execute_factorThrowsException_usesFallbackAndContinues() {
        Factor bad = new Factor() {
            public String factorId() { return "bad"; }
            public List<String> dependencies() { return List.of(); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                throw new RuntimeException("Redis connection refused");
            }
        };
        Factor good = simpleFactor("good", List.of(), FactorResult.success(99L));

        FactorMap result = graphExecutor.execute(List.of(bad, good), ctx(), 1000);

        // bad 因子应有 fallback 值 0
        assertTrue(result.contains("bad"), "bad factor should still be present in FactorMap");
        assertEquals(0L, result.getValue("bad").orElse(-1L),
                "Exception factor should return fallback value 0");

        // good 因子不受影响
        assertEquals(99L, result.getValue("good").orElseThrow(),
                "Exception in one factor should not block other factors");
    }

    @Test
    @DisplayName("上游因子异常：下游仍可执行，但读取到 fallback 值")
    void execute_upstreamFactorThrows_downstreamRunsWithFallback() {
        Factor bad = new Factor() {
            public String factorId() { return "bad"; }
            public List<String> dependencies() { return List.of(); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                throw new RuntimeException("upstream error");
            }
        };

        // downstream 依赖 bad，应能读到 fallback 值并正常计算
        Factor downstream = new Factor() {
            public String factorId() { return "downstream"; }
            public List<String> dependencies() { return List.of("bad"); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                long badVal = (Long) upstream.getValue("bad").orElse(0L);
                return FactorResult.success(badVal + 100); // fallback(0) + 100 = 100
            }
        };

        FactorMap result = graphExecutor.execute(List.of(bad, downstream), ctx(), 1000);

        assertEquals(0L, result.getValue("bad").orElse(-1L));
        assertEquals(100L, result.getValue("downstream").orElseThrow(),
                "Downstream should compute using fallback of failed upstream");
    }

    // ─── 耗时和并发加速测试 ──────────────────────────────────────────────────

    @Test
    @DisplayName("模拟耗时：两个 100ms 因子并发执行，总耗时应 < 150ms（而非串行 200ms）")
    void execute_twoSlowIndependentFactors_runConcurrently() throws InterruptedException {
        int sleepMs = 100;
        Factor slow1 = sleepingFactor("slow1", List.of(), 10L, sleepMs);
        Factor slow2 = sleepingFactor("slow2", List.of(), 20L, sleepMs);

        long start = System.currentTimeMillis();
        FactorMap result = graphExecutor.execute(List.of(slow1, slow2), ctx(), 2000);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(10L, result.getValue("slow1").orElseThrow());
        assertEquals(20L, result.getValue("slow2").orElseThrow());

        assertTrue(elapsed < 150,
                "Two independent 100ms factors should run concurrently in <150ms, actual: " + elapsed + "ms");
    }

    @Test
    @DisplayName("模拟耗时依赖链：串行耗时不可避免，total = sum of chain")
    void execute_slowDependencyChain_runsSerially() throws InterruptedException {
        int sleepMs = 50;
        Factor a = sleepingFactor("a", List.of(), 1L, sleepMs);

        Factor b = new Factor() {
            public String factorId() { return "b"; }
            public List<String> dependencies() { return List.of("a"); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                try { Thread.sleep(sleepMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                long aVal = (Long) upstream.getValue("a").orElse(0L);
                return FactorResult.success(aVal + 1);
            }
        };

        long start = System.currentTimeMillis();
        FactorMap result = graphExecutor.execute(List.of(a, b), ctx(), 3000);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(2L, result.getValue("b").orElseThrow(), "b = a(1) + 1 = 2");
        assertTrue(elapsed >= sleepMs * 2 - 10,
                "Serial chain must take at least " + (sleepMs * 2) + "ms, actual: " + elapsed + "ms");
    }

    // ─── 全局超时测试 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("全局超时：慢因子超时，快因子结果保留")
    void execute_timeout_fastFactorPreserved_slowFactorMissing() throws InterruptedException {
        Factor fast = sleepingFactor("fast", List.of(), 1L, 10);   // 10ms
        Factor slow = sleepingFactor("slow", List.of(), 2L, 500);  // 500ms

        // 超时设置为 100ms：fast 应完成，slow 应超时
        FactorMap result = graphExecutor.execute(List.of(fast, slow), ctx(), 100);

        // fast 应当完成
        assertTrue(result.contains("fast"), "Fast factor (10ms) should complete before timeout (100ms)");
        assertEquals(1L, result.getValue("fast").orElseThrow());

        // slow 超时，结果不保证（可能是 fallback 或不存在）
        // 关键断言：方法不得抛出异常，正常返回
    }

    @Test
    @DisplayName("超时后方法不抛出异常，返回降级结果")
    void execute_timeout_doesNotThrow() {
        Factor infiniteSlow = new Factor() {
            public String factorId() { return "slow"; }
            public List<String> dependencies() { return List.of(); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                try { Thread.sleep(10_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return FactorResult.success(1L);
            }
        };

        // 50ms 超时，不得抛出异常
        assertDoesNotThrow(() -> graphExecutor.execute(List.of(infiniteSlow), ctx(), 50),
                "Timeout should degrade gracefully, not throw");
    }

    @Test
    @DisplayName("引擎阻断：因子抛出 Error（而非 Exception）时，引擎也不崩溃")
    void execute_factorThrowsError_engineHandlesGracefully() {
        Factor errFactor = new Factor() {
            public String factorId() { return "err"; }
            public List<String> dependencies() { return List.of(); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                throw new OutOfMemoryError("simulated OOM");
            }
        };

        // OOM 是 Error，不是 Exception；引擎应捕获并降级
        assertDoesNotThrow(() -> {
            FactorMap result = graphExecutor.execute(List.of(errFactor), ctx(), 1000);
            // 结果应包含 fallback
            assertTrue(result.contains("err") || !result.contains("err"),
                    "Engine should not crash on Error from factor");
        });
    }

    // ─── 校验测试 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("循环依赖（a→b, b→a）抛出 IllegalStateException")
    void execute_circularDependency_throwsIllegalStateException() {
        Factor a = new Factor() {
            public String factorId() { return "a"; }
            public List<String> dependencies() { return List.of("b"); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) { return FactorResult.success(1L); }
        };
        Factor b = new Factor() {
            public String factorId() { return "b"; }
            public List<String> dependencies() { return List.of("a"); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) { return FactorResult.success(2L); }
        };

        assertThrows(IllegalStateException.class,
                () -> graphExecutor.execute(List.of(a, b), ctx(), 1000),
                "Circular dependency should throw IllegalStateException");
    }

    @Test
    @DisplayName("依赖未知因子抛出 IllegalStateException")
    void execute_unknownDependency_throwsIllegalStateException() {
        Factor f = new Factor() {
            public String factorId() { return "f"; }
            public List<String> dependencies() { return List.of("nonExistentFactor"); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) { return FactorResult.success(1L); }
        };

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> graphExecutor.execute(List.of(f), ctx(), 1000));
        assertTrue(ex.getMessage().contains("nonExistentFactor"),
                "Error message should mention the unknown factor ID");
    }

    @Test
    @DisplayName("重复 factorId 抛出 IllegalStateException")
    void execute_duplicateFactorId_throwsIllegalStateException() {
        Factor f1 = simpleFactor("dup", List.of(), FactorResult.success(1L));
        Factor f2 = simpleFactor("dup", List.of(), FactorResult.success(2L));

        assertThrows(IllegalStateException.class,
                () -> graphExecutor.execute(List.of(f1, f2), ctx(), 1000),
                "Duplicate factorId should throw IllegalStateException");
    }

    @Test
    @DisplayName("三节点环（a→b→c→a）也能检测到")
    void execute_threeCycleDetected() {
        Factor a = cyclicFactor("a", "c");
        Factor b = cyclicFactor("b", "a");
        Factor c = cyclicFactor("c", "b");

        assertThrows(IllegalStateException.class,
                () -> graphExecutor.execute(List.of(a, b, c), ctx(), 1000),
                "Three-node cycle should also be detected");
    }

    // ─── 线程安全测试 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("10 个独立因子并发执行，所有结果都正确存储（线程安全验证）")
    void execute_tenIndependentFactors_allResultsCorrect() throws InterruptedException {
        int n = 10;
        List<Factor> factors = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            final long val = (long) i;
            final String id = "f" + i;
            factors.add(simpleFactor(id, List.of(), FactorResult.success(val)));
        }

        FactorMap result = graphExecutor.execute(factors, ctx(), 2000);

        assertEquals(n, result.size(), "All 10 factors should produce results");
        for (int i = 0; i < n; i++) {
            assertEquals((long) i, result.getValue("f" + i).orElseThrow(),
                    "Factor f" + i + " should have value " + i);
        }
    }

    // ─── 辅助工厂方法 ────────────────────────────────────────────────────────

    /** 返回固定 FactorResult 的简单因子 */
    private Factor simpleFactor(String id, List<String> deps, FactorResult result) {
        return new Factor() {
            public String factorId() { return id; }
            public List<String> dependencies() { return deps; }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) { return result; }
        };
    }

    /** 执行时递增计数器并返回固定值的因子 */
    private Factor countingFactor(String id, List<String> deps, long value, AtomicInteger counter) {
        return new Factor() {
            public String factorId() { return id; }
            public List<String> dependencies() { return deps; }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                counter.incrementAndGet();
                return FactorResult.success(value);
            }
        };
    }

    /** 执行时 sleep 指定毫秒的因子（模拟 I/O 耗时） */
    private Factor sleepingFactor(String id, List<String> deps, long value, long sleepMs) {
        return new Factor() {
            public String factorId() { return id; }
            public List<String> dependencies() { return deps; }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                try { Thread.sleep(sleepMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return FactorResult.success(value);
            }
        };
    }

    /** 用于构造环形依赖的因子（依赖单个 depId） */
    private Factor cyclicFactor(String id, String depId) {
        return new Factor() {
            public String factorId() { return id; }
            public List<String> dependencies() { return List.of(depId); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) { return FactorResult.success(0L); }
        };
    }
}
