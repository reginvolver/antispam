package com.antispam.web;

import com.antispam.api.model.RiskContext;
import com.antispam.api.model.RiskResponse;
import com.antispam.api.spi.RiskEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 风控引擎 REST 入口。
 * POST /api/risk/evaluate
 */
@Slf4j
@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskEngineController {

    private final RiskEngine riskEngine;

    @PostMapping("/evaluate")
    public ResponseEntity<RiskResponse> evaluate(@RequestBody RiskRequest request) {
        RiskContext ctx = RiskContext.builder()
                .businessType(request.getBusinessType())
                .userId(request.getUserId())
                .deviceId(request.getDeviceId())
                .ip(request.getIp())
                .eventType(request.getEventType())
                .attributes(request.getAttributes())
                .timestamp(System.currentTimeMillis())
                .build();

        log.info("[RiskEngineController] Evaluating risk for userId={}, businessType={}",
                ctx.getUserId(), ctx.getBusinessType());

        RiskResponse response = riskEngine.evaluate(ctx);

        log.info("[RiskEngineController] Result: level={}, elapsedMs={}, timedOut={}",
                response.getLevel(), response.getElapsedMs(), response.isTimedOut());

        return ResponseEntity.ok(response);
    }
}
