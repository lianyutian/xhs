package com.lianyutian.xhs.user.service.risk;

public interface RiskControlService {

    LoginRiskAction currentLoginAction(String sourceIp, String account);

    void recordLoginFailure(String sourceIp, String account);

    void resetLoginFailures(String sourceIp, String account);

    int incrementUploadCount(Long userId);

    boolean allowImageCaptchaIssue(String sourceIp);

    boolean allowEmailCodeSend(String sourceIp, String targetIdentifier);

    boolean allowRefresh(String sourceIp, String refreshTokenHash);
}
