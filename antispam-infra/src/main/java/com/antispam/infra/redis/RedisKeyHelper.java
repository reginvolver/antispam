package com.antispam.infra.redis;

/**
 * Redis Key 命名规范：antispam:{功能}:{维度}
 */
public final class RedisKeyHelper {
    private static final String PREFIX = "antispam";

    private RedisKeyHelper() {}

    public static String loginFreqKey(String userId) {
        return PREFIX + ":login_freq:" + userId;
    }

    public static String deviceCountKey(String userId) {
        return PREFIX + ":device_count:" + userId;
    }

    public static String banKey(String userId) {
        return PREFIX + ":ban:" + userId;
    }

    public static String captchaKey(String userId) {
        return PREFIX + ":captcha:" + userId;
    }

    public static String rateLimitKey(String userId) {
        return PREFIX + ":rate_limit:" + userId;
    }
}
