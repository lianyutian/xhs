package com.lianyutian.xhs.user.controller;

import com.lianyutian.xhs.user.controller.request.SendEmailCodeRequest;
import com.lianyutian.xhs.user.controller.response.ApiResponse;
import com.lianyutian.xhs.user.controller.response.ImageCaptchaResponse;
import com.lianyutian.xhs.user.controller.response.SendEmailCodeResponse;
import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.service.event.SecurityEvent;
import com.lianyutian.xhs.user.service.event.SecurityEventRecorder;
import com.lianyutian.xhs.user.service.risk.RiskControlService;
import com.lianyutian.xhs.user.service.verification.DefaultVerificationCodeService;
import com.lianyutian.xhs.user.service.verification.ImageCaptchaChallenge;
import java.time.Instant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "verification")
@RestController
@RequestMapping("/api/v1/verification")
public class VerificationController {

    private final DefaultVerificationCodeService verificationCodeService;
    private final RiskControlService riskControlService;
    private final SecurityEventRecorder eventRecorder;
    private final SourceIpResolver sourceIpResolver;

    public VerificationController(
        DefaultVerificationCodeService verificationCodeService,
        RiskControlService riskControlService,
        SecurityEventRecorder eventRecorder,
        SourceIpResolver sourceIpResolver
    ) {
        this.verificationCodeService = verificationCodeService;
        this.riskControlService = riskControlService;
        this.eventRecorder = eventRecorder;
        this.sourceIpResolver = sourceIpResolver;
    }

    @Operation(summary = "issue image captcha challenge")
    @GetMapping("/image-captcha")
    public ApiResponse<ImageCaptchaResponse> imageCaptcha(
        @RequestParam(defaultValue = "LOGIN") String scenario,
        HttpServletRequest servletRequest
    ) {
        String sourceIp = sourceIpResolver.resolve(servletRequest);
        if (!riskControlService.allowImageCaptchaIssue(sourceIp)) {
            eventRecorder.record(new SecurityEvent(
                "RATE_LIMIT_HIT",
                null,
                null,
                sourceIp,
                "image_captcha_rate_limited",
                Instant.now()
            ));
            throw new IllegalArgumentException("IMAGE_CAPTCHA_RATE_LIMITED");
        }
        ImageCaptchaChallenge challenge = verificationCodeService.createImageCaptcha(scenario);
        return ApiResponse.ok(new ImageCaptchaResponse(challenge.token(), challenge.imageContent()));
    }

    @Operation(summary = "issue email verification code")
    @PostMapping("/email-code")
    public ApiResponse<SendEmailCodeResponse> sendEmailCode(@Valid @RequestBody SendEmailCodeRequest request, HttpServletRequest servletRequest) {
        VerificationCodePurpose purpose;
        try {
            purpose = VerificationCodePurpose.valueOf(request.purpose());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("INVALID_REQUEST");
        }

        boolean captchaPassed = request.captchaToken() != null
            && request.captchaAnswer() != null
            && verificationCodeService.verifyImageCaptcha(
                request.captchaToken(),
                request.captchaAnswer(),
                purpose.name()
            );
        if (!captchaPassed) {
            throw new IllegalArgumentException("CAPTCHA_REQUIRED");
        }

        String sourceIp = sourceIpResolver.resolve(servletRequest);
        if (!riskControlService.allowEmailCodeSend(sourceIp, request.targetEmail())) {
            eventRecorder.record(new SecurityEvent(
                "RATE_LIMIT_HIT",
                null,
                null,
                sourceIp,
                "email_code_rate_limited",
                Instant.now()
            ));
            throw new IllegalArgumentException("EMAIL_CODE_RATE_LIMITED");
        }
        DefaultVerificationCodeService.EmailCodeIssueReceipt receipt = verificationCodeService.issueEmailCodeWithReceipt(
            purpose,
            request.targetEmail()
        );
        if (!receipt.sent()) {
            throw new IllegalArgumentException("EMAIL_CODE_SEND_FAILED");
        }
        return ApiResponse.ok(new SendEmailCodeResponse(receipt.requestId(), null));
    }
}
