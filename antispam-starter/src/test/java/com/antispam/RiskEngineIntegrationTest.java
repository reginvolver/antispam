package com.antispam;

import com.antispam.api.model.*;
import com.antispam.infra.kafka.RiskKafkaProducer;
import com.antispam.infra.redis.RedisWindowCounter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 核心风控引擎端到端集成测试（REST 接口调用 + DAG 执行 + 套餐规则匹配 + 处罚执行）。
 * 排除数据库/Redis/Kafka等外部连接，使用 MockBean 进行行为隔离。
 */
@Slf4j
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "antispam.engine.timeout-ms=200"
    }
)
@AutoConfigureMockMvc
class RiskEngineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ─── 隔离外部组件依赖 ────────────────────────────────────────────────────

    @MockBean
    private RedisWindowCounter redisWindowCounter;

    @MockBean
    private RiskKafkaProducer riskKafkaProducer;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        // 让 StringRedisTemplate 返回 ValueOperations 模拟对象，防止处罚执行报错
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ─── 1. 简易测试场景：PASS（正常用户） ─────────────────────────────────────

    @Test
    @DisplayName("简易场景：正常用户登录，不触发任何风控规则，返回 PASS")
    void evaluate_simpleScenario_returnsPass() throws Exception {
        log.info(">>> [Test Scenario 1] Starting simple E2E test: PASS scenario");

        // 模拟 Redis 数据：最近 1 分钟登录 2 次，24 小时内不同设备数 1 台
        when(redisWindowCounter.count(contains("login_freq:normal_user"), anyLong(), anyLong()))
                .thenReturn(2L);
        when(redisWindowCounter.countSet(contains("device_count:normal_user")))
                .thenReturn(1L);

        // 构造 REST 请求参数
        Map<String, Object> request = new HashMap<>();
        request.put("businessType", "ECOMMERCE");
        request.put("userId", "normal_user");
        request.put("deviceId", "deviceA");
        request.put("ip", "192.168.1.100");
        request.put("eventType", "LOGIN");

        String responseBody = mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("PASS"))
                .andExpect(jsonPath("$.timedOut").value(false))
                .andExpect(jsonPath("$.factorValues.loginFreq1Min").value(2))
                .andExpect(jsonPath("$.factorValues.deviceCount24h").value(1))
                .andReturn().getResponse().getContentAsString();

        log.info(">>> [Test Scenario 1] Finished. Response:\n{}", responseBody);
    }

    // ─── 2. 中等测试场景：REVIEW（可疑用户，弹验证码） ────────────────────────

    @Test
    @DisplayName("中等场景：高频登录且多设备，触发 REVIEW，并执行 captcha 处罚")
    void evaluate_mediumScenario_returnsReviewAndCaptcha() throws Exception {
        log.info(">>> [Test Scenario 2] Starting medium E2E test: REVIEW scenario");

        // 模拟 Redis 数据：最近 1 分钟登录 6 次 (>5)，24 小时内不同设备数 4 台 (>3)
        // 匹配规则: loginFreq1Min > 5 && deviceCount24h > 3 -> REVIEW + captcha
        when(redisWindowCounter.count(contains("login_freq:suspicious_user"), anyLong(), anyLong()))
                .thenReturn(6L);
        when(redisWindowCounter.countSet(contains("device_count:suspicious_user")))
                .thenReturn(4L);

        Map<String, Object> request = new HashMap<>();
        request.put("businessType", "ECOMMERCE");
        request.put("userId", "suspicious_user");
        request.put("deviceId", "deviceD");
        request.put("ip", "192.168.1.101");
        request.put("eventType", "LOGIN");

        String responseBody = mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("REVIEW"))
                .andExpect(jsonPath("$.matchedPolicies[0]").value("loginRiskPolicy"))
                .andExpect(jsonPath("$.punishments[0].punishmentId").value("captcha"))
                .andExpect(jsonPath("$.punishments[0].executed").value(true))
                .andReturn().getResponse().getContentAsString();

        // 验证处罚是否向 Redis 写入了标记
        verify(valueOps).set(eq("antispam:captcha:suspicious_user"), eq("1"), eq(300L), any());
        log.info(">>> [Test Scenario 2] Finished. Response:\n{}", responseBody);
    }

    // ─── 3. 复杂测试场景：BLOCK（黑客高频，封号） ────────────────────────────

    @Test
    @DisplayName("复杂场景：极高频登录，触发 BLOCK，写黑名单并发送 Kafka 处罚事件")
    void evaluate_complexScenario_returnsBlockAndBanAccount() throws Exception {
        log.info(">>> [Test Scenario 3] Starting complex E2E test: BLOCK scenario");

        // 模拟 Redis 数据：最近 1 分钟登录 12 次 (>10)
        // 匹配规则: loginFreq1Min > 10 -> BLOCK + banAccount
        when(redisWindowCounter.count(contains("login_freq:hacker_user"), anyLong(), anyLong()))
                .thenReturn(12L);
        when(redisWindowCounter.countSet(contains("device_count:hacker_user")))
                .thenReturn(2L);

        Map<String, Object> request = new HashMap<>();
        request.put("businessType", "ECOMMERCE");
        request.put("userId", "hacker_user");
        request.put("deviceId", "deviceHack");
        request.put("ip", "8.8.8.8");
        request.put("eventType", "LOGIN");

        String responseBody = mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("BLOCK"))
                .andExpect(jsonPath("$.matchedPolicies[0]").value("loginRiskPolicy"))
                .andExpect(jsonPath("$.punishments[0].punishmentId").value("banAccount"))
                .andExpect(jsonPath("$.punishments[0].executed").value(true))
                .andReturn().getResponse().getContentAsString();

        // 验证封号处罚：Redis 黑名单写入 + Kafka 消息投递
        verify(valueOps).set(eq("antispam:ban:hacker_user"), eq("BLOCK"), eq(86400L), any());
        verify(riskKafkaProducer).sendPunishmentEvent(any());

        log.info(">>> [Test Scenario 3] Finished. Response:\n{}", responseBody);
    }
}
