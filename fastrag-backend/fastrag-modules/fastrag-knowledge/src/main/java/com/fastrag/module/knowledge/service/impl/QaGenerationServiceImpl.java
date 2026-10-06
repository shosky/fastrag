package com.fastrag.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.infra.rabbitmq.MessagePublisher;
import com.fastrag.module.graph.util.BenchmarkResponseParser;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbQaGenerationTask;
import com.fastrag.module.knowledge.entity.KbQaPair;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbQaGenerationTaskMapper;
import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.knowledge.model.QaGenerationConfig;
import com.fastrag.module.knowledge.model.QaGenerationTaskView;
import com.fastrag.module.knowledge.service.QaGenerationService;
import com.fastrag.module.knowledge.util.ExcelQaPairImporter;
import com.fastrag.ai.llm.LlmService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 问答对提炼编排服务实现（qa-generation 唯一深模块，design/qa-generation.md §1 QaGenerationModule）。
 *
 * <p>触发受理（参数校验 → 文件级持久化互斥 → 任务行 upsert → 投递）与
 * 生成编排（提取分片迭代 → LLM 提炼 → 防御解析 → 静态闸门 → 逐片实时落库 → 统计回写）。
 * 提取单位 = 父分片优先，存量旧管道文件（无父子结构）回退文本分片；
 * 产出逐条实时落库（问答对页即时可见，草稿不参与召回，确认才是检索闸门）；
 * replace 的删旧在任务开始时执行——中途崩溃残留部分新对，重跑 replace 即自愈（产出可再生成，可见性优先）。
 *
 * <p>复用件：归一化判重口径 = ExcelQaPairImporter.normalizeQuestion（与 Excel 导入同一函数）；
 * LLM 响应防御解析 = BenchmarkResponseParser（knowledge→graph-eval 依赖已声明）；
 * 模型路由 = model 表 api_url/api_key_ref 解析 + 失败回退默认网关（BenchmarkGenerationHelper 同款）。
 * MessagePublisher 经 ObjectProvider 延迟解析（规避 经 MessagePublisher→IngestionHandler 的构造环，
 * 与 IngestionConsumer 同款手法）。
 */
@Service
@RequiredArgsConstructor
public class QaGenerationServiceImpl implements QaGenerationService {

    private static final Logger log = LoggerFactory.getLogger(QaGenerationServiceImpl.class);

    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_PARTIAL = "partial";
    private static final String STATUS_FAILED = "failed";
    private static final String SOURCE_AI = "ai";
    private static final String SOURCE_MANUAL = "manual";
    private static final String CHUNK_TYPE_PARENT = "parent";
    private static final int MAX_TOKENS = 4096;
    private static final int MIN_QUESTION_LEN = 5;
    private static final int MIN_ANSWER_LEN = 2;

    private final KbQaGenerationTaskMapper taskMapper;
    private final KbQaPairMapper qaPairMapper;
    private final KbChunkMapper chunkMapper;
    private final KbFileMapper fileMapper;
    private final LlmService llmService;
    private final ObjectProvider<MessagePublisher> messagePublisherProvider;
    private final JdbcTemplate jdbcTemplate;

    /** running 行心跳超过该分钟数视为执行中断（消费端清扫与触发端判定共用阈值） */
    @Value("${qa.generation.stale-minutes:30}")
    private int staleMinutes;
    @Value("${qa.generation.llm-timeout-seconds:180}")
    private int llmTimeoutSeconds;

