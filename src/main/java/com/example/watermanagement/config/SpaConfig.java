package com.example.watermanagement.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;

/**
 * SPA 路由回退过滤器
 * <p>
 * Vue Router 使用 HTML5 History 模式，浏览器刷新 /readings 等路径时，
 * 后端没有对应资源，需要返回 index.html 让 Vue Router 处理。
 * <p>
 * 此过滤器将非 API、非静态资源的请求，统一转发到 /index.html。
 */
@Configuration
public class SpaConfig {

    @Bean
    public FilterRegistrationBean<SpaFilter> spaFilter() {
        FilterRegistrationBean<SpaFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new SpaFilter());
        reg.addUrlPatterns("/*");          // 拦截所有路径
        reg.setOrder(Ordered.LOWEST_PRECEDENCE); // 优先级最低，让 Spring MVC 先处理 API
        return reg;
    }

    static class SpaFilter implements Filter {
        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest request = (HttpServletRequest) req;
            String path = request.getRequestURI();

            // 以下路径不拦截：API、静态资源、Swagger
            if (path.startsWith("/api/")
                    || path.startsWith("/assets/")
                    || path.startsWith("/swagger-ui")
                    || path.startsWith("/api-docs")
                    || path.contains(".")) {   // 有后缀的是静态文件（.js/.css/.png等）
                chain.doFilter(req, res);
                return;
            }

            // 其他所有路径 → 转发到 /index.html
            request.getRequestDispatcher("/index.html").forward(req, res);
        }
    }
}
