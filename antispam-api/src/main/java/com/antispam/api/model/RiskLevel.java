package com.antispam.api.model;

public enum RiskLevel {
    PASS,
    REVIEW,
    BLOCK;

    /**
     * 取两个级别中更严重的一个（BLOCK > REVIEW > PASS）
     */
    public RiskLevel max(RiskLevel other) {
        return this.ordinal() >= other.ordinal() ? this : other;
    }
}
