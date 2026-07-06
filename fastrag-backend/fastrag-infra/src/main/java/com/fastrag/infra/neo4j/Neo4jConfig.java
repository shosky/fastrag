package com.fastrag.infra.neo4j;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Neo4j 驱动配置
 *
 * <p>当 neo4j.enabled=true 时创建 Driver bean。
 * 通过环境变量注入：NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD
 */
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
