package com.lianyutian.xhs.user.service.auth;

import com.lianyutian.xhs.user.model.domain.AccountStatus;
import com.lianyutian.xhs.user.model.domain.RefreshTokenStatus;
import com.lianyutian.xhs.user.model.domain.SessionStatus;
import com.lianyutian.xhs.user.model.domain.VerificationCodePurpose;
import com.lianyutian.xhs.user.service.event.SecurityEvent;
import com.lianyutian.xhs.user.service.event.SecurityEventRecorder;
import com.lianyutian.xhs.user.model.entity.UserAccountEntity;
import com.lianyutian.xhs.user.model.entity.UserProfileEntity;
import com.lianyutian.xhs.user.model.entity.UserRefreshTokenEntity;
import com.lianyutian.xhs.user.model.entity.UserSessionEntity;
import com.lianyutian.xhs.user.repository.mybatis.UserAccountMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserProfileMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserRefreshTokenMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserSessionMapper;
import com.lianyutian.xhs.user.service.risk.LoginRiskAction;
import com.lianyutian.xhs.user.service.risk.RiskControlService;
import com.lianyutian.xhs.user.service.verification.DefaultVerificationCodeService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

public class DefaultAuthService implements AuthService {

    private static final String DUMMY_PASSWORD_HASH = "$2a$10$K7f19MllLtWhKxL7e6aKKOjj8vD4LpwQ2xK4mK/2clvtVv5w8A3b2";

    private final DefaultVerificationCodeService verificationCodeService;
    private final RiskControlService riskControlService;
    private final SecurityEventRecorder eventRecorder;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final String jwtIssuer;
    private final SecretKey jwtSigningKey;
    private final UserAccountMapper userAccountMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserSessionMapper userSessionMapper;
    private final UserRefreshTokenMapper userRefreshTokenMapper;
    private final PasswordEncoder passwordEncoder;

