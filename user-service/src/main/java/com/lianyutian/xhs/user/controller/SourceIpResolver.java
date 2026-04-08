package com.lianyutian.xhs.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

/**
 * 来源 IP 解析器，用于从 HTTP 请求中提取真实的客户端 IP 地址
 */
public class SourceIpResolver {

    private final Set<String> trustedProxies;

    public SourceIpResolver(Set<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? Set.of() : Set.copyOf(trustedProxies);
    }

    /**
     * 解析并返回请求的真实来源 IP 地址
     *
     * @param request HTTP 请求对象
     * @return 客户端真实 IP 地址，如果无法获取则返回 "unknown"
     */
    public String resolve(HttpServletRequest request) {
        // 获取直连的远程地址
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            remoteAddr = "unknown";
        }

        // 如果直连地址不是受信任的代理，直接返回该地址
        if (!trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }

        // 从 X-Forwarded-For 头部提取第一个非代理 IP（最接近客户端的地址）
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }
        String candidate = forwardedFor.split(",")[0].trim();
        return candidate.isBlank() ? remoteAddr : candidate;
    }
}
