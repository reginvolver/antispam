package com.antispam.api.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class FactorResult {
    /** 计算成功的结果值（数字/布尔/字符串） */
    private final Object value;
    /** 是否成功计算 */
    private final boolean success;
    /** 计算失败时使用的 fallback 值，不得为 null */
    private final Object fallbackValue;
    /** 失败原因（可选） */
    private final String errorMessage;

    /**
     * 获取有效值：成功返回 value，失败返回 fallbackValue。
     * GraphExecutor 在将结果传递给 Aviator 规则时总是调用此方法。
     */
    public Object effectiveValue() {
        return success ? value : fallbackValue;
    }

    public static FactorResult success(Object value) {
        return FactorResult.builder()
                .success(true)
                .value(value)
                .fallbackValue(value)
                .build();
    }

    public static FactorResult failure(Object fallbackValue, String reason) {
        return FactorResult.builder()
                .success(false)
                .value(null)
                .fallbackValue(fallbackValue)
                .errorMessage(reason)
                .build();
    }
}
