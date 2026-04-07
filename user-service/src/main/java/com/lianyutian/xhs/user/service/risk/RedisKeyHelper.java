package com.lianyutian.xhs.user.service.risk;

public class RedisKeyHelper {

    private final String prefix;

    public RedisKeyHelper(String prefix) {
        this.prefix = prefix;
    }

    public String imageCaptcha(String token) {
        return prefix + ":captcha:image:" + token;
    }

    public String emailCaptcha(String purpose, String target) {
        return prefix + ":captcha:email:" + purpose + ":" + target;
    }

    public String captchaProof(String proofToken) {
        return prefix + ":captcha:proof:" + proofToken;
    }

    public String rateLimit(String action, String dimension, String key) {
        return prefix + ":risk:rate:" + action + ":" + dimension + ":" + key;
    }

    public String failureCounter(String action, String dimension, String key) {
        return prefix + ":risk:fail:" + action + ":" + dimension + ":" + key;
    }
}
