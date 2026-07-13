package com.antispam.web;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/** REST 接口入参，对应 RiskContext 字段 */
@Data
public class RiskRequest {
    private String businessType;
    private String userId;
    private String deviceId;
    private String ip;
    private String eventType;
    private Map<String, Object> attributes = new HashMap<>();
}
