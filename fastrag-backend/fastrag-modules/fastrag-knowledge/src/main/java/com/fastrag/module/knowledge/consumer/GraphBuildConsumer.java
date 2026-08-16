package com.fastrag.module.knowledge.consumer;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.module.publish.service.LogService;
import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.common.handler.GraphBuildHandler;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.module.graph.util.EntityTypeNormalizer;
import com.fastrag.module.graph.util.ExtractionNormalizer;
import com.fastrag.module.graph.util.GraphIdHashing;
import com.fastrag.module.graph.util.NameNormalizer;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.graph.entity.KbGraphIndex;
import com.fastrag.module.graph.mapper.KbGraphIndexMapper;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
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
 * 知识图谱构建消费者（RabbitMQ Consumer），从文档 chunks 中提取实体和关系并写入图数据库。
 *
 * <p>核心职责：
 * <ul>
 *   <li>监听 RabbitMQ 队列 {@code fastrag.graph-build.queue}，消费图谱构建消息</li>
 *   <li>支持单文件（fileId/fileIds）和全库（kbId）两种构建模式，以及 full（重建）和 incremental（增量）两种模式</li>
 *   <li>使用 LLM 对每个 chunk 进行实体和关系抽取，经 ExtractionNormalizer 规范化去重后写入 GraphStore</li>
 *   <li>使用确定性 ID 哈希（SHA-256）替代自增 ID，保证跨批次幂等</li>
 *   <li>使用 Mention 追踪表记录实体/三元组与 Chunk 的关联关系</li>
 *   <li>为实体批量生成 Embedding 向量，支持图谱中的语义检索</li>
 *   <li>构建完成后自动清理孤立实体/关系，回填缺失 embedding</li>
 * </ul>
 *
 * <p>消息格式（Map）：
 * <ul>
 *   <li>{@code kbId} — 知识库 ID（必填）</li>
 *   <li>{@code fileId} — 单个文件 ID（可选，兼容旧格式）</li>
 *   <li>{@code fileIds} — 多个文件 ID 列表（可选，新格式）</li>
 *   <li>{@code mode} — 构建模式：full（全量重建）/ incremental（增量，默认）</li>
 * </ul>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>RabbitMQ concurrency=5，通过线程池并行处理 chunks，并发数可配（默认 max=15，按 total/3 自动调整）</li>
 *   <li>跳过内容过短（< 50 字符）的 chunk，直接标记完成</li>
 *   <li>LLM 提取使用自定义 Prompt 限制实体类型白名单，收敛类型爆炸问题</li>
 *   <li>JSON 解析失败时使用正则兜底提取实体和关系</li>
 *   <li>构建进度实时更新到 kb_graph_index 表（status、progress、entityCount、relationCount）</li>
 *   <li>LLM 配置从文件的 parse strategy 中解析（llmModel → ModelRecord online）</li>
 * </ul>
 *
 * <p>与其他模块的交互：
 * <ul>
 *   <li>graph 模块（{@link GraphStore}）— 实体/关系/Chunk 节点的 CRUD 操作</li>
 *   <li>graph 模块（{@link ExtractionNormalizer}、{@link EntityTypeNormalizer}、{@link NameNormalizer}）— 实体规范化</li>
 *   <li>ai 模块（{@link LlmService}、{@link EmbeddingService}）— LLM 调用和 Embedding 生成</li>
 *   <li>publish 模块（{@link LogService}）— 构建日志记录</li>
 *   <li>platform 模块（{@link ModelRecordMapper}）— 查询 LLM/Embedding 模型配置</li>
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
    private final EmbeddingService embeddingService;
    private final KnowledgeBaseMapper kbMapper;

    /** 图谱构建最大并发数（可通过配置文件调整） */
    @Value("${graph.build.concurrency:15}")
    private int maxGraphBuildConcurrency;

    /** 图谱抽取 LLM 总超时（秒），默认 180s，适配 Qwen3 等慢响应模型 */
    @Value("${graph.build.llm-timeout:180}")
    private int graphBuildLlmTimeoutSeconds;

    /** 跳过内容过短的 chunk（不调 LLM），单位字符数 */
    private static final int MIN_CHUNK_LENGTH_FOR_EXTRACTION = 50;

    /** 实体类型白名单（逗号分隔，可覆盖默认集合；未命中归 UNKNOWN） */
    @Value("${graph.entity-type-whitelist:}")
    private String entityTypeWhitelistCfg;

    /** 白名单缓存（懒加载） */
    private volatile Set<String> entityTypeWhitelistCache;

    /**
     * 图谱构建消费者。
     * RabbitMQ concurrency=5：5 个消费者线程并行消费队列消息，每个文件处理完后取下一条。
     * 若需调整并发数，在 application.yml 中配置：
     *   spring.rabbitmq.listener.simple.concurrency=5
     *   spring.rabbitmq.listener.simple.max-concurrency=10
     */
    @Override
    @RabbitListener(queues = "fastrag.graph-build.queue", concurrency = "5")
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
        LlmConfig llmConfig = resolveLlmConfig(kbId, fileIds);
        log.info("[GraphBuild] Resolved LLM config: model={}, apiUrl={}, apiKeySet={}",
                llmConfig.getModel(),
                llmConfig.getApiUrl() != null ? "***provided***" : "null",
                llmConfig.getApiKey() != null ? "***provided***" : "null");

        // LLM 不可用时标记构建失败（而非静默跳过所有 chunk）
        if (llmConfig.getApiUrl() == null || llmConfig.getApiUrl().isBlank()) {
            log.error("[GraphBuild] LLM not configured (apiUrl is null/blank). Graph build cannot proceed. " +
                    "Ensure the file's parse strategy has a valid LLM model that is 'online' in model_config.");
            try {
                updateGraphStatus(kbId, "failed", 0, 0, 0, 0, 0, 0);
                // 记录具体错误原因
                var idx = graphIndexMapper.selectById(kbId);
                if (idx != null) {
                    idx.setBuildError("LLM not configured: no valid apiUrl found. " +
                            "Check parse strategy LLM model and ensure it is 'online'.");
                    graphIndexMapper.updateById(idx);
                }
                logService.addLog(kbId, LogCategory.operation, ActionType.graph_build_failed,
                        "", "LLM 未配置或模型不在线，无法进行实体抽取", "system", "failed", null);
            } catch (Exception le) {
                log.warn("[GraphBuild] Failed to record LLM-not-configured error for kb={}", kbId);
            }
            return;
        }

        try {
            // 查询需要处理的 chunks（仅处理未提取的）
            List<KbChunk> chunks = queryChunks(kbId, fileIds, mode);
            int total = chunks.size();

            // 实体类型白名单：KB 级 schema（kg_graph_index.settings.entitySchema）优先，
            // 其次 yml 全局配置，最后默认集合（KG-07 可配置化）
            final Set<String> entityTypeWhitelist = resolveEntityTypeWhitelist(kbId);
            log.info("[GraphBuild] Entity type whitelist for kb={}: {} types (schema configured={})",
                    kbId, entityTypeWhitelist.size(), entityTypeWhitelist != EntityTypeNormalizer.DEFAULT_WHITELIST
                            && !entityTypeWhitelist.equals(EntityTypeNormalizer.DEFAULT_WHITELIST));

            // 全库 chunk 统计（索引管理的 totalChunks 应为整个知识库的值，
            // 而非本次构建范围——单文件增量构建不应覆盖全库统计）
            long kbTotalChunks = chunkMapper.selectCount(
                    new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getKbId, kbId));
            long kbBuiltChunks = chunkMapper.selectCount(
                    new LambdaQueryWrapper<KbChunk>()
                            .eq(KbChunk::getKbId, kbId)
                            .eq(KbChunk::getGraphIndexed, 1));

            if (total == 0) {
                log.info("No chunks found for graph build, kb: {}, fileIds: {}", kbId, fileIds);
                updateGraphStatus(kbId, "completed", 100, 0, 0,
                        (int) kbTotalChunks, (int) kbBuiltChunks, 0);
                return;
            }

            updateGraphStatus(kbId, "building", 0, 0, 0, (int) kbTotalChunks, (int) kbBuiltChunks, 0);

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
                        updateGraphProgress(kbId, done, total, kbTotalChunks, kbBuiltChunks,
                                entityCount.get(), relationCount.get(), failedChunks.get());
                    }
                    continue;
                }

                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        // 提取实体和关系（KG-03/KG-06：实体带 description 与 attributes）
                        ExtractionNormalizer.ExtractionResult result = extractWithNormalization(
                                content, llmConfig.getModel(), llmConfig.getApiUrl(), llmConfig.getApiKey(), entityTypeWhitelist);

                        // 写入实体（使用确定性 ID；实体类型经白名单归一，收敛类型爆炸）
                        List<ExtractionNormalizer.Entity> entities = result.getEntities();
                        List<String> entityNamesForEmbed = new ArrayList<>();
                        // 本 chunk 提取实体的 ID 映射（normalized_name -> entity_id），
                        // 关系写入时用实体 ID 引用端点，消除"边按名称引用"的悬空/错连问题（KG-01）
                        Map<String, String> entityIdByNormalizedName = new HashMap<>();
                        if (entities != null) {
                            for (ExtractionNormalizer.Entity entity : entities) {
                                String normalizedName = NameNormalizer.normalize(entity.getText());
                                String type = EntityTypeNormalizer.normalize(entity.getLabel(), entityTypeWhitelist);
                                String entityId = GraphIdHashing.entityId(kbId, normalizedName, type);
                                String description = entity.getDescription();
                                String attributesJson = (entity.getAttributes() != null && !entity.getAttributes().isEmpty())
                                        ? JSONUtil.toJsonStr(entity.getAttributes()) : null;
                                graphStore.createEntity(kbId, entityId, entity.getText(), normalizedName, type,
                                        description, attributesJson);
                                graphStore.createEntityMention(kbId, entity.getText(), entityId, chunkId, chunk.getFileId());
                                entityIdByNormalizedName.put(normalizedName, entityId);
                                entityNamesForEmbed.add(entity.getText());
                                entityCount.incrementAndGet();
                            }
                        }

                        // 写入关系（使用确定性 ID + 端点实体 ID）
                        List<ExtractionNormalizer.Relation> relations = result.getRelations();
                        if (relations != null) {
                            for (ExtractionNormalizer.Relation rel : relations) {
                                String sourceName = rel.getSource() != null ? rel.getSource().toString() : "";
                                String targetName = rel.getTarget() != null ? rel.getTarget().toString() : "";
                                if (sourceName.isEmpty() || targetName.isEmpty()) continue;

                                // 自环防护（KG-05）：规范化后同名即视为自环，跳过
                                String sourceNorm = NameNormalizer.normalize(sourceName);
                                String targetNorm = NameNormalizer.normalize(targetName);
                                if (sourceNorm.equals(targetNorm)) {
                                    log.debug("[GraphBuild] Skipping self-loop relation: {} -> {}, label={}",
                                            sourceName, targetName, rel.getLabel());
                                    continue;
                                }

                                String tripleId = GraphIdHashing.tripleId(
                                        kbId, sourceName, "Entity", rel.getLabel(), targetName, "Entity");
                                String relContent = sourceName + " -> " + rel.getLabel() + " -> " + targetName;
                                // 端点实体 ID：先查本 chunk 提取结果，再查库（跨 chunk 已存在实体），
                                // 最后创建 UNKNOWN 占位实体——保证边始终引用有效实体 ID（KG-01）
                                String sourceId = resolveRelationEndpointId(kbId, sourceName, sourceNorm,
                                        entityIdByNormalizedName);
                                String targetId = resolveRelationEndpointId(kbId, targetName, targetNorm,
                                        entityIdByNormalizedName);
                                graphStore.createRelation(kbId, tripleId, sourceId, sourceName,
                                        targetId, targetName, rel.getLabel(), relContent);
                                graphStore.createTripleMention(kbId, tripleId, chunkId, chunk.getFileId());
                                relationCount.incrementAndGet();
                            }
                        }

                        // 在 Neo4j 中创建 Chunk 节点（携带 fileId 用于按文件删除）
                        graphStore.createChunk(kbId, chunkId, chunk.getFileId(), content);

                        // 标记 chunk 已完成图谱提取
                        KbChunk toUpdate = new KbChunk();
                        toUpdate.setId(chunkId);
                        toUpdate.setGraphIndexed(1);
                        toUpdate.setExtractionResult(JSONUtil.toJsonStr(result));
                        chunkMapper.updateById(toUpdate);

                        // 为实体生成 embedding（向量检索用；失败仅告警，不影响构建）
                        updateEntityEmbeddings(kbId, entityNamesForEmbed);

                    } catch (Exception e) {
                        failedChunks.incrementAndGet();
                        log.warn("Failed to process chunk {}: {}", chunkId, e.getMessage());
                    } finally {
                        int done = processed.incrementAndGet();
                        if (done % 10 == 0 || done == total) {
                            updateGraphProgress(kbId, done, total, kbTotalChunks, kbBuiltChunks,
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

            // 使用实时查询获取全库统计（而非本次构建的局部计数，避免多文件分批构建后数字被覆盖）
            long liveEntityCount = 0;
            long liveRelationCount = 0;
            try {
                liveEntityCount = graphStore.countEntities(kbId);
                liveRelationCount = graphStore.countRelations(kbId);
            } catch (Exception e) {
                log.warn("[GraphBuild] Failed to get live counts, falling back to local counters: {}", e.getMessage());
                liveEntityCount = entityCount.get();
                liveRelationCount = relationCount.get();
            }
            // 完成后重新统计全库已构建 chunk 数（包含此前其他文件已构建的部分）
            long finalBuiltChunks = chunkMapper.selectCount(
                    new LambdaQueryWrapper<KbChunk>()
                            .eq(KbChunk::getKbId, kbId)
                            .eq(KbChunk::getGraphIndexed, 1));
            updateGraphStatus(kbId, "completed", 100, (int) liveEntityCount, (int) liveRelationCount,
                    total, total - finalFailed - finalSkipped, finalFailed);
            log.info("Graph build completed for kb: {}, entities: {}, relations: {}, " +
                            "failed: {}/{}, skipped: {}",
                    kbId, liveEntityCount, liveRelationCount, finalFailed, total, finalSkipped);

            // 回填存量实体 embedding（新增实体已在构建时生成，这里只补历史缺失）
            backfillEntityEmbeddings(kbId);

            // 清理孤立实体/关系（删除文件或编辑 chunk 后残留的无引用数据）
            graphStore.cleanupOrphanNodes(kbId);

            // 观测实体类型分布（UNKNOWN 占比反映类型归一效果）
            try {
                Map<String, Long> typeCounts = graphStore.countEntitiesByType(kbId);
                long unknown = typeCounts.getOrDefault("UNKNOWN", 0L);
                long entityTotal = typeCounts.values().stream().mapToLong(Long::longValue).sum();
                String pct = String.format("%.1f%%", entityTotal > 0 ? 100.0 * unknown / entityTotal : 0.0);
                log.info("[GraphBuild] Entity types for kb={}: {} types, UNKNOWN={} ({} of {})",
                        kbId, typeCounts.size(), unknown, pct, entityTotal);
            } catch (Exception e) {
                log.warn("[GraphBuild] Failed to collect entity type distribution for kb={}", kbId);
            }

            // 记录图谱构建完成日志
            try {
                logService.addLog(kbId, LogCategory.operation, ActionType.graph_build_completed,
                        "", "实体: " + liveEntityCount + ", 关系: " + liveRelationCount +
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
     * 解析 LLM 配置：优先从 file 对应的 parse strategy 获取，fallback 到默认网关。
     * full 重建（无 fileIds）时回退到该知识库的任意文件解析，避免"清空后全量重建"必然失败。
     */
    private LlmConfig resolveLlmConfig(String kbId, List<String> fileIds) {
        log.info("[GraphBuild-LlmConfig] Resolving LLM config for kb={}, fileIds={}", kbId, fileIds);

        if (fileIds == null || fileIds.isEmpty()) {
            log.warn("[GraphBuild-LlmConfig] fileIds is null/empty, falling back to first file of kb={}", kbId);
            KbFile anyFile = fileMapper.selectOne(
                    new LambdaQueryWrapper<KbFile>()
                            .eq(KbFile::getKbId, kbId)
                            .isNull(KbFile::getDeletedAt)
                            .last("LIMIT 1"));
            if (anyFile == null) {
                log.warn("[GraphBuild-LlmConfig] No file found for kb={} -> returning empty config", kbId);
                return new LlmConfig(null, null, null);
            }
            fileIds = List.of(anyFile.getId());
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

        // Fallback: 查 KB 级 graphLlmModel（parse strategy 未配置 LLM 时的兜底）
        log.info("[GraphBuild-LlmConfig] Strategy LLM not available, trying KB-level graphLlmModel for kb={}", kbId);
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb != null && kb.getGraphLlmModel() != null && !kb.getGraphLlmModel().isBlank()) {
            String llmModel = kb.getGraphLlmModel();
            log.info("[GraphBuild-LlmConfig] KB graphLlmModel={}, looking up ModelRecord", llmModel);
            ModelRecord modelRecord = modelRecordMapper.selectOne(
                    new LambdaQueryWrapper<ModelRecord>()
                            .eq(ModelRecord::getCode, llmModel)
                            .eq(ModelRecord::getStatus, "online")
                            .last("LIMIT 1"));
            if (modelRecord != null) {
                log.info("[GraphBuild-LlmConfig] KB-level ModelRecord found: model={}, apiUrl={}",
                        llmModel, modelRecord.getApiUrl());
                return new LlmConfig(llmModel, modelRecord.getApiUrl(), modelRecord.getApiKeyRef());
            } else {
                log.warn("[GraphBuild-LlmConfig] KB-level ModelRecord NOT FOUND for code={}", llmModel);
            }
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
                LambdaQueryWrapper<KbChunk> wrapper = new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getKbId, kbId)
                        .eq(KbChunk::getFileId, fid);
                // full 模式不过滤 graphIndexed，处理所有 chunk
                if (!"full".equals(mode)) {
                    wrapper.and(w -> w.isNull(KbChunk::getGraphIndexed).or().eq(KbChunk::getGraphIndexed, 0));
                }
                allChunks.addAll(chunkMapper.selectList(wrapper));
            }
            return allChunks;
        }

        // 全库模式
        if ("full".equals(mode)) {
            // full 模式：处理所有 chunk（用于重建场景）
            return chunkMapper.selectList(
                    new LambdaQueryWrapper<KbChunk>()
                            .eq(KbChunk::getKbId, kbId));
        }

        // 默认增量模式：只查未提取的 chunks
        return chunkMapper.selectList(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getKbId, kbId)
                        .and(w -> w.isNull(KbChunk::getGraphIndexed).or().eq(KbChunk::getGraphIndexed, 0)));
    }

    /**
     * 从文本中提取实体和关系（调用 LLM + ExtractionNormalizer 规范化）
     *
     * @param whitelist 实体类型白名单（KB 级 schema 或全局配置解析结果，参与 prompt 与类型归一）
     */
    private ExtractionNormalizer.ExtractionResult extractWithNormalization(
            String text, String llmModel, String apiUrl, String apiKey, Set<String> whitelist) {
        // 未配置 LLM 时跳过抽取
        if (apiUrl == null || apiUrl.isBlank()) {
            log.warn("[GraphBuild-Extract] LLM NOT CONFIGURED (apiUrl is null/blank) -> skipping entity extraction for this chunk. " +
                    "Set LLM model in parsing strategy or ensure model is 'online' in model_config.");
            return new ExtractionNormalizer.ExtractionResult();
        }

        String model = llmModel != null ? llmModel : "default";
        String prompt = buildExtractionPrompt(text, whitelist);
        log.info("[GraphBuild-Extract] Calling LLM model={} for entity extraction, text length={}", model, text.length());

        try {
            // 图谱抽取使用流式 + 关闭 thinking + 自定义超时（默认 180s）
            // 关闭 thinking 避免 Qwen3 等模型输出大量 <think...> 推理文本导致超时
            String response;
            try {
                response = llmService.chatWithTimeout(model, prompt, apiUrl, apiKey,
                        false, graphBuildLlmTimeoutSeconds);
            } catch (Exception e) {
                log.warn("[GraphBuild-Extract] LLM call failed (timeout={}s), skipping chunk: {}",
                        graphBuildLlmTimeoutSeconds, e.getMessage());
                return new ExtractionNormalizer.ExtractionResult();
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
     * 构建 LLM 提取 Prompt（强调关系提取，参考 Yuxi 经验；实体附带 description 与 attributes）
     */
    private String buildExtractionPrompt(String text, Set<String> whitelist) {
        // 保留更多文本上下文以利于关系提取
        String truncated = text.substring(0, Math.min(text.length(), 3000));
        // 类型白名单提示（限制 LLM 输出类型集合，收敛类型爆炸；取前 60 个控制 token）
        String typeHint = whitelist.stream().limit(60)
                .collect(Collectors.joining("、"));
        return """
                从文本中提取实体和实体间的关系，返回JSON。

                先找出所有实体，再找出实体之间的明确关系。

                {"entities":[{"text":"实体名称","label":"实体类型","description":"一句话描述","attributes":[{"text":"属性值","label":"属性名"}]}],"relations":[{"source":"实体名称","target":"实体名称","label":"关系类型"}]}

                要求：
                - 每个关系必须连接两个文本中出现的不同实体
                - 关系类型要具体（如：属于、位于、使用、创建、包含、配置、管理、依赖、部署）
                - 实体类型（label）必须从以下集合中选择（选择最贴切的一个，不要自造类型）：
                """ + typeHint + """

                - 每个实体给出不超过一句话的 description（没有则为空字符串）
                - 每个实体可附带 0-5 个属性（attributes），属性是文本中与该实体直接相关的键值信息（如价格、型号、规格、要求、数量），属性值必须是文本中出现的内容
                - 同一条关系不要重复，source/target交换视为不同
                - 只返回JSON，无其他文字
                - 无关系则返回 {"entities":[],"relations":[]}

                文本：
                """ + truncated;
    }

    /**
     * 解析关系端点实体 ID（KG-01）：
     * 1. 本 chunk 提取实体映射；2. 图库按规范化名反查（跨 chunk 已存在）；3. 创建 UNKNOWN 占位实体。
     */
    private String resolveRelationEndpointId(String kbId, String rawName, String normalizedName,
                                             Map<String, String> entityIdByNormalizedName) {
        String id = entityIdByNormalizedName.get(normalizedName);
        if (id != null) return id;
        id = graphStore.findEntityId(kbId, normalizedName);
        if (id != null) return id;
        // 端点实体在任何 chunk 都未被提取：创建 UNKNOWN 占位实体，保证边引用有效
        String placeholderId = GraphIdHashing.entityId(kbId, normalizedName, EntityTypeNormalizer.UNKNOWN);
        graphStore.createEntity(kbId, placeholderId, rawName, normalizedName, EntityTypeNormalizer.UNKNOWN, null, null);
        log.debug("[GraphBuild] Created UNKNOWN placeholder entity for relation endpoint: {}", rawName);
        return placeholderId;
    }

    /**
     * 解析实体类型白名单（优先级：KB 级 schema 配置 → yml 全局配置 → 默认集合）。
     *
     * <p>KB 级 schema 存放在 kb_graph_index.settings 的 entitySchema 字段（逗号/换行分隔），
     * 由前端图谱设置面板写入（KG-07）。未配置时回退全局逻辑。</p>
     */
    private Set<String> resolveEntityTypeWhitelist(String kbId) {
        try {
            KbGraphIndex idx = graphIndexMapper.selectById(kbId);
            if (idx != null && idx.getSettings() != null && !idx.getSettings().isBlank()) {
                var settings = JSONUtil.parseObj(idx.getSettings());
                String schema = settings.getStr("entitySchema");
                if (schema != null && !schema.isBlank()) {
                    Set<String> types = Arrays.stream(schema.split("[,\\n]"))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toSet());
                    if (!types.isEmpty()) {
                        log.info("[GraphBuild] Using KB-level entity schema for kb={}: {} types", kbId, types.size());
                        return Collections.unmodifiableSet(types);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[GraphBuild] Failed to parse KB entity schema for kb={}: {}", kbId, e.getMessage());
        }
        return resolveEntityTypeWhitelist();
    }

    /**
     * 解析实体类型白名单（yml 配置优先，空则用默认集合；懒加载缓存）
     */
    private Set<String> resolveEntityTypeWhitelist() {
        Set<String> cached = entityTypeWhitelistCache;
        if (cached != null) return cached;
        if (entityTypeWhitelistCfg == null || entityTypeWhitelistCfg.isBlank()) {
            cached = EntityTypeNormalizer.DEFAULT_WHITELIST;
        } else {
            cached = Arrays.stream(entityTypeWhitelistCfg.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
        entityTypeWhitelistCache = cached;
        return cached;
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

    /**
     * 更新构建进度。
     *
     * @param kbId            知识库 ID
     * @param processed       本次构建已处理 chunk 数（用于进度百分比）
     * @param total           本次构建范围 chunk 总数（用于进度百分比）
     * @param kbTotalChunks   全库 chunk 总数（索引管理展示用）
     * @param kbBuiltChunks   构建开始前全库已构建 chunk 数（基准）
     * @param entityCount     本次新增实体数
     * @param relationCount   本次新增关系数
     * @param failedChunks    本次失败 chunk 数
     */
    private void updateGraphProgress(String kbId, int processed, int total,
                                     long kbTotalChunks, long kbBuiltChunks,
                                     int entityCount, int relationCount, int failedChunks) {
        int progress = total > 0 ? (int) ((double) processed / total * 100) : 0;
        // builtChunks = 全库已构建基准 + 本次已处理（progress 使用全库口径，避免覆盖）
        long built = kbBuiltChunks + processed;
        updateGraphStatus(kbId, "building", progress, entityCount, relationCount,
                (int) kbTotalChunks, (int) built, failedChunks);
        log.info("[GraphBuild-Progress] kb={}, progress={}% ({}/{}) entities={} relations={} failed={}",
                kbId, progress, processed, total, entityCount, relationCount, failedChunks);
    }

    /**
     * 为实体批量生成 embedding 并写入图谱（向量检索用）
     * 失败仅告警，不影响构建；未配置 embedding 模型时跳过（向量检索降级文本匹配）
     */
    private void updateEntityEmbeddings(String kbId, List<String> entityNames) {
        if (entityNames == null || entityNames.isEmpty()) return;
        LlmConfig embCfg = resolveEmbeddingModelConfig(kbId);
        if (embCfg == null) return;
        try {
            List<List<Float>> vectors = embeddingService.embed(
                    embCfg.getModel(), entityNames, embCfg.getApiUrl(), embCfg.getApiKey());
            for (int i = 0; i < entityNames.size() && i < vectors.size(); i++) {
                graphStore.updateEntityEmbedding(kbId, entityNames.get(i), vectors.get(i));
            }
            log.info("[GraphBuild-Embed] Embedded {} entities for kb={}", entityNames.size(), kbId);
        } catch (Exception e) {
            log.warn("[GraphBuild-Embed] Failed to embed {} entities for kb={}: {}",
                    entityNames.size(), kbId, e.getMessage());
        }
    }

    /**
     * 回填缺失 embedding 的存量实体（构建完成后自动执行；分批，可断点续跑）
     */
    private void backfillEntityEmbeddings(String kbId) {
        try {
            LlmConfig embCfg = resolveEmbeddingModelConfig(kbId);
            if (embCfg == null) return;
            List<String> missing = graphStore.listEntitiesWithoutEmbedding(kbId, 1000);
            if (missing.isEmpty()) return;
            log.info("[GraphBuild-Embed] Backfilling {} entities without embedding for kb={}", missing.size(), kbId);
            for (int i = 0; i < missing.size(); i += 20) {
                List<String> batch = missing.subList(i, Math.min(i + 20, missing.size()));
                try {
                    List<List<Float>> vectors = embeddingService.embed(
                            embCfg.getModel(), batch, embCfg.getApiUrl(), embCfg.getApiKey());
                    for (int j = 0; j < batch.size() && j < vectors.size(); j++) {
                        graphStore.updateEntityEmbedding(kbId, batch.get(j), vectors.get(j));
                    }
                } catch (Exception e) {
                    log.warn("[GraphBuild-Embed] Backfill batch failed for kb={}: {}", kbId, e.getMessage());
                }
            }
            log.info("[GraphBuild-Embed] Backfill done for kb={}", kbId);
        } catch (Exception e) {
            log.warn("[GraphBuild-Embed] Backfill failed for kb={}: {}", kbId, e.getMessage());
        }
    }

    /**
     * 解析知识库的 embedding 模型配置（kb.embeddingModel → ModelRecord online）
     */
    private LlmConfig resolveEmbeddingModelConfig(String kbId) {
        try {
            KnowledgeBase kb = kbMapper.selectById(kbId);
            if (kb == null || kb.getEmbeddingModel() == null || kb.getEmbeddingModel().isBlank()) {
                log.info("[GraphBuild-Embed] No embedding model configured for kb={}, skip", kbId);
                return null;
            }
            String modelCode = kb.getEmbeddingModel();
            ModelRecord modelRecord = modelRecordMapper.selectOne(
                    new LambdaQueryWrapper<ModelRecord>()
                            .eq(ModelRecord::getCode, modelCode)
                            .eq(ModelRecord::getStatus, "online")
                            .last("LIMIT 1"));
            if (modelRecord == null) {
                log.warn("[GraphBuild-Embed] Embedding model '{}' not found/offline for kb={}, skip", modelCode, kbId);
                return null;
            }
            return new LlmConfig(modelCode, modelRecord.getApiUrl(), modelRecord.getApiKeyRef());
        } catch (Exception e) {
            log.warn("[GraphBuild-Embed] Failed to resolve embedding model for kb={}", kbId);
            return null;
        }
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
