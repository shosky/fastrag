package com.fastrag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public SchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initSchema() {
        log.info("Checking and creating missing tables...");

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sensitive_word (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    word VARCHAR(128) NOT NULL,
                    category VARCHAR(64),
                    level VARCHAR(16),
                    replacement VARCHAR(128) DEFAULT '***',
                    enabled TINYINT DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("Table sensitive_word OK");
        } catch (Exception e) {
            log.error("Failed to create sensitive_word: {}", e.getMessage());
        }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_team (
                    id VARCHAR(32) PRIMARY KEY,
                    name VARCHAR(128) NOT NULL,
                    description VARCHAR(256),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("Table sys_team OK");
        } catch (Exception e) {
            log.error("Failed to create sys_team: {}", e.getMessage());
        }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_team_member (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    team_id VARCHAR(32) NOT NULL,
                    user_id VARCHAR(32) NOT NULL,
                    INDEX idx_team_id (team_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("Table sys_team_member OK");
        } catch (Exception e) {
            log.error("Failed to create sys_team_member: {}", e.getMessage());
        }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_dictionary (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    dict_type VARCHAR(64) NOT NULL,
                    dict_key VARCHAR(128) NOT NULL,
                    dict_value TEXT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("Table sys_dictionary OK");
        } catch (Exception e) {
            log.error("Failed to create sys_dictionary: {}", e.getMessage());
        }

        log.info("Schema initialization completed.");
    }
}
