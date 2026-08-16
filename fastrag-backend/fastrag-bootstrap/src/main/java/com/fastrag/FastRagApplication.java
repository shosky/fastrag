package com.fastrag;

/**
 * FastRag 应用程序启动类。
 * <p>作为整个 RAG（检索增强生成）平台的 Spring Boot 入口点，负责初始化应用的运行环境。
 *
 * <p>核心配置说明：
 * <ul>
 *   <li>{@code @ComponentScan} - 扫描 com.fastrag 包下所有组件，确保各子模块的 Bean 均被注册</li>
 *   <li>{@code @MapperScan} - 扫描各业务模块的 MyBatis Mapper 接口（按 com.fastrag.module.*.mapper 路径匹配）</li>
 *   <li>{@code @EnableRabbit} - 启用 RabbitMQ 消息队列支持，用于文档摄入、图谱构建等异步任务</li>
 *   <li>{@code @EnableAsync} - 启用异步方法执行能力，支持 @Async 注解的异步处理</li>
 *   <li>{@code @EnableScheduling} - 启用定时任务支持，用于定期检测、数据清理等计划任务</li>
 * </ul>
 *
 * <p>模块关系：本类是 fastrag-bootstrap 启动模块的核心，通过 ComponentScan 聚合
 * fastrag-common（公共组件）、fastrag-security（安全认证）、fastrag-iam（用户权限）、
 * fastrag-knowledge（知识库核心）、fastrag-tools（工具/Agent）等业务模块。
 */
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = "com.fastrag")
@MapperScan("com.fastrag.module.*.mapper")
@EnableRabbit
@EnableAsync
@EnableScheduling
public class FastRagApplication {
    public static void main(String[] args) {
        SpringApplication.run(FastRagApplication.class, args);
    }
}