    public DefaultAuthService(
        DefaultVerificationCodeService verificationCodeService,
        RiskControlService riskControlService,
        SecurityEventRecorder eventRecorder,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String jwtIssuer,
        String jwtSecret,
        UserAccountMapper userAccountMapper,
        UserProfileMapper userProfileMapper,
        UserSessionMapper userSessionMapper,
        UserRefreshTokenMapper userRefreshTokenMapper,
        PasswordEncoder passwordEncoder
    ) {
        this.verificationCodeService = verificationCodeService;
        this.riskControlService = riskControlService;
        this.eventRecorder = eventRecorder;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.jwtIssuer = jwtIssuer;
        if (jwtSecret == null || jwtSecret.length() < 32 || "replace-with-at-least-32-char-secret".equals(jwtSecret)) {
            throw new IllegalArgumentException("INVALID_JWT_SECRET");
        }
        this.jwtSigningKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.userAccountMapper = userAccountMapper;
        this.userProfileMapper = userProfileMapper;
        this.userSessionMapper = userSessionMapper;
        this.userRefreshTokenMapper = userRefreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户注册功能
     *
     * @param email 用户邮箱地址
     * @param password 用户密码（明文）
     * @param registerCode 注册验证码
     * @throws IllegalArgumentException 当邮箱已存在或验证码无效时抛出
     * @throws IllegalStateException 当账户创建失败时抛出
     */
    @Transactional
    public void register(String email, String password, String registerCode) {
        // 检查邮箱是否已被注册
        if (userAccountMapper.findByEmail(email) != null) {
            throw new IllegalArgumentException("EMAIL_ALREADY_EXISTS");
        }

        // 尝试消耗注册验证码并验证正确性
        DefaultVerificationCodeService.EmailCodeConsumeAttempt verificationAttempt =
            verificationCodeService.attemptConsumeEmailCodeInCurrentTransaction(
                VerificationCodePurpose.REGISTER,
                email,
                registerCode
            );
        if (!verificationAttempt.verified()) {
            verificationCodeService.recordFailedEmailCodeAttempt(verificationAttempt.failureContext());
            throw new IllegalArgumentException("INVALID_REGISTER_CODE");
        }

        // 创建用户账户记录，对密码进行加密存储
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UserAccountEntity account = new UserAccountEntity(
            null,
            email,
            passwordEncoder.encode(password),
            AccountStatus.ACTIVE,
            now,
            now
        );
        try {
            userAccountMapper.insert(account);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("EMAIL_ALREADY_EXISTS");
        }

        // 验证账户是否成功保存
        UserAccountEntity savedAccount = userAccountMapper.findByEmail(email);
        if (savedAccount == null) {
            throw new IllegalStateException("ACCOUNT_CREATE_FAILED");
        }

        // 创建用户档案记录，默认用户名为邮箱前缀
        userProfileMapper.insert(new UserProfileEntity(
            null,
            savedAccount.getId(),
            email.substring(0, email.indexOf("@")),
            null,
            now,
            now
        ));
    }

    public AuthTokens login(String email, String password, String sourceIp, String userAgent) {
        return login(email, password, sourceIp, userAgent, null, null);
    }

    @Transactional
    public AuthTokens login(
        String email,
        String password,
        String sourceIp,
        String userAgent,
        String captchaToken,
        String captchaAnswer
    ) {
        LoginRiskAction riskAction = riskControlService.currentLoginAction(sourceIp, email);
        if (riskAction == LoginRiskAction.TEMP_BLOCK) {
            eventRecorder.record(new SecurityEvent("RATE_LIMIT_HIT", null, null, sourceIp, "login_temp_blocked", Instant.now()));
            return AuthTokens.failure("LOGIN_TEMP_BLOCKED");
        }
        if (riskAction == LoginRiskAction.REQUIRE_CAPTCHA) {
            boolean captchaPassed = captchaToken != null
                && captchaAnswer != null
                && verificationCodeService.verifyImageCaptcha(captchaToken, captchaAnswer, "LOGIN");
            if (!captchaPassed) {
                riskControlService.recordLoginFailure(sourceIp, email);
                eventRecorder.record(new SecurityEvent(
                    "LOGIN_FAILED",
                    null,
                    null,
                    sourceIp,
                    "captcha_missing_or_invalid",
                    Instant.now()
                ));
                return AuthTokens.failure("CAPTCHA_REQUIRED");
            }
        }

        UserAccountEntity account = userAccountMapper.findByEmail(email);
        String loginFailureReason = resolveLoginFailureReason(account, password);
        if (loginFailureReason != null) {
            riskControlService.recordLoginFailure(sourceIp, email);
            eventRecorder.record(new SecurityEvent(
                "LOGIN_FAILED",
                account == null ? null : account.getId(),
                null,
                sourceIp,
                loginFailureReason,
                Instant.now()
            ));
            return AuthTokens.failure("INVALID_CREDENTIALS");
        }

        riskControlService.resetLoginFailures(sourceIp, email);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UserSessionEntity session = new UserSessionEntity(
            null,
            account.getId(),
            SessionStatus.ACTIVE,
            sourceIp,
            userAgent,
            now.plus(refreshTokenTtl),
            now,
            now
        );
        userSessionMapper.insert(session);
        Long sessionId = session.getId();
        if (sessionId == null) {
            throw new IllegalStateException("SESSION_CREATE_FAILED");
        }

        String accessToken = buildAccessToken(account.getId(), sessionId, email);
        String refreshToken = UUID.randomUUID().toString();
        userRefreshTokenMapper.insert(new UserRefreshTokenEntity(
            null,
            sessionId,
            hash(refreshToken),
            RefreshTokenStatus.ACTIVE,
            now.plus(refreshTokenTtl),
            now,
            now
        ));
        return AuthTokens.success(accessToken, refreshToken);
    }

    @Transactional
    public AuthTokens refresh(String refreshToken, String sourceIp) {
        String tokenHash = hash(refreshToken);
        if (!riskControlService.allowRefresh(sourceIp, tokenHash)) {
            eventRecorder.record(new SecurityEvent("RATE_LIMIT_HIT", null, null, sourceIp, "refresh_rate_limited", Instant.now()));
            return AuthTokens.failure("REFRESH_RATE_LIMITED");
        }
        UserRefreshTokenEntity current = userRefreshTokenMapper.findByTokenHash(tokenHash);
        if (current == null) {
            recordRefreshReplay(null, sourceIp, "token_not_found");
            return AuthTokens.failure("INVALID_REFRESH_TOKEN");
        }
        if (current.getStatus() == RefreshTokenStatus.REPLACED) {
            recordRefreshReplay(current.getSessionId(), sourceIp, "token_status_replaced");
            return AuthTokens.failure("REFRESH_TOKEN_REPLAYED");
        }
        if (current.getStatus() == RefreshTokenStatus.REVOKED) {
            recordRefreshReplay(current.getSessionId(), sourceIp, "token_status_revoked");
            return AuthTokens.failure("SESSION_REVOKED");
        }
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        if (current.getStatus() != RefreshTokenStatus.ACTIVE || current.getExpiresAt().isBefore(nowUtc)) {
            String detail = current.getExpiresAt().isBefore(nowUtc)
                ? "token_status_expired"
                : "token_status_" + current.getStatus().name().toLowerCase();
            recordRefreshReplay(current.getSessionId(), sourceIp, detail);
            return AuthTokens.failure("INVALID_REFRESH_TOKEN");
        }

        UserSessionEntity session = userSessionMapper.findById(current.getSessionId());
        if (session == null || session.getStatus() != SessionStatus.ACTIVE) {
            return AuthTokens.failure("SESSION_REVOKED");
        }
        if (session.getExpiresAt().isBefore(nowUtc)) {
            userSessionMapper.updateStatus(session.getId(), SessionStatus.EXPIRED, nowUtc);
            userRefreshTokenMapper.revokeActiveBySessionId(session.getId(), nowUtc);
            eventRecorder.record(new SecurityEvent(
                "SESSION_REVOKED",
                session.getUserId(),
                session.getId(),
                sourceIp,
                "session_expired",
                Instant.now()
            ));
            return AuthTokens.failure("SESSION_REVOKED");
        }

        UserAccountEntity account = userAccountMapper.findById(session.getUserId());
        if (account == null) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            revokeSessionAndActiveRefresh(session, sourceIp, "account_missing", now);
            return AuthTokens.failure("SESSION_REVOKED");
        }
        if (!account.getStatus().isLoginAllowed()) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            revokeSessionAndActiveRefresh(
                session,
                sourceIp,
                "account_status_" + account.getStatus().name().toLowerCase(),
                now
            );
            return AuthTokens.failure("SESSION_REVOKED");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int replacedRows = userRefreshTokenMapper.transitionToReplacedIfActive(current.getId(), now);
        if (replacedRows == 0) {
            recordRefreshReplay(current.getSessionId(), sourceIp, "token_status_replaced");
            return AuthTokens.failure("REFRESH_TOKEN_REPLAYED");
        }

        String newRefreshToken = UUID.randomUUID().toString();
        userRefreshTokenMapper.insert(new UserRefreshTokenEntity(
            null,
            session.getId(),
            hash(newRefreshToken),
            RefreshTokenStatus.ACTIVE,
            now.plus(refreshTokenTtl),
            now,
            now
        ));
        userSessionMapper.updateExpiresAt(session.getId(), now.plus(refreshTokenTtl), now);

        String newAccessToken = buildAccessToken(session.getUserId(), session.getId(), account.getEmail());
        return AuthTokens.success(newAccessToken, newRefreshToken);
    }

