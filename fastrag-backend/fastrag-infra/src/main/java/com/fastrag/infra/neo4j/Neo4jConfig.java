package com.fastrag.infra.neo4j;

/**
 * Neo4j 图数据库驱动配置类，负责创建和管理 Neo4j Java Driver 实例。
 *
 * <p>配置的组件：Neo4j {@link org.neo4j.driver.Driver} Bean，作为 Neo4j 数据库的连接驱动。
 *
 * <p>生效条件：通过 {@code @ConditionalOnProperty(prefix = "neo4j", name = "enabled", havingValue = "true")}
 * 注解控制，仅当配置文件中设置 {@code neo4j.enabled=true} 时才会创建 Driver Bean。
 * 当 Neo4j 未启用时，该 Bean 不存在，Neo4jGraphStore 同样不会被创建，
 * 系统自动降级使用 {@link com.fastrag.infra.graph.MysqlGraphStore}。
 *
 * <p>配置参数（来自 application.yml 或环境变量）：
 * <ul>
 *   <li>{@code neo4j.uri} — Neo4j 连接地址，默认 {@code bolt://localhost:7687}</li>
 *   <li>{@code neo4j.user} — 用户名，默认 {@code neo4j}</li>
 *   <li>{@code neo4j.password} — 密码，默认为空（无密码时使用 {@link GraphDatabase#driver(String)} 直连）</li>
 * </ul>
 *
 * <p>与其他模块的交互：产生的 Driver Bean 被 {@link Neo4jGraphStore} 和 {@link Neo4jService} 注入使用。
 */
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class Neo4jConfig {

    @Value("${neo4j.enabled:false}")
    private boolean enabled;

    @Value("${neo4j.uri:bolt://localhost:7687}")
    private String uri;

    @Value("${neo4j.user:neo4j}")
    private String user;

    @Value("${neo4j.password:}")
    private String password;

    @ConditionalOnProperty(prefix = "neo4j", name = "enabled", havingValue = "true")
    @Bean
    public Driver neo4jDriver() {
        if (!enabled) {
            return null;
        }
        if (password == null || password.isBlank()) {
            return GraphDatabase.driver(uri);
        }
        return GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }
}
