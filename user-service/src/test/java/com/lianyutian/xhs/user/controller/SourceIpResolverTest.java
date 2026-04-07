package com.lianyutian.xhs.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SourceIpResolverTest {

    @Test
    void shouldUseRemoteAddressWhenProxyIsNotTrusted() {
        SourceIpResolver resolver = new SourceIpResolver(Set.of("127.0.0.1"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("8.8.8.8");
        request.addHeader("X-Forwarded-For", "1.1.1.1");

        assertThat(resolver.resolve(request)).isEqualTo("8.8.8.8");
    }

    @Test
    void shouldUseForwardedAddressWhenProxyIsTrusted() {
        SourceIpResolver resolver = new SourceIpResolver(Set.of("127.0.0.1"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2");

        assertThat(resolver.resolve(request)).isEqualTo("1.1.1.1");
    }

    @Test
    void shouldFallbackToUnknownWhenRemoteIsMissing() {
        SourceIpResolver resolver = new SourceIpResolver(Set.of("127.0.0.1"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("");

        assertThat(resolver.resolve(request)).isEqualTo("unknown");
    }
}
