package com.antispam.web;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 实时风控 REST API 压测/流量生成器。
 * 默认运行 10 分钟以生成持续稳定的监控流量。
 */
class RiskEngineLoadRunner {

    private static final String TARGET_URL = "http://localhost:8080/api/risk/evaluate";
    
    // 模拟不同风险等级用户的请求参数模板
    private static final List<String> USER_TEMPLATES = List.of(
            // 1. PASS 正常流量 (占比 70%)
            "{\"businessType\":\"ECOMMERCE\",\"userId\":\"normal_user_%d\",\"deviceId\":\"dev_normal\",\"ip\":\"192.168.1.1\",\"eventType\":\"LOGIN\"}",
            // 2. REVIEW 可疑流量 (占比 20%)
            "{\"businessType\":\"ECOMMERCE\",\"userId\":\"suspicious_user_%d\",\"deviceId\":\"dev_suspicious\",\"ip\":\"192.168.2.1\",\"eventType\":\"LOGIN\"}",
            // 3. BLOCK 恶意攻击流量 (占比 10%)
            "{\"businessType\":\"ECOMMERCE\",\"userId\":\"hacker_user_%d\",\"deviceId\":\"dev_hacker\",\"ip\":\"8.8.8.8\",\"eventType\":\"LOGIN\"}"
    );

    @Test
    void startTrafficGeneration() throws Exception {
        System.out.println("==================================================");
        System.out.println("开始向 " + TARGET_URL + " 发送并发风控请求...");
        System.out.println("请打开 Grafana 监控面板 (http://localhost:3000) 观察指标变化");
        System.out.println("默认执行 10 分钟。输入 Ctrl+C 或在 IDE 中停止运行以提前结束。");
        System.out.println("==================================================");

        int clientCount = 8; // 并发请求线程数
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .executor(executor)
                .build();

        AtomicLong successCount = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);

        // 定时打印统计信息
        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor();
        reporter.scheduleAtFixedRate(() -> {
            long ok = successCount.getAndSet(0);
            long err = errorCount.getAndSet(0);
            System.out.printf("[%tT] 最近 5s 发送情况: 成功=%d req/s, 失败=%d req/s\n",
                    System.currentTimeMillis(), ok / 5, err / 5);
        }, 5, 5, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10); // 跑 10 分钟

        CountDownLatch latch = new CountDownLatch(clientCount);

        for (int i = 0; i < clientCount; i++) {
            executor.submit(() -> {
                long requestSeq = 0;
                while (System.currentTimeMillis() < endTime && !Thread.currentThread().isInterrupted()) {
                    requestSeq++;
                    try {
                        // 按概率生成不同的 JSON 请求体 (70% PASS, 20% REVIEW, 10% BLOCK)
                        int rand = ThreadLocalRandom.current().nextInt(100);
                        String body;
                        if (rand < 70) {
                            body = String.format(USER_TEMPLATES.get(0), requestSeq);
                        } else if (rand < 90) {
                            body = String.format(USER_TEMPLATES.get(1), requestSeq);
                        } else {
                            body = String.format(USER_TEMPLATES.get(2), requestSeq);
                        }

                        HttpRequest httpRequest = HttpRequest.newBuilder()
                                .uri(URI.create(TARGET_URL))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build();

                        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 200) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }

                        // 稍微控制下频次，防止瞬间把本地接口打死，每个客户端线程每次请求后随机休眠 1~5 毫秒
                        Thread.sleep(ThreadLocalRandom.current().nextInt(1, 6));

                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception ex) {
                        errorCount.incrementAndGet();
                        try {
                            Thread.sleep(100); // 发生连接报错时稍微等待
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                latch.countDown();
            });
        }

        latch.await();
        System.out.println("压测流量生成结束。");
        reporter.shutdown();
        executor.shutdown();
    }
}
