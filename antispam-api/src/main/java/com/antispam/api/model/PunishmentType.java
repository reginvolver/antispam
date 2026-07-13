package com.antispam.api.model;

public enum PunishmentType {
    /** 引擎内部直接执行（如写 Redis、限流） */
    INTERNAL,
    /** 通过 Webhook 通知外部系统 */
    WEBHOOK
}
