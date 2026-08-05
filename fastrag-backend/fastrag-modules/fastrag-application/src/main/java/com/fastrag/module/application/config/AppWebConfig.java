package com.fastrag.module.application.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册应用数据权限拦截器 */
@Configuration
@RequiredArgsConstructor
public class AppWebConfig implements WebMvcConfigurer {

    private final AppDataScopeInterceptor appDataScopeInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(appDataScopeInterceptor)
                .addPathPatterns("/api/apps/**");
    }
}
