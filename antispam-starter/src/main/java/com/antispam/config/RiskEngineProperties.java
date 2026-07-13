package com.antispam.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "antispam.engine")
public class RiskEngineProperties {
    private long timeoutMs = 200;
    private ThreadPoolProperties threadPool = new ThreadPoolProperties();

    @Data
    public static class ThreadPoolProperties {
        private int coreSize = 20;
        private int maxSize = 50;
        private int queueCapacity = 1000;
    }
}
