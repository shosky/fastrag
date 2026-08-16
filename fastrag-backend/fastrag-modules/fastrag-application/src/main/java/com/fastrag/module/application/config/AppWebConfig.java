package com.fastrag.module.application.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 应用模块Web配置类。
 *
 * <p>负责将 {@link AppDataScopeInterceptor} 拦截器注册到Spring MVC拦截器链中，
 * 对所有 /api/apps/** 路径的请求进行数据权限校验，确保用户只能访问和管理
 * 自己有权限的应用资源。
 *
 * @see AppDataScopeInterceptor
 */
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