    @Override
    public QaGenerationTaskView trigger(String kbId, String fileId, QaGenerationConfig raw) {
        QaGenerationConfig config = (raw == null ? new QaGenerationConfig() : raw).normalized();

        KbFile file = fileMapper.selectById(fileId);
        if (file == null || !kbId.equals(file.getKbId())) {
            throw BusinessException.badRequest("文件不存在或不属于该知识库");
        }

        List<KbChunk> parents = loadExtractChunks(kbId, fileId);
        if (parents.isEmpty() && !"completed".equals(file.getStatus())) {
            throw BusinessException.badRequest("文件尚未完成解析，无法提炼问答对");
        }

        // 文件级持久化互斥：running 且心跳新鲜 → 拒绝；陈旧 running 先标中断再放行
        KbQaGenerationTask existing = taskMapper.selectById(fileId);
        if (existing != null && STATUS_RUNNING.equals(existing.getStatus())) {
            if (!isStale(existing)) {
                throw BusinessException.badRequest("该文件已有进行中的提炼任务");
            }
            existing.setStatus(STATUS_FAILED);
            existing.setError("执行中断（超时无心跳，已自动恢复）");
            existing.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(existing);
        }

        KbQaGenerationTask task = new KbQaGenerationTask();
        task.setFileId(fileId);
        task.setKbId(kbId);
        task.setStatus(STATUS_RUNNING);
        task.setTotalChunks(parents.size());
        task.setSucceededChunks(0);
        task.setFailedChunks(0);
        task.setConfig(config.toJson());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        if (existing == null) {
            taskMapper.insert(task);
        } else {
            taskMapper.updateById(task);
        }

        try {
            MessagePublisher publisher = messagePublisherProvider.getIfAvailable();
            if (publisher == null) {
                throw new IllegalStateException("MessagePublisher bean 不可用");
            }
            Map<String, Object> message = new HashMap<>();
            message.put("kbId", kbId);
            message.put("fileId", fileId);
            message.put("config", config);
            publisher.publishQaGeneration(message);
        } catch (Exception e) {
            // 无同步降级（design 缝 2 偏差）：任务行落 failed + 业务错误返回
            task.setStatus(STATUS_FAILED);
            task.setError("队列不可用: " + e.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            throw BusinessException.badRequest("消息队列不可用，提炼任务未能受理");
        }
        return QaGenerationTaskView.from(task);
    }

    @Override
    public QaGenerationTaskView status(String kbId, String fileId) {
        KbQaGenerationTask task = taskMapper.selectById(fileId);
        return task == null ? null : QaGenerationTaskView.from(task);
    }

    @Override
    public void execute(String kbId, String fileId, QaGenerationConfig raw) {
        // 行态门：任务行非 running（已终态/被新任务覆盖）→ 静默丢弃（MQ 重投双跑防线）
        KbQaGenerationTask task = taskMapper.selectById(fileId);
        if (task == null || !STATUS_RUNNING.equals(task.getStatus())) {
            log.warn("[QaGeneration] stale/duplicate message dropped for file={} (task={})",
                    fileId, task == null ? "none" : task.getStatus());
            return;
        }
        QaGenerationConfig config = (raw == null ? new QaGenerationConfig() : raw).normalized();

        KbFile file = fileMapper.selectById(fileId);
        String fileName = file != null ? file.getName() : null;
        List<KbChunk> parents = loadExtractChunks(kbId, fileId);
        task.setTotalChunks(parents.size());

        // 判重基线：manual 恒在基线；replace 时同文件旧 ai 对不入基线（旧对在任务开始时即删，产出逐片实时落库可见）
        boolean replace = !QaGenerationConfig.MODE_APPEND.equals(config.getMode());
        List<KbQaPair> existingPairs = qaPairMapper.selectList(
                new LambdaQueryWrapper<KbQaPair>().eq(KbQaPair::getKbId, kbId));
        Set<String> seen = new HashSet<>();
        for (KbQaPair p : existingPairs) {
            boolean sameFileAi = fileId.equals(p.getFileId()) && SOURCE_AI.equals(p.getSource());
            if (replace && sameFileAi) {
                continue;
            }
            seen.add(ExcelQaPairImporter.normalizeQuestion(p.getQuestion()));
        }
        // replace 语义：任务开始即删该文件旧 ai 对（manual 对不触碰）——产出逐片落库，用户在问答对页实时可见
        if (replace) {
            qaPairMapper.delete(new LambdaQueryWrapper<KbQaPair>()
                    .eq(KbQaPair::getKbId, kbId)
                    .eq(KbQaPair::getFileId, fileId)
                    .eq(KbQaPair::getSource, SOURCE_AI));
        }

        int succeeded = 0;
        int failed = 0;
        int pairCount = 0;
        for (KbChunk chunk : parents) {
            int[] delta = generateForChunk(chunk, config, seen, kbId, fileId, fileName);
            succeeded += delta[0];
            failed += delta[1];
            pairCount += delta[2];
            // 每片完结即回写：进度可观测 + 兼作陈旧心跳
            task.setSucceededChunks(succeeded);
            task.setFailedChunks(failed);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }

        // 四态判定：全败=failed；有成有败=partial；无失败（含零分片空文档、合法零产出）=success
        String status = failed > 0 ? (succeeded > 0 ? STATUS_PARTIAL : STATUS_FAILED) : STATUS_SUCCESS;
        finalizeTask(task, status,
                failed > 0 ? "部分产出被跳过或失败：" + failed : null, succeeded, failed);
        // 成功路径静默会让「零分片/零产出」看起来像卡死——终态必须有一行可见日志
        log.info("[QaGeneration] task finished: file={}, status={}, chunks={}/{}, pairs={}, mode={}",
                fileId, status, succeeded, parents.size(), pairCount, config.getMode());
    }

    // ==================== 内部：单分片提炼 ====================

    /**
     * 单父分片提炼：LLM 调用（失败重试 1 次）→ 防御解析 → 静态闸门 → 逐条落库（实时可见）。
     *
     * @return {成功分片增量, 失败增量, 落库条数}——失败 = 分片级失败（LLM/解析两轮均败）+ 闸门拦截产出数（合并记账）
     */
    private int[] generateForChunk(KbChunk chunk, QaGenerationConfig config, Set<String> seen,
                                   String kbId, String fileId, String fileName) {
        String prompt = buildPrompt(chunk, config);
        List<Map<String, Object>> parsed = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String response = callLlm(config.getModel(), prompt);
                parsed = BenchmarkResponseParser.parseQuestions(response);
                break;
            } catch (Exception e) {
                log.warn("[QaGeneration] chunk {} attempt {} failed: {}", chunk.getId(), attempt, e.getMessage());
            }
        }
        if (parsed == null) {
            return new int[]{0, 1, 0};
        }

