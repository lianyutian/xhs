package com.lianyutian.xhs.user.support;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(TestOverrideConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractDbIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    protected StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanTables() {
        List<String> tables = List.of(
            "security_event",
            "user_address",
            "verification_code",
            "user_refresh_token",
            "user_session",
            "user_profile",
            "user_account"
        );
        for (String table : tables) {
            jdbcTemplate.update("delete from " + table);
        }
        if (redisTemplate != null) {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        }
    }
}
