package com.loopia.gameanimationshow.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 客户端 IP 解析工具：优先读取反向代理透传的 X-Real-IP / X-Forwarded-For，回退到 RemoteAddr。
 * 仅用于限流维度统计，不参与安全信任判定。
 */
public final class ClientIpUtil {

    private ClientIpUtil() {
    }

    /**
     * 获取当前请求的客户端 IP；不在 Web 请求上下文时返回 null。
     */
    public static String currentIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            String ip = request.getHeader("X-Real-IP");
            if (!StringUtils.hasText(ip)) {
                String forwarded = request.getHeader("X-Forwarded-For");
                if (StringUtils.hasText(forwarded)) {
                    ip = forwarded.split(",")[0].trim();
                }
            }
            if (!StringUtils.hasText(ip)) {
                ip = request.getRemoteAddr();
            }
            /* 本机 IPv6 回环归一，避免与 IPv4 回环被算成两个维度 */
            if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
                ip = "127.0.0.1";
            }
            return ip;
        } catch (Exception e) {
            return null;
        }
    }
}
