package com.fastrag.security.config;

/**
 * Spring Security 核心配置类，定义 HTTP 安全策略、认证过滤器链和密码编码器。
 *
 * <p>配置的组件：
 * <ul>
 *   <li>{@link org.springframework.security.web.SecurityFilterChain} — HTTP 安全过滤器链</li>
 *   <li>{@link org.springframework.security.crypto.password.PasswordEncoder} — BCrypt 密码编码器</li>
 * </ul>
 *
 * <p>关键安全策略：
 * <ul>
 *   <li>禁用 CSRF（前后端分离架构，使用 Token 认证无需 CSRF 防护）</li>
 *   <li>无状态会话管理（SessionCreationPolicy.STATELESS），不依赖 HTTP Session</li>
 *   <li>异步 Dispatcher（SSE 连接的完成回调）放行</li>
 *   <li>公开接口白名单：登录、注册、验证码发送、密码重置、微信扫码登录等认证相关接口</li>
 *   <li>Swagger/Actuator 等开发工具接口放行</li>
 *   <li>{@code /api/**} 路径要求认证，其他路径放行</li>
 * </ul>
 *
 * <p>过滤器链顺序：
 * <ol>
 *   <li>{@link com.fastrag.security.filter.ApiTokenAuthFilter} — API Token 认证（frag_ 前缀 Token）</li>
 *   <li>{@link com.fastrag.security.filter.JwtAuthFilter} — JWT Bearer Token 认证</li>
 *   <li>两者均在 {@code UsernamePasswordAuthenticationFilter} 之前执行</li>
 * </ol>
 *
 * <p>同时启用方法级安全注解（{@code @EnableMethodSecurity}），
 * 支持在 Service 方法上使用 {@code @PreAuthorize} 等注解进行细粒度权限控制。
 *
 * <p>生效方式：通过 {@code @Configuration} + {@code @EnableWebSecurity} 注解被 Spring 自动扫描加载。
 */

import com.fastrag.security.filter.ApiTokenAuthFilter;
import com.fastrag.security.filter.JwtAuthFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final ApiTokenAuthFilter apiTokenAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                .requestMatchers(
                "/api/auth/login", "/api/auth/send-code", "/api/auth/register", "/api/auth/reset-password",
                "/api/auth/wechat/qr-scene", "/api/auth/wechat/qr-status",
                "/api/auth/wechat/login", "/api/auth/wechat/qr-confirm"
            ).permitAll()

                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(apiTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
