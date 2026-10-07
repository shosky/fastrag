package com.fastrag.module.operation.listener;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.event.SysAuditLogEvent;
import com.fastrag.module.operation.entity.SysAuditLog;
import com.fastrag.module.operation.mapper.SysAuditLogMapper;
import com.fastrag.module.operation.model.AuditOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * backlog 单元 S5（gap-review P1-11）：审计 outcome 与异步化。
 *
 * <ul>
 *   <li>监听方法 @Async——审计写入离开发布方关键路径；</li>
 *   <li>写失败不外抛 + 失败计数 + 每分钟至多一条汇总 warn（防日志风暴）；</li>
 *   <li>outcome 语义扩展落位：status 列承载 success/failed/rejected/filtered 枚举。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysAuditLogEventListenerTest {

    @Mock
    private SysAuditLogMapper auditLogMapper;

    private SysAuditLogEventListener listener;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        listener = new SysAuditLogEventListener(auditLogMapper);
        logger = (Logger) LoggerFactory.getLogger(SysAuditLogEventListener.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private SysAuditLogEvent event() {
        return new SysAuditLogEvent(this, "u1", "alice", "knowledge",
                ActionType.kb_created, "kb-1", "create kb", "success", "127.0.0.1");
    }

    private long warnCount() {
        return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
    }

    @Test
    void 监听方法_标注Async_审计写入离开发布方关键路径() throws Exception {
        assertThat(SysAuditLogEventListener.class
                .getMethod("handleAuditLogEvent", SysAuditLogEvent.class)
                .isAnnotationPresent(Async.class))
                .as("S5: @EventListener + @Async 组合生效（@EnableAsync 已在 FastRagApplication 开启）")
                .isTrue();
    }

    @Test
    void 事件到达时_实体字段透传入库() {
        listener.handleAuditLogEvent(event());

        ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        SysAuditLog saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("u1");
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getModule()).isEqualTo("knowledge");
        assertThat(saved.getAction()).isEqualTo(ActionType.kb_created.name());
        assertThat(saved.getTarget()).isEqualTo("kb-1");
        assertThat(saved.getDetail()).isEqualTo("create kb");
        assertThat(saved.getIp()).isEqualTo("127.0.0.1");
        assertThat(saved.getStatus()).isEqualTo("success");
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void 写失败时_不外抛且失败计数递增() {
        doThrow(new RuntimeException("db down")).when(auditLogMapper).insert(any(SysAuditLog.class));

        assertThatCode(() -> listener.handleAuditLogEvent(event())).doesNotThrowAnyException();

        assertThat(listener.failureCount()).isEqualTo(1);
    }

    @Test
    void 写失败周期warn_连续失败时_每分钟至多一条汇总() {
        doThrow(new RuntimeException("db down")).when(auditLogMapper).insert(any(SysAuditLog.class));

        for (int i = 0; i < 5; i++) {
            listener.handleAuditLogEvent(event());
        }

        assertThat(listener.failureCount()).isEqualTo(5);
        assertThat(warnCount()).as("S5: 失败计数累计 + 周期 warn，避免每失败刷一条").isEqualTo(1);
        String warn = appender.list.get(appender.list.size() - 1).getFormattedMessage();
        assertThat(warn).as("首条失败立即汇总，窗口内后续失败并入计数").contains("sys_audit_log");
    }

    @Test
    void outcome枚举_含扩展语义常量() {
        assertThat(AuditOutcome.SUCCESS.code()).isEqualTo("success");
        assertThat(AuditOutcome.FAILED.code()).isEqualTo("failed");
        assertThat(AuditOutcome.REJECTED.code()).isEqualTo("rejected");
        assertThat(AuditOutcome.FILTERED.code()).isEqualTo("filtered");
    }
}
