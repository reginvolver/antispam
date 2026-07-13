package com.antispam.infra.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 消息生产者。发送处罚事件和审计日志到对应 Topic。
 * 使用异步发送（send 返回 Future/CompletableFuture），不阻塞主流程。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RiskKafkaProducer {

    public static final String PUNISHMENT_TOPIC = "antispam.punishment.events";
    public static final String AUDIT_TOPIC = "antispam.audit.logs";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 异步发送处罚事件，按 userId 分区。
     */
    public void sendPunishmentEvent(PunishmentEvent event) {
        kafkaTemplate.send(PUNISHMENT_TOPIC, event.getUserId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[RiskKafkaProducer] Failed to send punishment event for user={}: {}",
                                event.getUserId(), ex.getMessage());
                    } else {
                        log.debug("[RiskKafkaProducer] Punishment event sent for user={}", event.getUserId());
                    }
                });
    }

    /**
     * 异步发送审计日志。
     */
    public void sendAuditLog(AuditEvent event) {
        kafkaTemplate.send(AUDIT_TOPIC, event.getRequestId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[RiskKafkaProducer] Failed to send audit log for requestId={}: {}",
                                event.getRequestId(), ex.getMessage());
                    }
                });
    }
}
