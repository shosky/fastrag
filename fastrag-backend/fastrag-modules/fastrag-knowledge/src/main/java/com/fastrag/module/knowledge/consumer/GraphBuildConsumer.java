package com.fastrag.module.knowledge.consumer;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.module.publish.service.LogService;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.common.handler.GraphBuildHandler;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.module.graph.util.ExtractionNormalizer;
import com.fastrag.module.graph.util.GraphIdHashing;
import com.fastrag.module.graph.util.NameNormalizer;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.graph.entity.KbGraphIndex;
import com.fastrag.module.graph.mapper.KbGraphIndexMapper;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;

/**
 * 知识图谱构建服务 (RabbitMQ Consumer) — 升级版（参考 Yuxi 抽取管道）
 *
 * <p>从 chunks 中提取实体和关系，通过 GraphStore 写入存储。
 * 支持单文件(fileId)和全库(kbId)构建模式。
 * <ul>
 *   <li>使用确定性 ID 哈希（SHA-256）替代自增 ID</li>
 *   <li>使用 ExtractionNormalizer 进行实体去重和关系端点解析</li>
 *   <li>使用 Mention 追踪表记录实体/三元组与 Chunk 的关联</li>
 *   <li>升级 LLM 提取 Prompt，支持属性抽取和 Schema</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphBuildConsumer implements GraphBuildHandler {

    private final KbChunkMapper chunkMapper;
    private final GraphStore graphStore;
    private final LlmService llmService;
    private final KbGraphIndexMapper graphIndexMapper;
    private final KbFileMapper fileMapper;
    private final KbParseStrategyMapper parseStrategyMapper;
    private final ModelRecordMapper modelRecordMapper;
    private final LogService logService;

    /** 图谱构建最大并发数（可通过配置文件调整） */
    @Value("${graph.build.concurrency:15}")
    private int maxGraphBuildConcurrency;

    /** 跳过内容过短的 chunk（不调 LLM），单位字符数 */
    private static final int MIN_CHUNK_LENGTH_FOR_EXTRACTION = 50;

    @Override
    @RabbitListener(queues = "fastrag.graph-build.queue", concurrency = "2")
    public void handleGraphBuild(Map<String, Object> message) {
        String kbId = (String) message.get("kbId");
        // 兼容新旧消息格式：支持单 fileId 和多 fileIds
        String fileId = (String) message.get("fileId");
        List<String> fileIds = null;
        if (message.containsKey("fileIds")) {
            Object raw = message.get("fileIds");
            if (raw instanceof List) {
                fileIds = ((List<?>) raw).stream().map(Object::toString).collect(Collectors.toList());
            }
        }
        // 若只有单 fileId，转为列表统一处理
        if (fileId != null && !fileId.isEmpty() && fileIds == null) {
            fileIds = List.of(fileId);
        }

        String mode = message.containsKey("mode") ? String.valueOf(message.get("mode")) : "full";

        log.info("========== [GraphBuild] Start ==========");
        log.info("kbId={}, fileId={}, fileIds={}, mode={}", kbId, fileId, fileIds, mode);

        // 记录图谱构建开始日志
        try {
            logService.addLog(kbId, LogCategory.operation, ActionType.graph_build_started,
                    "", "开始构建图谱，模式: " + mode + ", 文件数: " + (fileIds != null ? fileIds.size() : 1),
                    "system", "success", null);
        } catch (Exception e) {
            log.warn("[Log] Failed to record graph build start log for kb={}", kbId);
        }

        // 获取 LLM 配置（用于实体/关系提取）
        LlmConfig llmConfig = resolveLlmConfig(fileIds);
        log.info("[GraphBuild] Resolved LLM config: model={}, apiUrl={}, apiKeySet={}",
                llmConfig.getModel(),
                llmConfig.getApiUrl() != null ? "***provided***" : "null",
                llmConfig.getApiKey() != null ? "***provided***" : "null");

        try {
            // 查询需要处理的 chunks（仅处理未提取的）
            List<KbChunk> chunks = queryChunks(kbId, fileIds, mode);
            int total = chunks.size();

            if (total == 0) {
                log.info("No chunks found for graph build, kb: {}, fileIds: {}", kbId, fileIds);
                updateGraphStatus(kbId, "completed", 100, 0, 0, total, total, 0);
                return;
            }

            updateGraphStatus(kbId, "building", 0, 0, 0, 0, total, 0);

            // 并行处理 chunks（并发数可配，默认 max=15，按 total/3 自动调整）
            int concurrency = Math.min(maxGraphBuildConcurrency, Math.max(1, total / 3));
            log.info("[GraphBuild] Processing {} chunks with concurrency={} (max={})",
                    total, concurrency, maxGraphBuildConcurrency);
            AtomicInteger processed = new AtomicInteger(0);
            AtomicInteger entityCount = new AtomicInteger(0);
            AtomicInteger relationCount = new AtomicInteger(0);
            AtomicInteger skippedChunks = new AtomicInteger(0);
            AtomicInteger failedChunks = new AtomicInteger(0);

            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    concurrency, concurrency, 60, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(total));
            List<CompletableFuture<Void>> futures = new ArrayList<>(total);

            for (KbChunk chunk : chunks) {
                String chunkId = chunk.getId();
                String content = chunk.getContent();

                // 跳过过短文本（< 50 字符），直接标记完成
                if (content == null || content.trim().length() < MIN_CHUNK_LENGTH_FOR_EXTRACTION) {
                    KbChunk toUpdate = new KbChunk();
                    toUpdate.setId(chunkId);
                    toUpdate.setGraphIndexed(1);
                    chunkMapper.updateById(toUpdate);
                    skippedChunks.incrementAndGet();
                    int done = processed.incrementAndGet();
                    if (done % 10 == 0 || done == total) {
                        updateGraphProgress(kbId, done, total,
                                entityCount.get(), relationCount.get(), failedChunks.get());
                    }
                    continue;
                }

                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        // 提取实体和关系
                        ExtractionNormalizer.ExtractionResult result = extractWithNormalization(
                                content, llmConfig.getModel(), llmConfig.getApiUrl(), llmConfig.getApiKey());

                        // 写入实体（使用确定性 ID）
                        List<ExtractionNormalizer.Entity> entities = result.getEntities();
                        if (entities != null) {
                            for (ExtractionNormalizer.Entity entity : entities) {
                                String normalizedName = NameNormalizer.normalize(entity.getText());
                                String entityId = GraphIdHashing.entityId(kbId, normalizedName, entity.getLabel());
                                graphStore.createEntity(kbId, entityId, entity.getText(), normalizedName, entity.getLabel());
                                graphStore.createEntityMention(kbId, entityId, chunkId);
                                entityCount.incrementAndGet();
                            }
                        }

                        // 写入关系（使用确定性 ID）
                        List<ExtractionNormalizer.Relation> relations = result.getRelations();
                        if (relations != null) {
                            for (ExtractionNormalizer.Relation rel : relations) {
                                String sourceName = rel.getSource() != null ? rel.getSource().toString() : "";
                                String targetName = rel.getTarget() != null ? rel.getTarget().toString() : "";
                                if (sourceName.isEmpty() || targetName.isEmpty()) continue;

                                String tripleId = GraphIdHashing.tripleId(
                                        kbId, sourceName, "Entity", rel.getLabel(), targetName, "Entity");
                                String relContent = sourceName + " -> " + rel.getLabel() + " -> " + targetName;
                                graphStore.createRelation(kbId, tripleId, sourceName, targetName, rel.getLabel(), relContent);
                                graphStore.createTripleMention(kbId, tripleId, chunkId);
                                relationCount.incrementAndGet();
                            }
                        }

                        // 在 Neo4j 中创建 Chunk 节点
                        graphStore.createChunk(kbId, chunkId, content);

                        // 标记 chunk 已完成图谱提取
                        KbChunk toUpdate = new KbChunk();
                        toUpdate.setId(chunkId);
                        toUpdate.setGraphIndexed(1);
                        toUpdate.setExtractionResult(JSONUtil.toJsonStr(result));
                        chunkMapper.updateById(toUpdate);

                    } catch (Exception e) {
                        failedChunks.incrementAndGet();
                        log.warn("Failed to process chunk {}: {}", chunkId, e.getMessage());
                    } finally {
                        int done = processed.incrementAndGet();
                        if (done % 10 == 0 || done == total) {
                            updateGraphProgress(kbId, done, total,
                                    entityCount.get(), relationCount.get(), failedChunks.get());
                        }
                    }
                }, executor));
            }

            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executor.shutdown();

            int finalEntityCount = entityCount.get();
            int finalRelationCount = relationCount.get();
            int finalFailed = failedChunks.get();
            int finalSkipped = skippedChunks.get();
            updateGraphStatus(kbId, "completed", 100, finalEntityCount, finalRelationCount,
                    total, total - finalFailed - finalSkipped, finalFailed);
            log.info("Graph build completed for kb: {}, entities: {}, relations: {}, " +
                            "failed: {}/{}, skipped: {}",
                    kbId, finalEntityCount, finalRelationCount, finalFailed, total, finalSkipped);

            // 记录图谱构建完成日志
            try {
                logService.addLog(kbId, LogCategory.operation, ActionType.graph_build_completed,
                        "", "实体: " + finalEntityCount + ", 关系: " + finalRelationCount +
                                ", 失败: " + finalFailed + "/" + total + ", 跳过: " + finalSkipped,
                        "system", "success", null);
            } catch (Exception e) {
                log.warn("[Log] Failed to record graph build complete log for kb={}", kbId);
            }

        } catch (Exception e) {
            log.error("Graph build failed for kb: {}, fileIds: {}", kbId, fileIds, e);
            try {
                logService.addLog(kbId, LogCategory.operation, ActionType.graph_build_failed,
                        "", "模式: " + mode + ", 错误: " + e.getMessage(),
                        "system", "failed", null);
            } catch (Exception le) {
                log.warn("[Log] Failed to record graph build failure log for kb={}", kbId);
            }
            updateGraphStatus(kbId, "failed", 0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * 解析 LLM 配置：优先从 file 对应的 parse strategy 获取，fallback 到默认网关
     */
    private LlmConfig resolveLlmConfig(List<String> fileIds) {
        log.info("[GraphBuild-LlmConfig] Resolving LLM config for fileIds={}", fileIds);

        if (fileIds == null || fileIds.isEmpty()) {
            log.warn("[GraphBuild-LlmConfig] fileIds is null/empty, cannot resolve LLM config -> extracting from LLM will be skipped");
            return new LlmConfig(null, null, null);
        }

        // 取第一个文件对应的策略
        KbFile file = fileMapper.selectById(fileIds.get(0));
        if (file == null) {
            log.warn("[GraphBuild-LlmConfig] File not found for id={} -> returning empty config", fileIds.get(0));
            return new LlmConfig(null, null, null);
        }
        log.info("[GraphBuild-LlmConfig] File found: name={}, parseStrategyId={}", file.getName(), file.getParseStrategyId());

        if (file.getParseStrategyId() != null) {
            KbParseStrategy strategy = parseStrategyMapper.selectById(file.getParseStrategyId());
            if (strategy == null) {
                log.warn("[GraphBuild-LlmConfig] Parse strategy not found for id={}", file.getParseStrategyId());
                return new LlmConfig(null, null, null);
            }
            log.info("[GraphBuild-LlmConfig] Strategy found: name={}, llmModel={}", strategy.getName(), strategy.getLlmModel());

            if (strategy.getLlmModel() != null && !strategy.getLlmModel().isEmpty()) {
                String llmModel = strategy.getLlmModel();
                log.info("[GraphBuild-LlmConfig] Looking up ModelRecord for code={}, status=online", llmModel);
                ModelRecord modelRecord = modelRecordMapper.selectOne(
                        new LambdaQueryWrapper<ModelRecord>()
                                .eq(ModelRecord::getCode, llmModel)
                                .eq(ModelRecord::getStatus, "online")
                                .last("LIMIT 1"));
                if (modelRecord != null) {
                    log.info("[GraphBuild-LlmConfig] ModelRecord found: model={}, apiUrl={}, apiKeyRef length={}",
                            llmModel, modelRecord.getApiUrl(),
                            modelRecord.getApiKeyRef() != null ? modelRecord.getApiKeyRef().length() : 0);
                    return new LlmConfig(llmModel, modelRecord.getApiUrl(), modelRecord.getApiKeyRef());
                } else {
                    log.warn("[GraphBuild-LlmConfig] ModelRecord NOT FOUND for code={} with status=online. " +
                            "Check model_config table: the model code '{}' must exist and be 'online'", llmModel, llmModel);
                }
            } else {
                log.warn("[GraphBuild-LlmConfig] Strategy's llmModel is null/empty, cannot resolve LLM config");
            }
        } else {
            log.warn("[GraphBuild-LlmConfig] File's parseStrategyId is null, no strategy associated with file");
        }

        log.warn("[GraphBuild-LlmConfig] Returning empty LLM config -> graph build will skip LLM extraction");
        return new LlmConfig(null, null, null);
    }

    /**
     * 查询需要构建图谱的 chunks（过滤已完成的，支持增量构建）
     */
    private List<KbChunk> queryChunks(String kbId, List<String> fileIds, String mode) {
        if (fileIds != null && !fileIds.isEmpty()) {
            List<KbChunk> allChunks = new ArrayList<>();
            for (String fid : fileIds) {
                allChunks.addAll(chunkMapper.selectList(
                        new LambdaQueryWrapper<KbChunk>()
                                .eq(KbChunk::getKbId, kbId)
                                .eq(KbChunk::getFileId, fid)
                                .and(w -> w.isNull(KbChunk::getGraphIndexed).or().eq(KbChunk::getGraphIndexed, 0))));
            }
            return allChunks;
        }

        // 全库模式：只查未提取的 chunks
        return chunkMapper.selectList(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getKbId, kbId)
                        .and(w -> w.isNull(KbChunk::getGraphIndexed).or().eq(KbChunk::getGraphIndexed, 0)));
    }

    /**
     * 从文本中提取实体和关系（调用 LLM + ExtractionNormalizer 规范化）
     */
    private ExtractionNormalizer.ExtractionResult extractWithNormalization(
            String text, String llmModel, String apiUrl, String apiKey) {
        // 未配置 LLM 时跳过抽取
        if (apiUrl == null || apiUrl.isBlank()) {
            log.warn("[GraphBuild-Extract] LLM NOT CONFIGURED (apiUrl is null/blank) -> skipping entity extraction for this chunk. " +
                    "Set LLM model in parsing strategy or ensure model is 'online' in model_config.");
            return new ExtractionNormalizer.ExtractionResult();
        }

        String model = llmModel != null ? llmModel : "default";
        String prompt = buildExtractionPrompt(text);
        log.info("[GraphBuild-Extract] Calling LLM model={} for entity extraction, text length={}", model, text.length());

        try {
            // 图谱抽取优先使用流式（非流式 Qwen3-8B 响应过慢），超时 60 秒
            String response;
            try {
                response = llmService.chat(model, prompt, apiUrl, apiKey, null);
            } catch (Exception e) {
                log.warn("[GraphBuild-Extract] Stream LLM call failed, retrying non-stream: {}", e.getMessage());
                response = llmService.chat(model, prompt, apiUrl, apiKey);
            }
            log.info("[GraphBuild-Extract] LLM responded, response length={}", response != null ? response.length() : 0);

            if (response == null || response.isBlank()) {
                log.warn("[GraphBuild-Extract] Empty response, skipping chunk");
                return new ExtractionNormalizer.ExtractionResult();
            }

            // 先清理 markdown 代码块标记（```json / ```），再检查 JSON
            // 先清理 markdown 代码块标记（```json / ```）和前后空白
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json?|```", "").trim();
            }
            // 清理 LLM 有时输出的多余文本（模型在 JSON 前后添加的对话内容）
            int braceStart = json.indexOf('{');
            if (braceStart > 0) {
                json = json.substring(braceStart);
            }

            // 检查清理后是否为 JSON
            if (!json.startsWith("{") && !json.startsWith("[")) {
                log.warn("[GraphBuild-Extract] Response is not JSON, skipping chunk: {}", response);
                return new ExtractionNormalizer.ExtractionResult();
            }
            ExtractionNormalizer.ExtractionResult rawResult;
            try {
                rawResult = JSONUtil.toBean(json, ExtractionNormalizer.ExtractionResult.class);
            } catch (Exception je) {
                // JSON 解析失败时尝试正则提取 JSON 数组内容（处理 LLM 输出格式异常）
                log.warn("[GraphBuild-Extract] JSON parse failed, trying regex fallback: {}", je.getMessage());
                rawResult = parseExtractionResultWithFallback(json);
            }
            log.info("[GraphBuild-Extract] Parsed result: {} entities, {} relations",
                    rawResult.getEntities() != null ? rawResult.getEntities().size() : 0,
                    rawResult.getRelations() != null ? rawResult.getRelations().size() : 0);

            // 规范化：去重、默认值、端点解析
            ExtractionNormalizer.ExtractionResult normalized = ExtractionNormalizer.normalize(rawResult);
            log.info("[GraphBuild-Extract] After normalization: {} entities, {} relations",
                    normalized.getEntities() != null ? normalized.getEntities().size() : 0,
                    normalized.getRelations() != null ? normalized.getRelations().size() : 0);
            return normalized;
        } catch (Exception e) {
            log.warn("[GraphBuild-Extract] Failed to parse LLM response for entity extraction: {}", e.getMessage(), e);
            return new ExtractionNormalizer.ExtractionResult();
        }
    }

    /**
     * JSON 解析兜底：当 JSONUtil.toBean 失败时，尝试正则提取 entities 和 relations 数组
     */
    private ExtractionNormalizer.ExtractionResult parseExtractionResultWithFallback(String json) {
        ExtractionNormalizer.ExtractionResult result = new ExtractionNormalizer.ExtractionResult();
        result.setEntities(new ArrayList<>());
        result.setRelations(new ArrayList<>());
        try {
            // 尝试提取 entities 数组（宽松匹配，容忍缺少冒号等格式问题）
            java.util.regex.Matcher entityMatcher = java.util.regex.Pattern.compile(
                    "\"entities\"\\s*[:=]?\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL).matcher(json);
            if (entityMatcher.find()) {
                String entityContent = entityMatcher.group(1).trim();
                if (!entityContent.isEmpty()) {
                    // 宽松匹配：提取每个 "text":"value" 对
                    java.util.regex.Matcher textMatcher = java.util.regex.Pattern.compile(
                            "\"text\"\\s*[:=]?\\s*\"([^\"]+)\"").matcher(entityContent);
                    java.util.regex.Matcher labelMatcher = java.util.regex.Pattern.compile(
                            "\"label\"\\s*[:=]?\\s*\"([^\"]+)\"").matcher(entityContent);
                    // 使用 text 和 label 交替匹配
                    List<String> texts = new ArrayList<>();
                    List<String> labels = new ArrayList<>();
                    while (textMatcher.find()) texts.add(textMatcher.group(1));
                    while (labelMatcher.find()) labels.add(labelMatcher.group(1));
                    int count = Math.min(texts.size(), labels.size());
                    for (int i = 0; i < count; i++) {
                        ExtractionNormalizer.Entity entity = new ExtractionNormalizer.Entity();
                        entity.setText(texts.get(i));
                        entity.setLabel(labels.get(i));
                        result.getEntities().add(entity);
                    }
                }
            }

            // 尝试提取 relations 数组（宽松匹配）
            java.util.regex.Matcher relMatcher = java.util.regex.Pattern.compile(
                    "\"relations\"\\s*[:=]?\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL).matcher(json);
            if (relMatcher.find()) {
                String relContent = relMatcher.group(1).trim();
                if (!relContent.isEmpty()) {
                    // 宽松匹配 source/target/label
                    java.util.regex.Matcher sourceMatcher = java.util.regex.Pattern.compile(
                            "\"source\"\\s*[:=]?\\s*\"([^\"]+)\"").matcher(relContent);
                    java.util.regex.Matcher targetMatcher = java.util.regex.Pattern.compile(
                            "\"target\"\\s*[:=]?\\s*\"([^\"]+)\"").matcher(relContent);
                    java.util.regex.Matcher relLabelMatcher = java.util.regex.Pattern.compile(
                            "\"label\"\\s*[:=]?\\s*\"([^\"]+)\"").matcher(relContent);
                    List<String> sources = new ArrayList<>();
                    List<String> targets = new ArrayList<>();
                    List<String> relLabels = new ArrayList<>();
                    while (sourceMatcher.find()) sources.add(sourceMatcher.group(1));
                    while (targetMatcher.find()) targets.add(targetMatcher.group(1));
                    while (relLabelMatcher.find()) relLabels.add(relLabelMatcher.group(1));
                    int count = Math.min(Math.min(sources.size(), targets.size()), relLabels.size());
                    for (int i = 0; i < count; i++) {
                        ExtractionNormalizer.Relation rel = new ExtractionNormalizer.Relation();
                        rel.setSource(sources.get(i));
                        rel.setTarget(targets.get(i));
                        rel.setLabel(relLabels.get(i));
                        result.getRelations().add(rel);
                    }
                }
            }

            log.info("[GraphBuild-Extract] Regex fallback parsed: {} entities, {} relations",
                    result.getEntities().size(), result.getRelations().size());
        } catch (Exception e) {
            log.warn("[GraphBuild-Extract] Regex fallback also failed: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 构建 LLM 提取 Prompt（强调关系提取，参考 Yuxi 经验）
     */
    private String buildExtractionPrompt(String text) {
        // 保留更多文本上下文以利于关系提取
        String truncated = text.substring(0, Math.min(text.length(), 3000));
        return """
                从文本中提取实体和实体间的关系，返回JSON。

                先找出所有实体，再找出实体之间的明确关系。

                {"entities":[{"text":"实体名称","label":"实体类型"}],"relations":[{"source":"实体名称","target":"实体名称","label":"关系类型"}]}

                要求：
                - 每个关系必须连接两个文本中出现的不同实体
                - 关系类型要具体（如：属于、位于、使用、创建、包含、配置、管理、依赖、部署）
                - 同一条关系不要重复，source/target交换视为不同
                - 只返回JSON，无其他文字
                - 无关系则返回 {"entities":[],"relations":[]}

                文本：
                """ + truncated;
    }

    /**
     * 更新图谱构建状态（UPSERT）
     */
    private void updateGraphStatus(String kbId, String status, int progress,
                                   int entityCount, int relationCount, int totalChunks,
                                   int builtChunks, int failedChunks) {
        try {
            KbGraphIndex index = graphIndexMapper.selectById(kbId);
            if (index == null) {
                index = new KbGraphIndex();
                index.setKbId(kbId);
                index.setStatus(status);
                index.setBuildProgress(progress);
                index.setEntityCount(entityCount);
                index.setRelationCount(relationCount);
                index.setTotalChunks(totalChunks);
                index.setBuiltChunks(builtChunks);
                index.setFailedChunks(failedChunks);
                graphIndexMapper.insert(index);
            } else {
                index.setStatus(status);
                index.setBuildProgress(progress);
                index.setEntityCount(entityCount);
                index.setRelationCount(relationCount);
                index.setTotalChunks(totalChunks);
                index.setBuiltChunks(builtChunks);
                index.setFailedChunks(failedChunks);
                graphIndexMapper.updateById(index);
            }
        } catch (Exception e) {
            log.warn("Failed to update graph status: {}", e.getMessage());
        }
    }

    private void updateGraphProgress(String kbId, int processed, int total,
                                     int entityCount, int relationCount, int failedChunks) {
        int progress = total > 0 ? (int) ((double) processed / total * 100) : 0;
        updateGraphStatus(kbId, "building", progress, entityCount, relationCount,
                total, processed - failedChunks, failedChunks);
        log.info("[GraphBuild-Progress] kb={}, progress={}% ({}/{}) entities={} relations={} failed={}",
                kbId, progress, processed, total, entityCount, relationCount, failedChunks);
    }

    /**
     * LLM 配置内部 DTO
     */
    @Data
    private static class LlmConfig {
        private final String model;
        private final String apiUrl;
        private final String apiKey;
    }
}
