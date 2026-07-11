package com.example.watermanagement.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcSecurityConfig implements WebMvcConfigurer {

    private final LocalOnlyOperationInterceptor localOnlyOperationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localOnlyOperationInterceptor).addPathPatterns("/api/**");
    }
}
