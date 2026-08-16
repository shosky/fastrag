package com.fastrag.module.agent.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Agent模块的Spring MVC扩展配置类，用于注册自定义参数解析器。
 *
 * <p>核心职责：
 * <ul>
 *   <li>实现WebMvcConfigurer接口，向Spring MVC注册自定义的HandlerMethodArgumentResolver</li>
 *   <li>注册{@link CurrentUserArgumentResolver}，使Controller方法可以通过@CurrentUser注解直接获取当前登录用户信息</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>通过构造器注入CurrentUserArgumentResolver，在addArgumentResolvers回调中将其添加到解析器列表</li>
 *   <li>使用@RequiredArgsConstructor（Lombok）自动生成构造函数</li>
 * </ul></p>
 *
 * @see CurrentUserArgumentResolver 当前用户参数解析器
 * @see CurrentUser 当前用户注解
 */
@Configuration
@RequiredArgsConstructor
public class AgentWebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
