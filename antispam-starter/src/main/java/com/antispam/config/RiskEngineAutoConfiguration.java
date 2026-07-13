package com.antispam.config;

import com.antispam.core.graph.GraphExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
@EnableConfigurationProperties(RiskEngineProperties.class)
@ComponentScan(basePackages = {
        "com.antispam.core",
        "com.antispam.factor",
        "com.antispam.policy",
        "com.antispam.punishment",
        "com.antispam.infra"
})
public class RiskEngineAutoConfiguration {

    @Bean
    public ExecutorService riskEngineThreadPool(RiskEngineProperties props) {
        RiskEngineProperties.ThreadPoolProperties tp = props.getThreadPool();
        return new ThreadPoolExecutor(
                tp.getCoreSize(),
                tp.getMaxSize(),
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(tp.getQueueCapacity()),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("risk-graph-" + t.getId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时由调用线程执行（背压）
        );
    }

    @Bean
    public GraphExecutor graphExecutor(ExecutorService riskEngineThreadPool) {
        return new GraphExecutor(riskEngineThreadPool);
    }
}
