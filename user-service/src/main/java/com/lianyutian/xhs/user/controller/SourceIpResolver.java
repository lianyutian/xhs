package com.lianyutian.xhs.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

public class SourceIpResolver {

    private final Set<String> trustedProxies;

    public SourceIpResolver(Set<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? Set.of() : Set.copyOf(trustedProxies);
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            remoteAddr = "unknown";
        }

        if (!trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }
        String candidate = forwardedFor.split(",")[0].trim();
        return candidate.isBlank() ? remoteAddr : candidate;
    }
}
