package com.antispam.infra.redis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RedisKeyHelperTest {

    @Test
    void loginFreqKey_containsUserIdAndPrefix() {
        String key = RedisKeyHelper.loginFreqKey("user123");
        assertEquals("antispam:login_freq:user123", key);
    }

    @Test
    void deviceCountKey_containsUserIdAndPrefix() {
        String key = RedisKeyHelper.deviceCountKey("user123");
        assertEquals("antispam:device_count:user123", key);
    }

    @Test
    void banKey_containsUserIdAndPrefix() {
        String key = RedisKeyHelper.banKey("user123");
        assertEquals("antispam:ban:user123", key);
    }

    @Test
    void captchaKey_containsUserIdAndPrefix() {
        String key = RedisKeyHelper.captchaKey("user123");
        assertEquals("antispam:captcha:user123", key);
    }

    @Test
    void rateLimitKey_containsUserIdAndPrefix() {
        String key = RedisKeyHelper.rateLimitKey("user123");
        assertEquals("antispam:rate_limit:user123", key);
    }
}
