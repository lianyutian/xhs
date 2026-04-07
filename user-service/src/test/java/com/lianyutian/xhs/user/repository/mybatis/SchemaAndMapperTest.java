package com.lianyutian.xhs.user.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.lianyutian.xhs.user.repository.mybatis.SecurityEventMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserAddressMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserProfileMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserRefreshTokenMapper;
import com.lianyutian.xhs.user.repository.mybatis.UserSessionMapper;
import com.lianyutian.xhs.user.repository.mybatis.VerificationCodeMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SchemaAndMapperTest {

    @Test
    void shouldContainAllAuthTablesInSchema() throws IOException {
        ClassPathResource resource = new ClassPathResource("db/schema.sql");
        String schema = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(schema).contains("create table if not exists user_account");
        assertThat(schema).contains("create table if not exists user_profile");
        assertThat(schema).contains("create table if not exists user_address");
        assertThat(schema).contains("create table if not exists user_session");
        assertThat(schema).contains("create table if not exists user_refresh_token");
        assertThat(schema).contains("create table if not exists verification_code");
        assertThat(schema).contains("create table if not exists security_event");
    }

    @Test
    void shouldExposeMappersForAllAuthAggregates() {
        assertThat(UserProfileMapper.class).isNotNull();
        assertThat(UserAddressMapper.class).isNotNull();
        assertThat(UserSessionMapper.class).isNotNull();
        assertThat(UserRefreshTokenMapper.class).isNotNull();
        assertThat(VerificationCodeMapper.class).isNotNull();
        assertThat(SecurityEventMapper.class).isNotNull();
    }

    @Test
    void shouldProvideSqlMappingsForCoreInsertions() throws Exception {
        assertThat(readMapper("mapper/UserAccountMapper.xml")).contains("<insert id=\"insert\"");
        assertThat(readMapper("mapper/UserProfileMapper.xml")).contains("<insert id=\"insert\"");
        assertThat(readMapper("mapper/UserAddressMapper.xml")).contains("<select id=\"findOwnerUserIdByAddressId\"");
        assertThat(readMapper("mapper/UserSessionMapper.xml")).contains("<insert id=\"insert\"");
        assertThat(readMapper("mapper/UserRefreshTokenMapper.xml")).contains("<insert id=\"insert\"");
        assertThat(readMapper("mapper/VerificationCodeMapper.xml")).contains("<insert id=\"insert\"");
        assertThat(readMapper("mapper/SecurityEventMapper.xml")).contains("<insert id=\"insert\"");
    }

    private String readMapper(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
