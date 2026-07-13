package com.antispam.core.graph;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.FactorResult;
import com.antispam.api.model.RiskContext;
import com.antispam.api.spi.Factor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

/**
 * GraphExecutor DAG 引擎性能压测与基准测试
 */
class GraphExecutorBenchmark {

    @Test
    @Disabled("手动运行的压测，单元测试阶段默认跳过")
    void runBenchmark() throws Exception {
        System.out.println("==================================================");
        System.out.println("开始 GraphExecutor DAG 调度引擎性能压测...");
        System.out.println("==================================================");

        // 1. 初始化线程池与执行器
        int coreCpu = Runtime.getRuntime().availableProcessors();
        System.out.println("CPU 核心数: " + coreCpu);
        int threadPoolSize = coreCpu * 4;
        System.out.println("调度执行池线程数: " + threadPoolSize);

        ExecutorService threadPool = new ThreadPoolExecutor(
                threadPoolSize, threadPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10000),
                r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    return t;
                }
        );
        GraphExecutor executor = new GraphExecutor(threadPool);

        // 2. 构造包含 5 个因子的经典钻石/树形依赖图
        // a, b (无依赖) -> c (依赖 a, b) -> d (依赖 c), e (依赖 c)
        Factor a = createMockFactor("a", List.of(), 5L);
        Factor b = createMockFactor("b", List.of(), 10L);
        Factor c = new Factor() {
            @Override
            public String factorId() { return "c"; }
            @Override
            public List<String> dependencies() { return List.of("a", "b"); }
            @Override
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                long aVal = (Long) upstream.getValue("a").orElse(0L);
                long bVal = (Long) upstream.getValue("b").orElse(0L);
                return FactorResult.success(aVal + bVal); // 5 + 10 = 15
            }
        };
        Factor d = new Factor() {
            @Override
            public String factorId() { return "d"; }
            @Override
            public List<String> dependencies() { return List.of("c"); }
            @Override
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                long cVal = (Long) upstream.getValue("c").orElse(0L);
                return FactorResult.success(cVal * 2); // 30
            }
        };
        Factor e = new Factor() {
            @Override
            public String factorId() { return "e"; }
            @Override
            public List<String> dependencies() { return List.of("c"); }
            @Override
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                long cVal = (Long) upstream.getValue("c").orElse(0L);
                return FactorResult.success(cVal + 100); // 115
            }
        };

        List<Factor> factorList = List.of(a, b, c, d, e);
        RiskContext ctx = RiskContext.builder()
                .userId("benchmark_user")
                .businessType("ECOMMERCE")
                .build();

        // 3. 预热阶段 (Warm up) - 2 秒，让 JIT 编译器充分优化
        System.out.println("正在进行 JIT 预热 (2 秒)...");
        long warmUpEnd = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < warmUpEnd) {
            executor.execute(factorList, ctx, 500);
        }
        System.out.println("预热完成。");

        // 4. 正式压测阶段 (Measurement) - 5 秒
        int concurrency = 32; // 压测并发客户端线程数
        System.out.println("开始正式压测，并发客户端线程数: " + concurrency + "，持续 5 秒...");

        LongAdder successCounter = new LongAdder();
        List<Long> allLatencies = new CopyOnWriteArrayList<>();
        ExecutorService clientPool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch stopLatch = new CountDownLatch(concurrency);

        long testDurationMs = 5000;
        long testEnd = System.currentTimeMillis() + testDurationMs;

        for (int i = 0; i < concurrency; i++) {
            clientPool.submit(() -> {
                try {
                    startLatch.await();
                    while (System.currentTimeMillis() < testEnd) {
                        long start = System.nanoTime();
                        FactorMap res = executor.execute(factorList, ctx, 500);
                        long latency = System.nanoTime() - start;

                        // 校验结果准确性，确保压测没有跑错
                        if (res.getValue("d").orElse(0L).equals(30L) &&
                                res.getValue("e").orElse(0L).equals(115L)) {
                            successCounter.increment();
                            // 为了避免频繁写入 List 影响压测本身性能，我们进行采样记录耗时 (例如每 20 次记录一次)
                            if (ThreadLocalRandom.current().nextInt(20) == 0) {
                                allLatencies.add(latency / 1000); // 转为微秒 (us)
                            }
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    stopLatch.countDown();
                }
            });
        }

        long actualStart = System.currentTimeMillis();
        startLatch.countDown(); // 所有人开始冲锋
        stopLatch.await();
        long actualDurationMs = System.currentTimeMillis() - actualStart;

        // 5. 整理输出统计结果
        long totalRequests = successCounter.sum();
        double qps = (double) totalRequests / (actualDurationMs / 1000.0);

        List<Long> sortedLatencies = new ArrayList<>(allLatencies);
        Collections.sort(sortedLatencies);

        double avgLatencyUs = 0;
        long p95LatencyUs = 0;
        long p99LatencyUs = 0;
        if (!sortedLatencies.isEmpty()) {
            avgLatencyUs = sortedLatencies.stream().mapToLong(x -> x).average().orElse(0);
            p95LatencyUs = sortedLatencies.get((int) (sortedLatencies.size() * 0.95));
            p99LatencyUs = sortedLatencies.get((int) (sortedLatencies.size() * 0.99));
        }

        System.out.println("==================================================");
        System.out.println("压测完成报告:");
        System.out.println("==================================================");
        System.out.printf("实际测试时长: %.2f 秒\n", actualDurationMs / 1000.0);
        System.out.printf("总执行成功次数: %d\n", totalRequests);
        System.out.printf("吞吐量 QPS: %.2f req/sec\n", qps);
        if (!sortedLatencies.isEmpty()) {
            System.out.printf("平均延迟: %.2f us (微秒)\n", avgLatencyUs);
            System.out.printf("平均延迟: %.3f ms (毫秒)\n", avgLatencyUs / 1000.0);
            System.out.printf("P95 延迟: %d us (%.3f ms)\n", p95LatencyUs, p95LatencyUs / 1000.0);
            System.out.printf("P99 延迟: %d us (%.3f ms)\n", p99LatencyUs, p99LatencyUs / 1000.0);
        } else {
            System.out.println("未收集到足够的延时数据点");
        }
        System.out.println("==================================================");

        threadPool.shutdown();
        clientPool.shutdown();
    }

    private Factor createMockFactor(String id, List<String> deps, long result) {
        return new Factor() {
            @Override
            public String factorId() { return id; }
            @Override
            public List<String> dependencies() { return deps; }
            @Override
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                return FactorResult.success(result);
            }
        };
    }
}
