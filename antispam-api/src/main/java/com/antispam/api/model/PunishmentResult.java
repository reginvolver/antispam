package com.antispam.api.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class PunishmentResult {
    private final String punishmentId;
    private final boolean executed;
    private final String message;

    public static PunishmentResult success(String punishmentId) {
        return PunishmentResult.builder()
                .punishmentId(punishmentId)
                .executed(true)
                .message("executed successfully")
                .build();
    }

    public static PunishmentResult failure(String punishmentId, String reason) {
        return PunishmentResult.builder()
                .punishmentId(punishmentId)
                .executed(false)
                .message(reason)
                .build();
    }
}
