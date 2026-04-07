package com.lianyutian.xhs.user.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
    @Valid @NotNull Jwt jwt,
    @Valid @NotNull Captcha captcha
) {

    @AssertTrue(message = "jwt secret must not use placeholder value")
    public boolean isJwtSecretNotPlaceholder() {
        if (jwt == null) {
            return true;
        }
        return !"replace-with-at-least-32-char-secret".equals(jwt.secret());
    }

    public record Jwt(
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotBlank String issuer,
        @NotBlank @Size(min = 32) String secret
    ) {
    }

    public record Captcha(
        @NotNull Duration imageTtl,
        @NotNull Duration emailTtl,
        @Min(1) int maxAttempts
    ) {
    }
}
