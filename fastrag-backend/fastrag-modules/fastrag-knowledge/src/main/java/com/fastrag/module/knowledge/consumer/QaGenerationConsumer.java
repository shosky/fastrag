package com.fastrag.module.knowledge.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbQaGenerationTask;
import com.fastrag.module.knowledge.mapper.KbQaGenerationTaskMapper;
import com.fastrag.module.knowledge.model.QaGenerationConfig;
import com.fastrag.module.knowledge.service.QaGenerationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * 问答对提炼任务消费者（qa-generation，design/qa-generation.md §1 QaGenerationConsumerModule）。
 *
 * <p>复刻 GraphBuildConsumer 骨架：MQ 消息**即时 ack** + 长任务移出消费者线程
 * （qa-generation-worker 线程池，避免 2026-09-11 图谱构建 consumer_timeout 重投事故重演）
 * + 进程内文件级互斥（重投/连点只允许一个执行）。与图谱模板的三处有意偏离：
 * ①互斥粒度文件级（非 KB 级）；②互斥介质以 DB 任务行态为准（跨重启持久，spec REQ-A04）
 * 叠加进程内集合防同 JVM 竞态；③每次消息驱动一次陈旧 running 行清扫
 * （超 qa.generation.stale-minutes 无心跳 → failed「执行中断」）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QaGenerationConsumer {

    private final QaGenerationService qaGenerationService;
    private final KbQaGenerationTaskMapper taskMapper;

    @Value("${qa.generation.workers:3}")
    private int workers;
    @Value("${qa.generation.stale-minutes:30}")
    private int staleMinutes;

    /** 提炼执行器（应用内，独立于 MQ 消费者线程） */
    private ExecutorService executor;

    /** 按 file 的执行互斥集合：同文件并发提炼（MQ 重投/连点）只允许一个执行 */
    private final Set<String> runningFiles = ConcurrentHashMap.newKeySet();

    @PostConstruct
    void initExecutor() {
        executor = Executors.newFixedThreadPool(Math.max(1, workers), r -> {
            Thread t = new Thread(r, "qa-generation-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void shutdownExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /** MQ 消费入口：只做调度与即时 ack，实际提炼提交到应用内执行器 */
    @RabbitListener(queues = "fastrag.qa-generation.queue", concurrency = "3")
    public void handleQaGeneration(Map<String, Object> message) {
        String kbId = message == null ? null : (String) message.get("kbId");
        String fileId = message == null ? null : (String) message.get("fileId");
        if (fileId == null || fileId.isBlank()) {
            log.warn("[QaGeneration] message without fileId, dropped: {}", message);
            return;
        }
        sweepStaleTasks();
        if (!runningFiles.add(fileId)) {
            log.warn("[QaGeneration] file={} already generating, duplicate message skipped", fileId);
            return;
        }
        try {
            executor.submit(() -> {
                try {
                    qaGenerationService.execute(kbId, fileId, extractConfig(message));
                } catch (Exception e) {
                    log.error("[QaGeneration] execute error for file={}: {}", fileId, e.getMessage(), e);
                    markFailedQuietly(fileId, e.getMessage());
                } finally {
                    runningFiles.remove(fileId);
                }
            });
        } catch (RejectedExecutionException e) {
            runningFiles.remove(fileId);
            markFailedQuietly(fileId, "执行器拒绝任务");
        }
    }

    // ==================== 内部 ====================

    /** 消息 config 字段 → 参数对象（缺字段逐个容错，缺省值交由 execute 内 normalized 统一补齐） */
    private QaGenerationConfig extractConfig(Map<String, Object> message) {
        Object raw = message == null ? null : message.get("config");
        if (raw instanceof QaGenerationConfig config) {
            return config;
        }
        QaGenerationConfig config = new QaGenerationConfig();
        if (raw instanceof Map<?, ?> map) {
            if (map.get("count") instanceof Number n) {
                config.setCount(n.intValue());
            }
            if (map.get("customInstruction") != null) {
                config.setCustomInstruction(String.valueOf(map.get("customInstruction")));
            }
            if (map.get("mode") != null) {
                config.setMode(String.valueOf(map.get("mode")));
            }
            if (map.get("model") != null) {
                config.setModel(String.valueOf(map.get("model")));
            }
        }
        return config;
    }

    /** 陈旧 running 行清扫：心跳超阈值的任务标 failed「执行中断」（持久化互斥的自愈路径） */
    private void sweepStaleTasks() {
        try {
            List<KbQaGenerationTask> running = taskMapper.selectList(
                    new LambdaQueryWrapper<KbQaGenerationTask>()
                            .eq(KbQaGenerationTask::getStatus, "running"));
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(staleMinutes);
            for (KbQaGenerationTask task : running) {
                LocalDateTime heart = task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getCreatedAt();
                if (heart != null && heart.isBefore(threshold)) {
                    task.setStatus("failed");
                    task.setError("执行中断（超时无心跳，已自动恢复）");
                    task.setUpdatedAt(LocalDateTime.now());
                    taskMapper.updateById(task);
                    log.warn("[QaGeneration] stale running task recovered as failed: file={}", task.getFileId());
                }
            }
        } catch (Exception e) {
            log.warn("[QaGeneration] stale sweep failed: {}", e.getMessage());
        }
    }

    /** 执行异常时兜底落终态（不向 MQ 抛异常——消息已即时 ack） */
    private void markFailedQuietly(String fileId, String message) {
        try {
            KbQaGenerationTask task = taskMapper.selectById(fileId);
            if (task != null && "running".equals(task.getStatus())) {
                task.setStatus("failed");
                task.setError(message);
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);
            }
        } catch (Exception ignored) {
            // 兜底失败仅告警，不再上抛
        }
    }
}
