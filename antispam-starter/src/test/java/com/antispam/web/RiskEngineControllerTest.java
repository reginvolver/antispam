package com.antispam.web;

import com.antispam.api.model.RiskLevel;
import com.antispam.api.model.RiskResponse;
import com.antispam.api.spi.RiskEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RiskEngineController.class)
class RiskEngineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RiskEngine riskEngine;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void evaluate_returnsPassResponse() throws Exception {
        when(riskEngine.evaluate(any())).thenReturn(
                RiskResponse.builder()
                        .level(RiskLevel.PASS)
                        .elapsedMs(15L)
                        .timedOut(false)
                        .matchedPolicies(Collections.emptyList())
                        .punishments(Collections.emptyList())
                        .factorValues(Collections.emptyMap())
                        .build()
        );

        RiskRequest request = new RiskRequest();
        request.setUserId("user1");
        request.setBusinessType("ECOMMERCE");
        request.setEventType("LOGIN");
        request.setDeviceId("dev1");
        request.setIp("1.2.3.4");

        mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("PASS"))
                .andExpect(jsonPath("$.timedOut").value(false));
    }

    @Test
    void evaluate_returnsBlockResponse() throws Exception {
        when(riskEngine.evaluate(any())).thenReturn(
                RiskResponse.builder()
                        .level(RiskLevel.BLOCK)
                        .elapsedMs(80L)
                        .timedOut(false)
                        .matchedPolicies(java.util.List.of("loginRiskPolicy"))
                        .punishments(Collections.emptyList())
                        .factorValues(Collections.emptyMap())
                        .build()
        );

        RiskRequest request = new RiskRequest();
        request.setUserId("bot_user");
        request.setBusinessType("ECOMMERCE");

        mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("BLOCK"))
                .andExpect(jsonPath("$.matchedPolicies[0]").value("loginRiskPolicy"));
    }
}
