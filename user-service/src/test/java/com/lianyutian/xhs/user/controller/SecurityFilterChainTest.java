package com.lianyutian.xhs.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.SecurityFilterChain;

class SecurityFilterChainTest {

    @Test
    void shouldProvideExplicitWebSecurityConfigWithFilterChainBean() throws Exception {
        Class<?> configClass = Class.forName("com.lianyutian.xhs.user.config.WebSecurityConfig");
        assertThat(configClass).isNotNull();

        Method filterChainMethod = null;
        for (Method method : configClass.getDeclaredMethods()) {
            if (SecurityFilterChain.class.isAssignableFrom(method.getReturnType())) {
                filterChainMethod = method;
                break;
            }
        }
        assertThat(filterChainMethod).isNotNull();
    }
}