        int rejected = 0;
        int accepted = 0;
        for (Map<String, Object> item : parsed) {
            Object q = item == null ? null : item.get("question");
            Object a = item == null ? null : item.get("answer");
            String question = q == null ? null : String.valueOf(q).trim();
            String answer = a == null ? "" : String.valueOf(a).trim();
            // 静态闸门①：长度护栏（问题<5 字 / 答案<2 字丢弃）
            if (question == null || question.length() < MIN_QUESTION_LEN || answer.length() < MIN_ANSWER_LEN) {
                rejected++;
                continue;
            }
            // 静态闸门②：归一化判重（与 Excel 导入同一口径函数）
            String normalized = ExcelQaPairImporter.normalizeQuestion(question);
            if (normalized == null || seen.contains(normalized)) {
                rejected++;
                continue;
            }
            seen.add(normalized);
            KbQaPair pair = new KbQaPair();
            pair.setId(generateId());
            pair.setKbId(kbId);
            pair.setFileId(fileId);
            pair.setFileName(fileName);
            pair.setQuestion(question);
            pair.setAnswer(answer);
            pair.setSource(SOURCE_AI);
            pair.setStatus("draft");
            pair.setSourceChunkId(chunk.getId());
            pair.setCreatedAt(LocalDateTime.now());
            qaPairMapper.insert(pair);
            accepted++;
        }