    public CurrentUserView currentUser(String accessToken) {
        Claims claims = parseAccessToken(accessToken);
        Long sessionId = claims.get("sid", Long.class);
        Long userIdClaim = Long.parseLong(claims.getSubject());

        UserSessionEntity session = userSessionMapper.findById(sessionId);
        if (session == null || session.getStatus() != SessionStatus.ACTIVE || session.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new IllegalArgumentException("UNAUTHORIZED");
        }
        if (!session.getUserId().equals(userIdClaim)) {
            throw new IllegalArgumentException("UNAUTHORIZED");
        }

        UserAccountEntity account = userAccountMapper.findById(session.getUserId());
        if (account == null) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            revokeSessionAndActiveRefresh(session, null, "account_missing", now);
            throw new IllegalArgumentException("UNAUTHORIZED");
        }
        if (!account.getStatus().isLoginAllowed()) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            revokeSessionAndActiveRefresh(
                session,
                null,
                "account_status_" + account.getStatus().name().toLowerCase(),
                now
            );
            throw new IllegalArgumentException("UNAUTHORIZED");
        }
        UserProfileEntity profile = userProfileMapper.findByUserId(account.getId());
        return new CurrentUserView(account.getId(), account.getEmail(), profile == null ? "" : profile.getNickname());
    }

    @Transactional
    public void logout(String accessToken) {
        Claims claims = parseAccessToken(accessToken);
        Long sessionId = claims.get("sid", Long.class);
        UserSessionEntity session = userSessionMapper.findById(sessionId);
        if (session == null) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        userSessionMapper.updateStatus(sessionId, SessionStatus.REVOKED, now);
        userRefreshTokenMapper.revokeActiveBySessionId(sessionId, now);
        eventRecorder.record(new SecurityEvent("SESSION_REVOKED", session.getUserId(), sessionId, null, "logout", Instant.now()));
    }

    private String buildAccessToken(Long userId, Long sessionId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId.toString())
            .issuer(jwtIssuer)
            .claim("sid", sessionId)
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTokenTtl)))
            .signWith(jwtSigningKey)
            .compact();
    }

    private Claims parseAccessToken(String accessToken) {
        try {
            return Jwts.parser()
                .verifyWith(jwtSigningKey)
                .requireIssuer(jwtIssuer)
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();
        } catch (JwtException ex) {
            throw new IllegalArgumentException("UNAUTHORIZED");
        }
    }

    private String hash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(messageDigest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String resolveLoginFailureReason(UserAccountEntity account, String password) {
        if (account == null) {
            passwordEncoder.matches(password, DUMMY_PASSWORD_HASH);
            return "account_not_found";
        }
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            return "password_mismatch";
        }
        if (!account.getStatus().isLoginAllowed()) {
            return "account_status_" + account.getStatus().name().toLowerCase();
        }
        return null;
    }

    private void recordRefreshReplay(Long sessionId, String sourceIp, String detail) {
        eventRecorder.record(new SecurityEvent("REFRESH_REPLAY_DETECTED", null, sessionId, sourceIp, detail, Instant.now()));
    }

    private void revokeSessionAndActiveRefresh(UserSessionEntity session, String sourceIp, String detail, OffsetDateTime now) {
        userSessionMapper.updateStatus(session.getId(), SessionStatus.REVOKED, now);
        userRefreshTokenMapper.revokeActiveBySessionId(session.getId(), now);
        eventRecorder.record(new SecurityEvent(
            "SESSION_REVOKED",
            session.getUserId(),
            session.getId(),
            sourceIp,
            detail,
            Instant.now()
        ));
    }
}
