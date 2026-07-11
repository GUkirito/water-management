package com.example.watermanagement.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

@Component
public class LocalOnlyOperationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!isSensitiveWrite(request) || isLoopback(request.getRemoteAddr())) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":403,\"message\":\"该操作仅允许在本机执行\",\"data\":null}");
        return false;
    }

    private boolean isSensitiveWrite(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("GET".equals(method) && "/api/settings/backup/download".equals(path)) return true;
        if ("POST".equals(method) && "/api/settings/backup/restore".equals(path)) return true;
        if ("POST".equals(method) && "/api/households/batch-delete".equals(path)) return true;
        if ("DELETE".equals(method) && path.startsWith("/api/households/")) return true;
        if ("POST".equals(method) && "/api/material-records/batch-delete".equals(path)) return true;
        if ("DELETE".equals(method) && path.matches("/api/material-records/\\d+")) return true;
        if ("DELETE".equals(method) && "/api/accounting/month-locks".equals(path)) return true;
        return "POST".equals(method) && path.startsWith("/api/accounting/adjustments/");
    }

    private boolean isLoopback(String remoteAddress) {
        try {
            return remoteAddress != null && InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (Exception ignored) {
            return false;
        }
    }
}
