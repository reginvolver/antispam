package com.antispam.infra.kafka;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

@Getter
@Builder
@ToString
public class AuditEvent {
    private final String requestId;
    private final String userId;
    private final String businessType;
    private final String eventType;
    private final String riskLevel;
    private final boolean timedOut;
    private final long elapsedMs;
    private final Map<String, Object> factorValues;
    private final long timestamp;
}
