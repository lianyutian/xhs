package com.lianyutian.xhs.user.model.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lianyutian.xhs.user.model.domain.AccountStatus;
import com.lianyutian.xhs.user.model.domain.RefreshTokenStatus;
import com.lianyutian.xhs.user.model.domain.SessionStatus;
import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.model.domain.VerificationCodeStatus;
import org.junit.jupiter.api.Test;

class AuthDomainRulesTest {

    @Test
    void shouldSupportExpectedSessionAndTokenTransitions() {
        assertThat(SessionStatus.ACTIVE.canTransitionTo(SessionStatus.REVOKED)).isTrue();
        assertThat(SessionStatus.REVOKED.canTransitionTo(SessionStatus.ACTIVE)).isFalse();

        assertThat(RefreshTokenStatus.ACTIVE.canTransitionTo(RefreshTokenStatus.REPLACED)).isTrue();
        assertThat(RefreshTokenStatus.REPLACED.canTransitionTo(RefreshTokenStatus.ACTIVE)).isFalse();
    }

    @Test
    void shouldSupportVerificationLifecycleRules() {
        assertThat(VerificationCodePurpose.REGISTER.name()).isEqualTo("REGISTER");
        assertThat(VerificationCodeStatus.ACTIVE.canTransitionTo(VerificationCodeStatus.CONSUMED)).isTrue();
        assertThat(VerificationCodeStatus.CONSUMED.canTransitionTo(VerificationCodeStatus.ACTIVE)).isFalse();
    }

    @Test
    void shouldIdentifyLoginEligibleAccountStatus() {
        assertThat(AccountStatus.ACTIVE.isLoginAllowed()).isTrue();
        assertThat(AccountStatus.LOCKED.isLoginAllowed()).isFalse();
    }
}
