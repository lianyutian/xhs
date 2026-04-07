package com.lianyutian.xhs.user.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.lianyutian.xhs.user.service.auth.AuthService;
import com.lianyutian.xhs.user.controller.response.ApiResponse;
import com.lianyutian.xhs.user.service.integration.mail.MailSenderAdapter;
import com.lianyutian.xhs.user.repository.mybatis.UserAccountMapper;
import com.lianyutian.xhs.user.service.risk.RiskControlService;
import com.lianyutian.xhs.user.service.verification.VerificationCodeService;
import org.junit.jupiter.api.Test;

class PackageStructureTest {

    @Test
    void shouldProvideCorePackageTypes() {
        assertThat(AuthService.class).isNotNull();
        assertThat(VerificationCodeService.class).isNotNull();
        assertThat(RiskControlService.class).isNotNull();
        assertThat(UserAccountMapper.class).isNotNull();
        assertThat(ApiResponse.class).isNotNull();
        assertThat(MailSenderAdapter.class).isNotNull();
    }
}
