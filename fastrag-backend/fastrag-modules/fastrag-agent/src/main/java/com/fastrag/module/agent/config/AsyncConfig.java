package com.fastrag.module.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Agent模块异步任务线程池配置类，为Agent运行提供独立的异步执行环境。
 *
 * <p>核心职责：
 * <ul>
 *   <li>通过@EnableAsync启用Spring异步调用支持</li>
 *   <li>定义名为"agentTaskExecutor"的专用线程池Bean，供Agent运行任务使用</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>核心线程数2，最大线程数5，队列容量50，适合中等并发的Agent运行场景</li>
 *   <li>线程命名前缀为"agent-run-"，便于在日志和监控中识别Agent相关线程</li>
 *   <li>Agent运行任务通过@Async("agentTaskExecutor")注解指定使用此线程池</li>
 * </ul></p>
 *
 * @see com.fastrag.module.agent.service.impl.AgentRunServiceImpl Agent运行服务实现（异步任务的使用者）
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("agentTaskExecutor")
    public Executor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("agent-run-");
        executor.initialize();
        return executor;
    }
}
