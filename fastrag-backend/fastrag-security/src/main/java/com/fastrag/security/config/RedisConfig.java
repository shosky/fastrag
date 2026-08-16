package com.fastrag.security.config;

/**
 * Redis 配置类，提供 StringRedisTemplate Bean 用于 JWT Token 黑名单等安全相关缓存操作。
 *
 * <p>配置的组件：Spring Data Redis 的 {@link org.springframework.data.redis.core.StringRedisTemplate}，
 * 基于 {@link org.springframework.data.redis.connection.RedisConnectionFactory} 自动装配。
 *
 * <p>生效方式：通过 {@code @Configuration} 注解被 Spring 自动扫描加载，
 * Redis 连接参数来自 {@code application.yml} 中的 {@code spring.data.redis.*} 前缀配置。
 *
 * <p>与其他模块的交互：产生的 StringRedisTemplate Bean 被 {@link com.fastrag.security.filter.JwtAuthFilter}
 * 注入，用于检查 JWT Token 是否在黑名单中（登出时将 Token 加入黑名单使其立即失效）。
 */

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
