package com.lianyutian.xhs.user.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

class DockerAvailabilityGuardTest {

    @Test
    void shouldRequireDockerInCiForRedisIntegrationTests() {
        boolean ci = "true".equalsIgnoreCase(System.getenv("CI"));
        if (!ci) {
            return;
        }
        assertThat(DockerClientFactory.instance().isDockerAvailable())
            .as("CI 环境必须提供 Docker，以执行 Redis Testcontainers 集成测试")
            .isTrue();
    }
}
