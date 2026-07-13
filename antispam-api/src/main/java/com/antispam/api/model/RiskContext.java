package com.antispam.api.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
@Builder
@ToString
public class RiskContext {
    /** 业务种类，用于路由到对应套餐，例如 "ECOMMERCE" */
    private final String businessType;
    /** 用户唯一标识 */
    private final String userId;
    /** 设备唯一标识 */
    private final String deviceId;
    /** 客户端 IP */
    private final String ip;
    /** 事件类型，例如 "LOGIN"、"PAY"、"REGISTER" */
    private final String eventType;
    /** 扩展属性，可携带业务方自定义字段 */
    @Builder.Default
    private final Map<String, Object> attributes = new HashMap<>();
    /** 请求时间戳（毫秒） */
    @Builder.Default
    private final long timestamp = System.currentTimeMillis();
}
