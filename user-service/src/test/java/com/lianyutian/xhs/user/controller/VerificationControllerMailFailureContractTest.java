package com.lianyutian.xhs.user.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lianyutian.xhs.user.controller.request.SendEmailCodeRequest;
import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.service.integration.mail.MailSenderAdapter;
import com.lianyutian.xhs.user.service.verification.DefaultVerificationCodeService;
import com.lianyutian.xhs.user.support.AbstractDbIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletRequest;

@Import(VerificationControllerMailFailureContractTest.FailingMailConfig.class)
class VerificationControllerMailFailureContractTest extends AbstractDbIntegrationTest {

    @Autowired
    private VerificationController verificationController;

    @Autowired
    private DefaultVerificationCodeService verificationCodeService;

    @Test
    void shouldExposeSendFailedCodeWhenMailDeliveryFails() {
        var challenge = verificationCodeService.createImageCaptcha("REGISTER");
        SendEmailCodeRequest request = new SendEmailCodeRequest(
            VerificationCodePurpose.REGISTER.name(),
            "mail-fail@example.com",
            challenge.token(),
            challenge.answer()
        );
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("8.8.8.8");

        assertThatThrownBy(() -> verificationController.sendEmailCode(request, servletRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("EMAIL_CODE_SEND_FAILED");
    }

    @TestConfiguration
    static class FailingMailConfig {

        @Bean
        @Primary
        MailSenderAdapter failingMailSenderAdapter() {
            return (to, subject, content) -> {
                throw new IllegalStateException("mail down");
            };
        }
    }
}
