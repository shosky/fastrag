package com.fastrag.module.knowledge.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbQaGenerationTask;
import com.fastrag.module.knowledge.mapper.KbQaGenerationTaskMapper;
import com.fastrag.module.knowledge.service.QaGenerationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 提炼任务消费者测试（tdd/qa-generation.md S5 清扫与互斥切片）。
 * 执行器为真实线程池（workers=1），异步行为以 CountDownLatch + timeout verify 驯服。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QaGenerationConsumerTest {

    @Mock private QaGenerationService svc;
    @Mock private KbQaGenerationTaskMapper taskMapper;

    private QaGenerationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new QaGenerationConsumer(svc, taskMapper);
        ReflectionTestUtils.setField(consumer, "workers", 1);
        ReflectionTestUtils.setField(consumer, "staleMinutes", 30);
        consumer.initExecutor();
    }

    @AfterEach
    void tearDown() {
        consumer.shutdownExecutor();
    }

    private Map<String, Object> message() {
        Map<String, Object> m = new HashMap<>();
        m.put("kbId", "kb1");
        m.put("fileId", "f1");
        return m;
    }

    @Test
    void 消费_无fileId时_丢弃() {
        Map<String, Object> m = new HashMap<>();
        m.put("kbId", "kb1");
        consumer.handleQaGeneration(m);
        verifyNoInteractions(svc);
    }

    @Test
    void 消费_同文件重复消息时_仅执行一次() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            running.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(svc).execute(any(), any(), any());

        consumer.handleQaGeneration(message());
        assertTrue(running.await(2, TimeUnit.SECONDS), "首个任务应已进入执行");
        consumer.handleQaGeneration(message()); // 进程内互斥命中，跳过
        release.countDown();

        verify(svc, timeout(2000).times(1)).execute(eq("kb1"), eq("f1"), any());
    }

    @Test
    void 消费_委托编排器时_参数逐字段透传() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        doAnswer(inv -> {
            done.countDown();
            return null;
        }).when(svc).execute(any(), any(), any());

        Map<String, Object> cfg = new HashMap<>();
        cfg.put("count", 5);
        cfg.put("mode", "append");
        cfg.put("model", "deepseek-v4");
        Map<String, Object> m = message();
        m.put("config", cfg);
        consumer.handleQaGeneration(m);

        assertTrue(done.await(2, TimeUnit.SECONDS));
        ArgumentCaptor<com.fastrag.module.knowledge.model.QaGenerationConfig> cap =
                ArgumentCaptor.forClass(com.fastrag.module.knowledge.model.QaGenerationConfig.class);
        verify(svc).execute(eq("kb1"), eq("f1"), cap.capture());
        assertEquals(5, cap.getValue().getCount());
        assertEquals("append", cap.getValue().getMode());
        assertEquals("deepseek-v4", cap.getValue().getModel());
    }

    @Test
    void 清扫_running超阈值时_标执行中断() throws Exception {
        KbQaGenerationTask stale = new KbQaGenerationTask();
        stale.setFileId("f-stale");
        stale.setKbId("kb1");
        stale.setStatus("running");
        stale.setUpdatedAt(LocalDateTime.now().minusMinutes(31));
        when(taskMapper.selectList(any())).thenReturn(List.of(stale));
        CountDownLatch done = new CountDownLatch(1);
        doAnswer(inv -> {
            done.countDown();
            return null;
        }).when(svc).execute(any(), any(), any());

        consumer.handleQaGeneration(message());

        assertTrue(done.await(2, TimeUnit.SECONDS));
        ArgumentCaptor<KbQaGenerationTask> cap = ArgumentCaptor.forClass(KbQaGenerationTask.class);
        verify(taskMapper).updateById(cap.capture());
        assertEquals("failed", cap.getValue().getStatus());
        assertTrue(cap.getValue().getError().contains("执行中断"));
    }

    @Test
    void 清扫_running心跳新鲜时_不动() throws Exception {
        KbQaGenerationTask fresh = new KbQaGenerationTask();
        fresh.setFileId("f1");
        fresh.setKbId("kb1");
        fresh.setStatus("running");
        fresh.setUpdatedAt(LocalDateTime.now().minusMinutes(5));
        when(taskMapper.selectList(any())).thenReturn(List.of(fresh));
        CountDownLatch done = new CountDownLatch(1);
        doAnswer(inv -> {
            done.countDown();
            return null;
        }).when(svc).execute(any(), any(), any());

        consumer.handleQaGeneration(message());

        assertTrue(done.await(2, TimeUnit.SECONDS));
        verify(taskMapper, never()).updateById(any());
    }
}
