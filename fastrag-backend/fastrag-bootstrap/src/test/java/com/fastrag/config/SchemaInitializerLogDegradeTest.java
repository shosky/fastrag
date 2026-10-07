package com.fastrag.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * backlog 单元 S6：SchemaInitializer 吞错降级——Index/Column ensure skipped 的
 * log.info 升 log.warn 并携带根因异常类名。
 *
 * <p>背景（gap-review P1 实证）：smoke 环境中 kb_parse_strategy.file_id 索引创建失败
 * 被 INFO 掩盖，运维无感知。索引 ensure 失败属异常路径（duplicate 之外的失败同样被 skip），
 * 必须 warn 级别 + 根因类名可见。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchemaInitializerLogDegradeTest {

    @Mock
    private JdbcTemplate jdbc;

    private SchemaInitializer initializer;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        initializer = new SchemaInitializer(jdbc);
        logger = (Logger) LoggerFactory.getLogger(SchemaInitializer.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    /** 指定 SQL 片段命中时抛 RuntimeException(cause=SQLException)（模拟驱动层包装）。 */
    private void failSqlContaining(String fragment, SQLException cause) {
        doAnswer(inv -> {
            if (inv.getArgument(0, String.class).contains(fragment)) {
                throw new RuntimeException("StatementCallback failed", cause);
            }
            return null;
        }).when(jdbc).execute(anyString());
    }

    private List<String> warnMessages() {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Test
    void 索引ensure失败_kb_parse_strategy_file_id时_warn且带根因类名() {
        failSqlContaining("ADD INDEX idx_kb_parse_strategy_file",
                new SQLException("Duplicate key name 'idx_kb_parse_strategy_file'", "42000", 1061));

        initializer.initSchema();

        List<String> warns = warnMessages().stream()
                .filter(m -> m.contains("kb_parse_strategy.file_id"))
                .toList();
        assertThat(warns).as("索引 ensure skipped 必须 warn（原为 INFO 被掩盖）").isNotEmpty();
        assertThat(warns.get(0))
                .as("warn 消息需带根因异常类名，便于定位驱动层错误")
                .contains("SQLException");
    }

    @Test
    void 索引ensure失败_skill_uk_slug时_warn且带根因类名() {
        // 2026-10-07 拍板：idx_slug 原地升级为唯一索引 uk_slug（DROP+ADD 一条 ALTER）
        failSqlContaining("ADD UNIQUE INDEX uk_slug",
                new SQLException("Duplicate entry 'web-search' for key 'skill.uk_slug'", "23000", 1062));

        initializer.initSchema();

        List<String> warns = warnMessages().stream()
                .filter(m -> m.contains("skill.uk_slug"))
                .toList();
        assertThat(warns).isNotEmpty();
        assertThat(warns.get(0)).contains("SQLException");
    }
}
