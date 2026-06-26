package com.fastrag.infra.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class Neo4jConfig {
    @Value("${neo4j.uri:bolt://neo4j:7687}") private String uri;
    @Value("${neo4j.username:neo4j}") private String username;
    @Value("${neo4j.password:fastrag123}") private String password;

    @Bean
    public Driver neo4jDriver() {
        Config config = Config.builder()
            .withConnectionTimeout(10, TimeUnit.SECONDS)
            .withMaxConnectionLifetime(1, TimeUnit.HOURS)
            .withMaxConnectionPoolSize(50)
            .build();
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
    }
}
