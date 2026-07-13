package com.antispam.infra.kafka;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class PunishmentEvent {
    private final String requestId;
    private final String userId;
    private final String punishmentId;
    private final String riskLevel;
    private final String businessType;
    private final long timestamp;
}