        if (accepted > 0) {
            return new int[]{1, rejected, accepted};
        }
        if (parsed.isEmpty()) {
            // 分片无可答题材料，出零条属正常完结（REQ-B02）
            return new int[]{1, 0, 0};
        }
        return new int[]{0, Math.max(rejected, 1), 0};
    }

    private String buildPrompt(KbChunk chunk, QaGenerationConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是知识库问答对生成助手。请仅基于下面的文档片段，生成 ")
                .append(config.getCount()).append(" 个问答对。\n");
        sb.append("要求：\n");
        sb.append("1. 问题必须自包含：脱离本片段也能被理解，模拟真实用户的提问方式；\n");
        sb.append("2. 答案只能陈述片段中出现的内容，严禁引入片段之外的知识；\n");
        sb.append("3. 如果片段不包含可用于出题的材料，返回空数组 []。\n");
        if (config.getCustomInstruction() != null) {
            sb.append("额外要求：").append(config.getCustomInstruction()).append('\n');
        }
        sb.append("\n文档片段");
        if (chunk.getTitle() != null && !chunk.getTitle().isBlank()) {
            sb.append("（所属标题：").append(chunk.getTitle()).append("）");
        }
        sb.append("：\n").append(chunk.getContent() == null ? "" : chunk.getContent());
        sb.append("\n\n只输出 JSON 数组，不要输出任何其他文字，格式：[{\"question\":\"...\",\"answer\":\"...\"}]");
        return sb.toString();
    }

    /** 模型路由：model 表解析 api_url/api_key_ref，失败/缺省回退默认网关（BenchmarkGenerationHelper 同款） */
    private String callLlm(String model, String prompt) {
        String m = model == null || model.isBlank() ? "default" : model;
        if ("default".equals(m)) {
            return llmService.chatWithTimeout(m, prompt, null, null, false, llmTimeoutSeconds, MAX_TOKENS);
        }
        try {
            List<Map<String, Object>> records = jdbcTemplate.queryForList(
                    "SELECT api_url, api_key_ref FROM model WHERE code = ? LIMIT 1", m);
            if (!records.isEmpty()) {
                Map<String, Object> rec = records.get(0);
                String apiUrl = (String) rec.get("api_url");
                String apiKeyRef = (String) rec.get("api_key_ref");
                return llmService.chatWithTimeout(m, prompt,
                        apiUrl != null && !apiUrl.isBlank() ? apiUrl : null,
                        apiKeyRef != null && !apiKeyRef.isBlank() ? apiKeyRef : null,
                        false, llmTimeoutSeconds, MAX_TOKENS);
            }
        } catch (Exception e) {
            log.warn("[QaGeneration] model '{}' resolve failed, fallback default gateway: {}", m, e.getMessage());
        }
        return llmService.chatWithTimeout(m, prompt, null, null, false, llmTimeoutSeconds, MAX_TOKENS);
    }

    // ==================== 内部：辅助 ====================

    private List<KbChunk> loadExtractChunks(String kbId, String fileId) {
        // 提取单位 = 父分片优先；存量兼容：旧管道产物无父子结构（全为 text 分片），回退全部文本分片
        List<KbChunk> parents = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getKbId, kbId)
                .eq(KbChunk::getFileId, fileId)
                .eq(KbChunk::getChunkType, CHUNK_TYPE_PARENT)
                .orderByAsc(KbChunk::getChunkIndex));
        if (!parents.isEmpty()) {
            return parents;
        }
        return chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getKbId, kbId)
                .eq(KbChunk::getFileId, fileId)
                .ne(KbChunk::getChunkType, "image")
                .orderByAsc(KbChunk::getChunkIndex));
    }

    private boolean isStale(KbQaGenerationTask task) {
        LocalDateTime heart = task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getCreatedAt();
        return heart == null || heart.isBefore(LocalDateTime.now().minusMinutes(staleMinutes));
    }

    private void finalizeTask(KbQaGenerationTask task, String status, String error, int succeeded, int failed) {
        task.setStatus(status);
        task.setError(error);
        task.setSucceededChunks(succeeded);
        task.setFailedChunks(failed);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /** 生成与 ASSIGN_ID 策略兼容的雪花 ID（同 QaPairServiceImpl 口径） */
    private static String generateId() {
        String hex = Long.toHexString(System.currentTimeMillis());
        while (hex.length() < 12) {
            hex = "0" + hex;
        }
        return hex + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
