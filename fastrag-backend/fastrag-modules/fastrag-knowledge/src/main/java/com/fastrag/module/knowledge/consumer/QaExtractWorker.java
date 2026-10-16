package com.fastrag.module.knowledge.consumer;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbQaExtractTask;
import com.fastrag.module.knowledge.entity.KbQaPair;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KbQaExtractTaskMapper;
import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问答抽取执行器：消费 kb_qa_extract_task 中 pending 的任务，把文档分块交给 LLM 拆成"一问一答"，
 * 结果以 source='ai'、status='draft' 写入 kb_qa_pair，人工「确认/全部入库」后参与召回。
 * 原 QaExtractServiceImpl 只做任务行 CRUD（无执行者），本类补齐"一键自动拆分为一问一答"的实际执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaExtractWorker {

    private final KbQaExtractTaskMapper taskMapper;
    private final KbChunkMapper chunkMapper;
    private final KbQaPairMapper qaPairMapper;
    private final ModelRecordMapper modelRecordMapper;
    private final LlmService llmService;

    private static final int BATCH_SIZE = 3;              // 每批处理的分块数（批间回写进度）
    private static final int DEFAULT_MAX_QUESTIONS = 50;  // 单任务产出问答对上限
    private static final int CHUNK_TEXT_LIMIT = 1500;     // 单块喂给模型的字符上限
    private static final String FALLBACK_MODEL_CODE = "qwen3-72b";

    /** 5s 轮询认领 pending 任务；调度模式与 KbSyncServiceImpl.scheduleSync 一致 */
    @Scheduled(fixedDelay = 5_000)
    public void poll() {
        try {
            var pending = taskMapper.selectList(new LambdaQueryWrapper<KbQaExtractTask>()
                    .eq(KbQaExtractTask::getStatus, "pending")
                    .orderByAsc(KbQaExtractTask::getCreatedAt).last("LIMIT 1"));
            if (pending.isEmpty()) return;
            var task = pending.get(0);
            // 条件更新认领：并发/多实例下只有一个执行者能把它从 pending 推到 running
            int claimed = taskMapper.update(null, new LambdaUpdateWrapper<KbQaExtractTask>()
                    .eq(KbQaExtractTask::getId, task.getId())
                    .eq(KbQaExtractTask::getStatus, "pending")
                    .set(KbQaExtractTask::getStatus, "running"));
            if (claimed == 0) return;
            process(task);
        } catch (Exception e) {
            log.warn("问答抽取轮询异常", e);
        }
    }

    private void process(KbQaExtractTask task) {
        log.info("开始问答抽取任务: {} kb={} model={}", task.getId(), task.getKbId(), task.getLlmModel());
        try {
            List<String> fileIds = parseFileIds(task);
            var w = new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getKbId, task.getKbId());
            if (!fileIds.isEmpty()) w.in(KbChunk::getFileId, fileIds);
            w.orderByAsc(KbChunk::getFileId).orderByAsc(KbChunk::getChunkIndex);
            List<KbChunk> chunks = chunkMapper.selectList(w);
            if (chunks.isEmpty()) {
                finish(task, "failed", Map.of("error", fileIds.isEmpty()
                        ? "知识库暂无已解析分块，请先上传并处理文档" : "所选文件尚未解析出分块，请先处理文档"));
                return;
            }

            Map<String, Object> params = parseParams(task);
            int batchSize = Math.max(1, intParam(params, "batchSize", BATCH_SIZE));
            int maxQuestions = Math.max(1, intParam(params, "maxQuestions", DEFAULT_MAX_QUESTIONS));
            ModelRecord model = resolveModel(task.getLlmModel());
            log.info("问答抽取任务 {} 使用模型: {} (endpoint={})", task.getId(),
                    model == null ? FALLBACK_MODEL_CODE : model.getCode(),
                    model == null || model.getApiUrl() == null ? "默认AI网关" : model.getApiUrl());

            int total = chunks.size(), completed = 0, generated = 0, skipped = 0, llmFailed = 0;
            String llmError = null;
            updateProgress(task.getId(), total, completed);

            for (int i = 0; i < chunks.size() && generated < maxQuestions; i += batchSize) {
                if (isStopped(task.getId())) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("generated", generated); r.put("skipped", skipped); r.put("processedChunks", completed);
                    finish(task, "stopped", r);
                    return;
                }
                for (KbChunk chunk : chunks.subList(i, Math.min(i + batchSize, chunks.size()))) {
                    ExtractOutcome outcome = extractFromChunk(chunk, model);
                    if (outcome.getLlmError() != null) { llmFailed++; llmError = outcome.getLlmError(); }
                    for (QaCandidate cand : outcome.getCandidates()) {
                        if (generated >= maxQuestions) break;
                        if (isBlank(cand.getQuestion()) || isBlank(cand.getAnswer())) continue;
                        String question = trimTo(cand.getQuestion().trim(), 500);
                        if (exists(task.getKbId(), chunk.getFileId(), question)) { skipped++; continue; }
                        insertQaPair(task.getKbId(), chunk, question, cand.getAnswer().trim());
                        generated++;
                    }
                    completed++;
                }
                updateProgress(task.getId(), total, completed);
            }

            // 全程一次都没产出且模型报错：明确失败并给出上游原因，避免"空成功"
            if (generated == 0 && llmFailed > 0) {
                finish(task, "failed", Map.of("error", llmError == null ? "模型调用失败" : llmError,
                        "processedChunks", completed));
                return;
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("generated", generated); r.put("skipped", skipped);
            r.put("processedChunks", completed); r.put("chunkFailed", llmFailed);
            finish(task, "completed", r);
        } catch (Exception e) {
            log.error("问答抽取任务失败: {}", task.getId(), e);
            finish(task, "failed", Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    /** 单分块抽取：LLM 返回 JSON 数组 [{question,answer}]；调用失败时在 outcome 里带出原因 */
    private ExtractOutcome extractFromChunk(KbChunk chunk, ModelRecord model) {
        var outcome = new ExtractOutcome();
        String text = chunk.getContent() == null ? "" : chunk.getContent();
        if (text.length() > CHUNK_TEXT_LIMIT) text = text.substring(0, CHUNK_TEXT_LIMIT);
        if (text.isBlank()) return outcome;
        String prompt = """
                你是知识库采编助手。请从下面的文档片段中抽取"一问一答"式知识问答，只返回 JSON 数组：
                [{"question":"用户可能的提问","answer":"依据原文的准确回答"}]
                要求：
                1. question 用用户口语化的提问方式，不要照抄原文标题；
                2. answer 必须严格来自原文，不要编造或补充外部信息；
                3. 片段中没有可抽取的问答时返回 []；
                4. 只输出 JSON，不要任何解释文字或 Markdown 代码块。

                文档片段：
                """ + text;
        String modelCode = model != null ? model.getCode() : FALLBACK_MODEL_CODE;
        String resp = llmService.chat(modelCode, prompt,
                model != null ? model.getApiUrl() : null,
                model != null ? resolveKey(model) : null);
        if (resp == null || resp.isBlank()) { outcome.setLlmError("模型无响应"); return outcome; }
        if (resp.startsWith("模型调用失败")) { outcome.setLlmError(resp); return outcome; }
        outcome.setCandidates(parseCandidates(resp));
        return outcome;
    }

    private List<QaCandidate> parseCandidates(String resp) {
        String json = resp.trim();
        if (json.contains("```")) json = json.replaceAll("```json?", "").replaceAll("```", "").trim();
        int start = json.indexOf('['), end = json.lastIndexOf(']');
        if (start < 0 || end <= start) return List.of();
        try {
            List<QaCandidate> list = JSONUtil.toList(json.substring(start, end + 1), QaCandidate.class);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            log.warn("解析问答抽取结果失败: {}", e.getMessage());
            return List.of();
        }
    }

    private void insertQaPair(String kbId, KbChunk chunk, String question, String answer) {
        var e = new KbQaPair();
        e.setKbId(kbId);
        e.setFileId(chunk.getFileId());
        e.setFileName(chunk.getFileName());
        e.setQuestion(question);
        e.setAnswer(answer);
        e.setSource("ai");
        e.setStatus("draft");
        e.setFaqType("common");
        e.setCreatedAt(LocalDateTime.now());
        qaPairMapper.insert(e);
    }

    private boolean exists(String kbId, String fileId, String question) {
        Long n = qaPairMapper.selectCount(new LambdaQueryWrapper<KbQaPair>()
                .eq(KbQaPair::getKbId, kbId)
                .eq(fileId != null && !fileId.isBlank(), KbQaPair::getFileId, fileId)
                .eq(KbQaPair::getQuestion, question));
        return n != null && n > 0;
    }

    /**
     * 模型解析：任务指定的 code 优先；若该模型没有可用上游地址（例如仅占位的本地模型），
     * 回退到任一配置了上游地址的在线 LLM 模型，避免直接打到未启动的本地 AI 网关导致必然失败。
     */
    private ModelRecord resolveModel(String llmModel) {
        ModelRecord m = null;
        if (llmModel != null && !llmModel.isBlank()) {
            m = modelRecordMapper.selectOne(new LambdaQueryWrapper<ModelRecord>()
                    .eq(ModelRecord::getCode, llmModel).eq(ModelRecord::getStatus, "online")
                    .orderByAsc(ModelRecord::getId).last("LIMIT 1"));
        }
        if (!hasEndpoint(m)) {
            ModelRecord usable = firstUsableModel();
            if (usable != null) {
                if (m != null) log.info("模型 {} 未配置上游地址，回退使用 {}", llmModel, usable.getCode());
                m = usable;
            }
        }
        return m;
    }

    private boolean hasEndpoint(ModelRecord m) {
        return m != null && m.getApiUrl() != null && !m.getApiUrl().isBlank();
    }

    /** 任一配置了上游地址的在线 LLM；优先取配置了真实密钥的（MySQL DESC 排序下 NULL 排在最后） */
    private ModelRecord firstUsableModel() {
        return modelRecordMapper.selectOne(new LambdaQueryWrapper<ModelRecord>()
                .eq(ModelRecord::getPurpose, "LLM").eq(ModelRecord::getStatus, "online")
                .isNotNull(ModelRecord::getApiUrl).ne(ModelRecord::getApiUrl, "")
                .orderByDesc(ModelRecord::getApiKey).last("LIMIT 1"));
    }

    /** 调用密钥：优先界面保存的真实 api_key，其次 api_key_ref 引用 */
    private String resolveKey(ModelRecord m) {
        return (m.getApiKey() != null && !m.getApiKey().isBlank()) ? m.getApiKey() : m.getApiKeyRef();
    }

    private List<String> parseFileIds(KbQaExtractTask task) {
        String raw = task.getSourceIds();
        if (raw == null || raw.isBlank()) return List.of();
        String s = raw.trim();
        try {
            if (s.startsWith("[")) {
                List<String> list = JSONUtil.toList(s, String.class);
                return list == null ? List.of() : list;
            }
        } catch (Exception e) {
            log.warn("解析 sourceIds 失败: {}", e.getMessage());
        }
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    private Map<String, Object> parseParams(KbQaExtractTask task) {
        String raw = task.getParams();
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            if (raw.trim().startsWith("{")) return JSONUtil.parseObj(raw);
        } catch (Exception e) {
            log.warn("解析抽取参数失败: {}", e.getMessage());
        }
        return Map.of();
    }

    private int intParam(Map<String, Object> params, String key, int def) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) { try { return Integer.parseInt(v.toString().trim()); } catch (NumberFormatException ignore) { } }
        return def;
    }

    private boolean isStopped(String taskId) {
        var t = taskMapper.selectById(taskId);
        return t != null && "stopped".equals(t.getStatus());
    }

    private void updateProgress(String taskId, int total, int completed) {
        taskMapper.update(null, new LambdaUpdateWrapper<KbQaExtractTask>()
                .eq(KbQaExtractTask::getId, taskId)
                .set(KbQaExtractTask::getTotalCount, total)
                .set(KbQaExtractTask::getCompletedCount, completed));
    }

    private void finish(KbQaExtractTask task, String status, Map<String, Object> result) {
        var upd = new LambdaUpdateWrapper<KbQaExtractTask>()
                .eq(KbQaExtractTask::getId, task.getId())
                .set(KbQaExtractTask::getStatus, status)
                .set(KbQaExtractTask::getCompletedAt, LocalDateTime.now());
        try { upd.set(KbQaExtractTask::getResult, JSONUtil.toJsonStr(result)); }
        catch (Exception ignore) { }
        taskMapper.update(null, upd);
        log.info("问答抽取任务 {} 结束: {} {}", task.getId(), status, result);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String trimTo(String s, int max) { return s.length() <= max ? s : s.substring(0, max); }

    /** LLM 返回的一条问答 */
    @Data
    public static class QaCandidate {
        private String question;
        private String answer;
    }

    /** 单分块抽取结果：候选问答 + 模型错误（可选） */
    @Data
    private static class ExtractOutcome {
        private List<QaCandidate> candidates = new ArrayList<>();
        private String llmError;
    }
}
