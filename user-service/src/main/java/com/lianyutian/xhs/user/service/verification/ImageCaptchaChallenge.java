package com.lianyutian.xhs.user.service.verification;

public record ImageCaptchaChallenge(String token, String imageContent, String answer) {
}
