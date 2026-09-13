package com.fastrag.module.knowledge.consumer;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.module.publish.service.LogService;
import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.common.handler.GraphBuildHandler;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.module.graph.util.EntityNameGuard;
import com.fastrag.module.graph.util.EntityTypeNormalizer;
import com.fastrag.module.graph.util.ExtractionNormalizer;
import com.fastrag.module.graph.util.GraphIdHashing;
import com.fastrag.module.graph.util.NameNormalizer;
import com.fastrag.module.knowledge.chunking.ExtractionUnitAssembler;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.graph.entity.KbGraphIndex;
import com.fastrag.module.graph.entity.KbGraphUnit;
import com.fastrag.module.graph.mapper.KbGraphIndexMapper;
import com.fastrag.module.graph.mapper.KbGraphUnitMapper;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 知识图谱构建消费者（RabbitMQ Consumer）——v1.2 单元抽取架构。
 *
 * <p>核心思路（LightRAG/GraphRAG 开源共识，docs/design/cross-chunk-graph-extraction.md §14）：
 * <b>抽取单元与检索分片解耦</b>。检索层保持 500 字分片（服务向量检索精度）；图谱层把文件
 * 全部分片按 chunkIndex 顺序重组为 ~2000 字符的"抽取单元"（ExtractionUnit），
 * 单次 LLM 调用抽取一个单元的实体与关系。语义完整的章节（如"营销六步法"标题+六个步骤）
 * 天然落在同一单元内，跨分片关系在单元内直接抽出。</p>
 *
 * <p>跨单元一致性只靠两件事（开源共识，无补丁层）：</p>
 * <ul>
 *   <li>确定性 ID + 归一化名合并（{@link NameNormalizer} + {@link GraphIdHashing}，Neo4j MERGE）</li>
 *   <li>证据追踪（MENTIONS / TripleMention 按名字包含归属到底层分片，支撑删除回收与 PPR）</li>
 * </ul>
 *
 * <p>v1.2 移除的历史补丁层：链式携带（carry）、文件级缝合（stitch）、headingPath 框架补边
 * （backfill）——它们的职责全部被"更大的抽取单元"天然覆盖。</p>
 *
 * <p>其他职责：</p>
 * <ul>
 *   <li>MQ 即时 ack + 应用内执行器（graph-build-worker）+ KB 级构建互斥（防重投并发）</li>
 *   <li>单元级缓存（kb_graph_unit.content_hash）：内容未变化的单元零 LLM 跳过；
 *       replay 模式重放持久化的抽取结果</li>
 *   <li>退化抽取兜底（有关系但全部缺端点 → 重试一次择优）；gleaning 补捞（默认开）</li>
 *   <li>失败单元构建内自动重试一轮</li>
 *   <li>构建完成后：embedding 回填、孤立实体/关系清理</li>
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
    private final KbGraphUnitMapper unitMapper;
    private final KbFileMapper fileMapper;
    private final KbParseStrategyMapper parseStrategyMapper;
    private final ModelRecordMapper modelRecordMapper;
    private final LogService logService;
    private final EmbeddingService embeddingService;
    private final KnowledgeBaseMapper kbMapper;

    /** 图谱构建最大并发数（单元级并行） */
    @Value("${graph.build.concurrency:15}")
    private int maxGraphBuildConcurrency;

    /** 图谱抽取 LLM 总超时（秒），默认 180s，适配 Qwen3 等慢响应模型 */
    @Value("${graph.build.llm-timeout:180}")
    private int graphBuildLlmTimeoutSeconds;

    /**
     * 图谱抽取 LLM 输出上限（tokens，0=不限制）。生产观测：500 字符的 OCR 噪声分片曾回 5000+ 字符
     * 长 JSON，单次生成 60~175s；限制输出可显著压时延（2026-09-11）。
     */
    @Value("${graph.build.llm-max-tokens:4096}")
    private int graphBuildLlmMaxTokens;

    /** gleaning 补捞开关（LightRAG 同款，默认开：对"有实体零关系"的单元补一轮） */
    @Value("${graph.build.gleaning:true}")
    private boolean gleaningEnabled;

    /** gleaning 输入护栏（字符近似 token）：超长跳过补捞 */
    private static final int GLEANING_MAX_INPUT_CHARS = 16000;

    /** 抽取单元字符预算（≈ LightRAG chunk_token_size=1200 token 的中文字符换算） */
    @Value("${graph.build.unit-max-chars:2000}")
    private int unitMaxChars;

    /** 跳过内容过短的单元（不调 LLM），单位字符数 */
    private static final int MIN_UNIT_LENGTH_FOR_EXTRACTION = 50;

    /** kb_chunk.graph_indexed 三态：已提取 */
    private static final int GRAPH_INDEXED_DONE = 1;
    /** kb_chunk.graph_indexed 三态：抽取/写入失败，增量构建自动重试 */
    private static final int GRAPH_INDEXED_FAILED = 2;

    /** kb_graph_unit.status：待抽取 / 已完成 / 失败 */
    private static final int UNIT_STATUS_TODO = 0;
    private static final int UNIT_STATUS_DONE = 1;
    private static final int UNIT_STATUS_FAILED = 2;

    /** 类型提示排除集：值型/时间型类型会诱导 LLM 把数值、日期、编号抽成实体（应作为 attributes） */
    private static final Set<String> VALUE_TYPE_EXCLUDE = Set.of(
            "数值", "日期", "时间", "时间点", "时间段", "时间范围", "时间节点",
            "发生时间", "恢复时间", "时长", "指标", "指标名称", "指标类型", "阈值", "阈值类型");

    /** 判定实体是否为垃圾的清洗闸门（代码侧兜底，规则详见 EntityNameGuard；归一化类型用于拦截属性型实体） */
    static boolean isJunkEntityName(String name, String normalizedType) {
        return EntityNameGuard.isJunkName(name, normalizedType);
    }

    /** 实体类型白名单（逗号分隔，可覆盖默认集合；未命中归 UNKNOWN） */
    @Value("${graph.entity-type-whitelist:}")
    private String entityTypeWhitelistCfg;

    /** 白名单缓存（懒加载） */
    private volatile Set<String> entityTypeWhitelistCache;

    /** 构建工作线程数：实际构建在应用内执行器跑，MQ 消费者只做调度与即时 ack */
    @Value("${graph.build.workers:3}")
    private int buildWorkers;

    /** 构建执行器（应用内，独立于 MQ 消费者线程；@PostConstruct 初始化） */
    private ExecutorService buildExecutor;

    /** 按 KB 的构建互斥集合：同 KB 并发构建（MQ 重投/用户连点）只允许一个执行 */
    private final Set<String> runningKbs = ConcurrentHashMap.newKeySet();

    @PostConstruct
    void initBuildExecutor() {
        buildExecutor = Executors.newFixedThreadPool(Math.max(1, buildWorkers), r -> {
            Thread t = new Thread(r, "graph-build-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void shutdownBuildExecutor() {
        if (buildExecutor != null) {
            buildExecutor.shutdownNow();
        }
    }

    /**
     * MQ 消费入口：只做调度与即时 ack，实际构建提交到应用内执行器（graph-build-worker）。
     *
     * <p>背景（2026-09-11 生产事故）：整库构建曾在本方法内同步执行，文件内串行链使单条消息
     * 处理时长超过 RabbitMQ consumer_timeout（默认 30min），channel ack 超时触发消息重投，
     * 而原执行线程不会被中断——同一 KB 出现 2~3 个并发构建，各自的孤儿清理互相踩踏，
     * 实体/关系计数塌陷。修复：消息即时 ack、长任务移出消费者、KB 级互斥。</p>
     */
    @Override
    @RabbitListener(queues = "fastrag.graph-build.queue", concurrency = "5")
    public void handleGraphBuild(Map<String, Object> message) {
        Object kbIdObj = message != null ? message.get("kbId") : null;
        String kbId = kbIdObj != null ? kbIdObj.toString() : null;
        if (kbId == null || kbId.isBlank()) {
            log.warn("[GraphBuild] message without kbId, dropped: {}", message);
            return;
        }
        // KB 互斥：同一 KB 同时只允许一个构建（MQ 重投/用户连点/多文件连发的重复消息直接跳过）
        if (!runningKbs.add(kbId)) {
            log.warn("[GraphBuild] kb={} is already building, duplicate build message skipped", kbId);
            return;
        }
        try {
            buildExecutor.submit(() -> {
                try {
                    runGraphBuild(message);
                } catch (Exception e) {
                    // 不向 MQ 抛异常（消息已即时 ack）；失败单元已标 graph_indexed=2，下次增量构建自动重试
                    log.error("[GraphBuild] build error for kb={}: {}", kbId, e.getMessage(), e);
                    try {
                        logService.addLog(kbId, LogCategory.operation, ActionType.graph_build_failed,
                                "", "构建异常: " + e.getMessage(), "system", "failed", null);
                    } catch (Exception ignored) { }
                } finally {
                    runningKbs.remove(kbId);
                }
            });
        } catch (RejectedExecutionException e) {
            runningKbs.remove(kbId);
            log.warn("[GraphBuild] build executor rejected task, build skipped for kb={}", kbId);
        }
    }

    /** 实际构建流程（运行在 graph-build-worker 线程，不占用 MQ 消费者） */
    private void runGraphBuild(Map<String, Object> message) {
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
        if (fileId != null && !fileId.isEmpty() && fileIds == null) {
            fileIds = List.of(fileId);
        }

        String mode = message.containsKey("mode") ? String.valueOf(message.get("mode")) : "full";
        // replay：零 LLM 成本重建——重放 kb_graph_unit 持久化的抽取结果
        boolean replayMode = "replay".equals(mode);
        // full 全量重建：忽略单元缓存强制重抽（retryBuild 已先清图）
        boolean ignoreCache = "full".equals(mode);

        log.info("========== [GraphBuild] Start ==========");
        log.info("kbId={}, fileId={}, fileIds={}, mode={}", kbId, fileId, fileIds, mode);

        try {
            logService.addLog(kbId, LogCategory.operation, ActionType.graph_build_started,
                    "", "开始构建图谱，模式: " + mode + ", 文件数: " + (fileIds != null ? fileIds.size() : 1),
                    "system", "success", null);
        } catch (Exception e) {
            log.warn("[Log] Failed to record graph build start log for kb={}", kbId);
        }

        // 获取 LLM 配置（用于实体/关系提取）；replay 模式重放持久化结果，无需 LLM
        LlmConfig llmConfig = null;
        if (replayMode) {
            log.info("[GraphBuild] replay mode: replay persisted unit extraction results");
        } else {
            llmConfig = resolveLlmConfig(kbId, fileIds);
            log.info("[GraphBuild] Resolved LLM config: model={}, apiUrl={}, apiKeySet={}",
                    llmConfig.getModel(),
                    llmConfig.getApiUrl() != null ? "***provided***" : "null",
                    llmConfig.getApiKey() != null ? "***provided***" : "null");
        }

        // LLM 不可用时标记构建失败（而非静默跳过所有单元）
        if (!replayMode && (llmConfig.getApiUrl() == null || llmConfig.getApiUrl().isBlank())) {
            log.error("[GraphBuild] LLM not configured (apiUrl is null/blank). Graph build cannot proceed. " +
                    "Ensure the file's parse strategy has a valid LLM model that is 'online' in model_config.");
            try {
                updateGraphStatus(kbId, "failed", 0, 0, 0, 0, 0, 0);
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
            final LlmConfig buildLlmConfig = llmConfig;
            final Set<String> entityTypeWhitelist = resolveEntityTypeWhitelist(kbId);
            log.info("[GraphBuild] Entity type whitelist for kb={}: {} types", kbId, entityTypeWhitelist.size());

            // 查询范围内全部分片（单元组装需要完整文件内容；增量/全量的差异由单元缓存承担）
            List<KbChunk> chunks = queryChunks(kbId, fileIds);
            if (chunks.isEmpty()) {
                log.info("No chunks found for graph build, kb: {}, fileIds: {}", kbId, fileIds);
                updateGraphStatus(kbId, "completed", 100, 0, 0, 0, 0, 0);
                return;
            }
            long kbTotalChunks = chunkMapper.selectCount(
                    new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getKbId, kbId));

            // 单元组装：文件内按 chunkIndex 顺序重组为 ~unit-max-chars 的抽取单元
            Map<String, List<KbChunk>> fileGroups = groupChunksByFile(chunks);
            List<String> orderedFileIds = new ArrayList<>(fileGroups.keySet());
            List<List<ExtractionUnitAssembler.ExtractionUnit>> fileUnits = new ArrayList<>(fileGroups.size());
            int totalUnits = 0;
            for (String fid : orderedFileIds) {
                List<ExtractionUnitAssembler.ExtractionUnit> units =
                        ExtractionUnitAssembler.assemble(fileGroups.get(fid), unitMaxChars, GraphIdHashing::hashstr32);
                fileUnits.add(units);
                totalUnits += units.size();
            }
            log.info("[GraphBuild] {} chunks across {} files -> {} extraction units (unit-max-chars={}, mode={})",
                    chunks.size(), fileGroups.size(), totalUnits, unitMaxChars,
                    replayMode ? "replay" : (ignoreCache ? "full(no-cache)" : "incremental(cache)"));

            UnitRun run = new UnitRun(kbId, buildLlmConfig, entityTypeWhitelist, replayMode, ignoreCache, totalUnits);

            int concurrency = Math.min(maxGraphBuildConcurrency, Math.max(1, totalUnits / 2));
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    concurrency, concurrency, 60, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(Math.max(totalUnits, 1)));
            List<CompletableFuture<Void>> futures = new ArrayList<>(totalUnits);

            for (int f = 0; f < orderedFileIds.size(); f++) {
                String groupFileId = orderedFileIds.get(f);
                List<ExtractionUnitAssembler.ExtractionUnit> units = fileUnits.get(f);
                Map<Integer, KbGraphUnit> existingRows = loadUnitRows(kbId, groupFileId, units.size());
                for (ExtractionUnitAssembler.ExtractionUnit unit : units) {
                    KbGraphUnit row = existingRows.get(unit.index());
                    futures.add(CompletableFuture.runAsync(() -> processUnit(run, groupFileId, unit, row, true), executor));
                }
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 失败单元构建内自动重试一轮：LLM 瞬态超时/输出退化大多可恢复
            if (!run.failedUnitsList.isEmpty()) {
                List<UnitAttempt> failedOnce = new ArrayList<>(run.failedUnitsList);
                log.warn("[GraphBuild] Retrying {} failed unit(s) once within this build", failedOnce.size());
                List<CompletableFuture<Void>> retryFutures = new ArrayList<>(failedOnce.size());
                for (UnitAttempt attempt : failedOnce) {
                    retryFutures.add(CompletableFuture.runAsync(() -> {
                        int before = run.failedUnits.get();
                        processUnit(run, attempt.fileId(), attempt.unit(), attempt.row(), false);
                        if (run.failedUnits.get() < before) {
                            log.info("[GraphBuild] Unit recovered on in-build retry: file={}, unit={}",
                                    attempt.fileId(), attempt.unit().index());
                        }
                    }, executor));
                }
                CompletableFuture.allOf(retryFutures.toArray(new CompletableFuture[0])).join();
            }
            executor.shutdown();

            // 使用实时查询获取全库统计
            long liveEntityCount = 0;
            long liveRelationCount = 0;
            try {
                liveEntityCount = graphStore.countEntities(kbId);
                liveRelationCount = graphStore.countRelations(kbId);
            } catch (Exception e) {
                log.warn("[GraphBuild] Failed to get live counts: {}", e.getMessage());
            }
            long finalBuiltChunks = chunkMapper.selectCount(
                    new LambdaQueryWrapper<KbChunk>()
                            .eq(KbChunk::getKbId, kbId)
                            .eq(KbChunk::getGraphIndexed, 1));
            updateGraphStatus(kbId, "completed", 100, (int) liveEntityCount, (int) liveRelationCount,
                    (int) kbTotalChunks, (int) finalBuiltChunks, run.failedUnits.get());
            log.info("Graph build completed for kb: {}, entities: {}, relations: {}, units: {}/{} (cached={}, failed={})",
                    kbId, liveEntityCount, liveRelationCount,
                    totalUnits - run.failedUnits.get(), totalUnits, run.cachedUnits.get(), run.failedUnits.get());
            if (run.failedUnits.get() > 0) {
                log.error("[GraphBuild] {} unit(s) failed extraction/write for kb={} — 已标记 graph_indexed=2，" +
                        "下次增量构建将自动重试；若持续失败请检查 LLM/Neo4j 配置", run.failedUnits.get(), kbId);
            }

            // 回填存量实体 embedding（新增实体已在构建时生成，这里只补历史缺失）
            backfillEntityEmbeddings(kbId);

            // 清理孤立实体/关系（删除文件或编辑 chunk 后残留的无引用数据）
            graphStore.cleanupOrphanNodes(kbId);

            // 记录图谱构建完成日志
            try {
                logService.addLog(kbId, LogCategory.operation, ActionType.graph_build_completed,
                        "", "实体: " + liveEntityCount + ", 关系: " + liveRelationCount
                                + ", 单元: " + (totalUnits - run.failedUnits.get()) + "/" + totalUnits,
                        "system", "success", null);
            } catch (Exception e) {
                log.warn("[Log] Failed to record graph build complete log for kb={}", kbId);
            }

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

    // ==================== 单元处理 ====================

    /** 单次构建的共享运行时状态 */
    private static final class UnitRun {
        final String kbId;
        final LlmConfig llmConfig;
        final Set<String> whitelist;
        final boolean replay;
        final boolean ignoreCache;
        final int totalUnits;
        final AtomicInteger processed = new AtomicInteger();
        final AtomicInteger cachedUnits = new AtomicInteger();
        final AtomicInteger failedUnits = new AtomicInteger();
        final AtomicInteger valueSkipped = new AtomicInteger();
        final List<UnitAttempt> failedUnitsList = Collections.synchronizedList(new ArrayList<>());

        UnitRun(String kbId, LlmConfig llmConfig, Set<String> whitelist,
                boolean replay, boolean ignoreCache, int totalUnits) {
            this.kbId = kbId;
            this.llmConfig = llmConfig;
            this.whitelist = whitelist;
            this.replay = replay;
            this.ignoreCache = ignoreCache;
            this.totalUnits = totalUnits;
        }
    }

    /** 失败单元引用（构建内重试用） */
    private record UnitAttempt(String fileId, ExtractionUnitAssembler.ExtractionUnit unit, KbGraphUnit row) {
    }

    /** 加载文件现有单元行（unitIndex → row），并删除越界的旧行 */
    private Map<Integer, KbGraphUnit> loadUnitRows(String kbId, String fileId, int unitCount) {
        List<KbGraphUnit> rows = unitMapper.selectList(new LambdaQueryWrapper<KbGraphUnit>()
                .eq(KbGraphUnit::getKbId, kbId)
                .eq(KbGraphUnit::getFileId, fileId));
        Map<Integer, KbGraphUnit> byIndex = new HashMap<>();
        for (KbGraphUnit row : rows) {
            if (row.getUnitIndex() == null || row.getUnitIndex() >= unitCount) {
                unitMapper.deleteById(row.getId()); // 旧行越界（文件分片变少）：清理
            } else {
                byIndex.put(row.getUnitIndex(), row);
            }
        }
        return byIndex;
    }

    /**
     * 处理单个抽取单元：缓存命中跳过 / LLM 抽取（或 replay 重放）/ 写库 / 单元行与 chunk 状态回写。
     *
     * @param firstAttempt 首次尝试（计入失败统计与重试清单）；构建内重试传 false
     */
    private void processUnit(UnitRun run, String fileId, ExtractionUnitAssembler.ExtractionUnit unit,
                             KbGraphUnit row, boolean firstAttempt) {
        try {
            // 过短单元（< 50 字符）无需抽取：写空结果并标完成
            if (unit.content().trim().length() < MIN_UNIT_LENGTH_FOR_EXTRACTION) {
                upsertUnitRow(run.kbId, fileId, unit, row,
                        new ExtractionNormalizer.ExtractionResult(), UNIT_STATUS_DONE);
                markUnitChunks(unit, GRAPH_INDEXED_DONE);
                return;
            }

            ExtractionNormalizer.ExtractionResult result = null;
            boolean fromCache = false;
            if (run.replay) {
                if (row != null && row.getExtractionResult() != null && !row.getExtractionResult().isBlank()) {
                    result = deserializeExtractionResult(row.getExtractionResult());
                }
                if (result == null) {
                    log.warn("[GraphBuild-Replay] unit file={}, index={} has no persisted result, marked failed (needs LLM build)",
                            fileId, unit.index());
                    markUnitChunks(unit, GRAPH_INDEXED_FAILED);
                    upsertUnitRow(run.kbId, fileId, unit, row, null, UNIT_STATUS_FAILED);
                    run.failedUnits.incrementAndGet();
                    return;
                }
            } else if (!run.ignoreCache && row != null
                    && Integer.valueOf(UNIT_STATUS_DONE).equals(row.getStatus())
                    && unit.contentHash().equals(row.getContentHash())
                    && row.getExtractionResult() != null && !row.getExtractionResult().isBlank()) {
                // 单元级缓存命中：内容未变化的单元直接跳过（零 LLM 成本）
                result = deserializeExtractionResult(row.getExtractionResult());
                if (result != null) {
                    fromCache = true;
                    run.cachedUnits.incrementAndGet();
                } else {
                    log.warn("[GraphBuild] cached unit result corrupted, re-extracting: file={}, unit={}",
                            fileId, unit.index());
                }
            }

            if (!fromCache) {
                result = extractWithNormalization(unit.content(), run.llmConfig.getModel(), run.llmConfig.getApiUrl(),
                        run.llmConfig.getApiKey(), run.whitelist);
                if (result == null) {
                    // 抽取失败：单元 chunk 标 graph_indexed=2，单元行标失败
                    markUnitChunks(unit, GRAPH_INDEXED_FAILED);
                    upsertUnitRow(run.kbId, fileId, unit, row, null, UNIT_STATUS_FAILED);
                    if (firstAttempt) {
                        run.failedUnits.incrementAndGet();
                        run.failedUnitsList.add(new UnitAttempt(fileId, unit, row));
                    }
                    log.warn("[GraphBuild] Unit extraction failed, marked for retry: file={}, unit={}",
                            fileId, unit.index());
                    return;
                }
                upsertUnitRow(run.kbId, fileId, unit, row, result, UNIT_STATUS_DONE);
            }

            writeUnitGraph(run.kbId, unit, result, run);
            markUnitChunks(unit, GRAPH_INDEXED_DONE);

        } catch (Exception e) {
            // 图谱写入失败（Neo4j 不可达/约束冲突等）：标记失败待重试，不静默吞掉
            markUnitChunks(unit, GRAPH_INDEXED_FAILED);
            upsertUnitRow(run.kbId, fileId, unit, row, null, UNIT_STATUS_FAILED);
            if (firstAttempt) {
                run.failedUnits.incrementAndGet();
                run.failedUnitsList.add(new UnitAttempt(fileId, unit, row));
            }
            log.warn("Failed to process unit file={}, unit={}: {}", fileId, unit.index(), e.getMessage());
        } finally {
            int done = run.processed.incrementAndGet();
            if (done % 5 == 0 || done == run.totalUnits) {
                updateGraphProgress(run.kbId, done, run.totalUnits, run.failedUnits.get());
            }
        }
    }

    /**
     * 单元图谱写入：实体（垃圾闸门 + 确定性 ID + 证据按名字包含归属到分片）、
     * 关系（端点解析 + 自环防护 + TripleMention 归属）、Chunk 节点、embedding。
     */
    private void writeUnitGraph(String kbId, ExtractionUnitAssembler.ExtractionUnit unit,
                                ExtractionNormalizer.ExtractionResult result, UnitRun run) {
        List<String> entityNamesForEmbed = new ArrayList<>();
        Map<String, String> entityIdByNormalizedName = new HashMap<>();
        if (result.getEntities() != null) {
            for (ExtractionNormalizer.Entity entity : result.getEntities()) {
                String type = EntityTypeNormalizer.normalize(entity.getLabel(), run.whitelist);
                // 垃圾实体闸门：值型内容/键值对残留/OCR乱码/表头泛化词/属性型实体不入图
                if (isJunkEntityName(entity.getText(), type)) {
                    run.valueSkipped.incrementAndGet();
                    continue;
                }
                String normalizedName = NameNormalizer.normalize(entity.getText());
                String entityId = GraphIdHashing.entityId(kbId, normalizedName);
                String attributesJson = (entity.getAttributes() != null && !entity.getAttributes().isEmpty())
                        ? JSONUtil.toJsonStr(entity.getAttributes()) : null;
                graphStore.createEntity(kbId, entityId, entity.getText(), normalizedName, type,
                        entity.getDescription(), attributesJson);
                // 证据归属：单元内名字包含命中的分片都挂 MENTIONS（删除回收/PPR 的依据）
                attributeEntityMentions(kbId, entity.getText(), normalizedName, entityId, unit);
                entityIdByNormalizedName.put(normalizedName, entityId);
                entityNamesForEmbed.add(entity.getText());
            }
        }

        if (result.getRelations() != null) {
            for (ExtractionNormalizer.Relation rel : result.getRelations()) {
                String sourceName = rel.getSource() != null ? rel.getSource().toString() : "";
                String targetName = rel.getTarget() != null ? rel.getTarget().toString() : "";
                if (sourceName.isEmpty() || targetName.isEmpty()) continue;
                if (isJunkEntityName(sourceName, null) || isJunkEntityName(targetName, null)) continue;

                String sourceNorm = NameNormalizer.normalize(sourceName);
                String targetNorm = NameNormalizer.normalize(targetName);
                if (sourceNorm.equals(targetNorm)) continue; // 自环防护（KG-05）

                String tripleId = GraphIdHashing.tripleId(kbId, sourceName, "Entity", rel.getLabel(), targetName, "Entity");
                String relContent = sourceName + " -> " + rel.getLabel() + " -> " + targetName;
                String sourceId = resolveRelationEndpointId(kbId, sourceName, sourceNorm,
                        entityIdByNormalizedName, unit);
                String targetId = resolveRelationEndpointId(kbId, targetName, targetNorm,
                        entityIdByNormalizedName, unit);
                graphStore.createRelation(kbId, tripleId, sourceId, sourceName, sourceNorm,
                        targetId, targetName, targetNorm, rel.getLabel(), relContent);
                attributeTripleMention(kbId, tripleId, sourceName, sourceNorm, targetName, targetNorm, unit, fileIdOf(unit));
                entityNamesForEmbed.add(sourceName);
                entityNamesForEmbed.add(targetName);
            }
        }

        // 在 Neo4j 中创建 Chunk 节点（携带 fileId 用于按文件删除）
        for (KbChunk c : unit.chunks()) {
            graphStore.createChunk(kbId, c.getId(), c.getFileId(),
                    c.getContent() != null && c.getContent().length() > 200
                            ? c.getContent().substring(0, 200) : c.getContent());
        }

        // 为实体生成 embedding（向量检索用；失败仅告警，不影响构建）
        updateEntityEmbeddings(kbId, entityNamesForEmbed);
    }

    private String fileIdOf(ExtractionUnitAssembler.ExtractionUnit unit) {
        return unit.chunks().isEmpty() ? null : unit.chunks().get(0).getFileId();
    }

    /** 单元内证据归属：实体名（原文/归一化名）包含命中的分片都挂 MENTIONS；全不命中挂单元首个分片 */
    private void attributeEntityMentions(String kbId, String displayName, String normalizedName, String entityId,
                                         ExtractionUnitAssembler.ExtractionUnit unit) {
        boolean matched = false;
        for (KbChunk c : unit.chunks()) {
            String content = c.getContent();
            if (content == null) continue;
            if (content.contains(displayName)
                    || (!normalizedName.isEmpty() && content.toLowerCase().contains(normalizedName))) {
                graphStore.createEntityMention(kbId, displayName, entityId, c.getId(), c.getFileId());
                matched = true;
            }
        }
        if (!matched && !unit.chunks().isEmpty()) {
            KbChunk first = unit.chunks().get(0);
            graphStore.createEntityMention(kbId, displayName, entityId, first.getId(), first.getFileId());
        }
    }

    /** TripleMention 归属：关系两端名字都被分片内容包含的分片；全不命中挂单元首个分片 */
    private void attributeTripleMention(String kbId, String tripleId, String srcName, String srcNorm,
                                        String tgtName, String tgtNorm,
                                        ExtractionUnitAssembler.ExtractionUnit unit, String fileId) {
        boolean matched = false;
        for (KbChunk c : unit.chunks()) {
            String content = c.getContent();
            if (content == null) continue;
            String lower = content.toLowerCase();
            boolean hasSrc = content.contains(srcName) || (!srcNorm.isEmpty() && lower.contains(srcNorm));
            boolean hasTgt = content.contains(tgtName) || (!tgtNorm.isEmpty() && lower.contains(tgtNorm));
            if (hasSrc && hasTgt) {
                graphStore.createTripleMention(kbId, tripleId, c.getId(), fileId);
                matched = true;
            }
        }
        if (!matched && !unit.chunks().isEmpty()) {
            KbChunk first = unit.chunks().get(0);
            graphStore.createTripleMention(kbId, tripleId, first.getId(), fileId);
        }
    }

    /** 单元行 upsert（status/结果/哈希），缓存命中的单元不重写 */
    private void upsertUnitRow(String kbId, String fileId, ExtractionUnitAssembler.ExtractionUnit unit,
                               KbGraphUnit existing, ExtractionNormalizer.ExtractionResult result, int status) {
        String chunkIdsJson = JSONUtil.toJsonStr(unit.chunks().stream().map(KbChunk::getId).toList());
        String resultJson = result != null ? JSONUtil.toJsonStr(result) : null;
        if (existing != null && existing.getId() != null) {
            existing.setContentHash(unit.contentHash());
            existing.setChunkIds(chunkIdsJson);
            existing.setExtractionResult(resultJson);
            existing.setStatus(status);
            existing.setUpdatedAt(LocalDateTime.now());
            unitMapper.updateById(existing);
        } else {
            KbGraphUnit u = new KbGraphUnit();
            u.setKbId(kbId);
            u.setFileId(fileId);
            u.setUnitIndex(unit.index());
            u.setContentHash(unit.contentHash());
            u.setChunkIds(chunkIdsJson);
            u.setExtractionResult(resultJson);
            u.setStatus(status);
            u.setCreatedAt(LocalDateTime.now());
            unitMapper.insert(u);
        }
    }

    private void markUnitChunks(ExtractionUnitAssembler.ExtractionUnit unit, int state) {
        for (KbChunk c : unit.chunks()) {
            markChunkGraphState(c.getId(), state, null);
        }
    }

    /** 按文件分组并组内按 chunkIndex 排序（单元组装的基础顺序） */
    static Map<String, List<KbChunk>> groupChunksByFile(List<KbChunk> chunks) {
        Map<String, List<KbChunk>> groups = new LinkedHashMap<>();
        for (KbChunk c : chunks) {
            String fid = c.getFileId() != null ? c.getFileId() : "";
            groups.computeIfAbsent(fid, k -> new ArrayList<>()).add(c);
        }
        for (List<KbChunk> list : groups.values()) {
            list.sort(Comparator
                    .comparing(KbChunk::getChunkIndex, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(c -> c.getId() != null ? c.getId() : ""));
        }
        return groups;
    }

    /** 查询范围内的全部分片（单元组装需要完整文件内容；增量/全量的差异由单元缓存承担） */
    private List<KbChunk> queryChunks(String kbId, List<String> fileIds) {
        LambdaQueryWrapper<KbChunk> w = new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getKbId, kbId);
        if (fileIds != null && !fileIds.isEmpty()) {
            w.in(KbChunk::getFileId, fileIds);
        }
        return chunkMapper.selectList(w);
    }

    // ==================== LLM 抽取 ====================

    /**
     * 从单元文本中提取实体和关系（调用 LLM + ExtractionNormalizer 规范化）。
     *
     * @return 规范化结果（可能为空，表示 LLM 正常返回但未提取到实体/关系，非失败）；
     *         LLM 调用失败/超时/响应不可解析等瞬态失败返回 null
     */
    private ExtractionNormalizer.ExtractionResult extractWithNormalization(
            String text, String llmModel, String apiUrl, String apiKey, Set<String> whitelist) {
        if (apiUrl == null || apiUrl.isBlank()) {
            log.warn("[GraphBuild-Extract] LLM not configured (apiUrl is null/blank) -> skipping extraction. " +
                    "Set LLM model in parsing strategy or ensure model is 'online' in model_config.");
            return null;
        }
        String model = llmModel != null ? llmModel : "default";
        String prompt = buildExtractionPromptText(text, whitelist);
        log.info("[GraphBuild-Extract] Calling LLM model={} for entity extraction, text length={}", model, text.length());

        try {
            String response = callExtractionLlm(model, prompt, apiUrl, apiKey);
            if (response == null) return null;
            ExtractionNormalizer.ExtractionResult normalized = parseLlmExtractionJson(response);
            if (normalized == null) return null;

            // 截断/格式退化兜底：有关系但全部缺端点（输出被截断或格式退化）——重试一次，按实体+关系总数择优
            if (isDegenerateExtraction(normalized)) {
                log.warn("[GraphBuild-Extract] Degenerate extraction detected (all relations lack endpoints), retrying once, textLen={}",
                        text.length());
                String retryResponse = callExtractionLlm(model, prompt, apiUrl, apiKey);
                if (retryResponse != null) {
                    ExtractionNormalizer.ExtractionResult retried = parseLlmExtractionJson(retryResponse);
                    if (retried != null && extractionScore(retried) > extractionScore(normalized)) {
                        normalized = retried;
                        log.info("[GraphBuild-Extract] Degenerate extraction replaced by retry: {} entities, {} relations",
                                retried.getEntities() != null ? retried.getEntities().size() : 0,
                                retried.getRelations() != null ? retried.getRelations().size() : 0);
                    }
                }
            }
            log.info("[GraphBuild-Extract] After normalization: {} entities, {} relations",
                    normalized.getEntities() != null ? normalized.getEntities().size() : 0,
                    normalized.getRelations() != null ? normalized.getRelations().size() : 0);

            // Gleaning 补捞（LightRAG 同款）：抽到实体但零关系的单元追加一轮"只补漏不重复"
            if (gleaningEnabled && shouldGlean(normalized)) {
                ExtractionNormalizer.ExtractionResult gleaned = gleanRelations(text, model, apiUrl, apiKey, normalized);
                if (gleaned != null) {
                    normalized = mergeExtractionResults(normalized, gleaned);
                    log.info("[GraphBuild-Glean] After gleaning: {} entities, {} relations",
                            normalized.getEntities() != null ? normalized.getEntities().size() : 0,
                            normalized.getRelations() != null ? normalized.getRelations().size() : 0);
                }
            }
            return normalized;
        } catch (Exception e) {
            log.warn("[GraphBuild-Extract] Failed to parse LLM response for entity extraction: {}", e.getMessage(), e);
            return null;
        }
    }

    /** 调抽取 LLM（流式 + 关闭 thinking + 自定义超时/输出上限）；失败/空响应返回 null */
    private String callExtractionLlm(String model, String prompt, String apiUrl, String apiKey) {
        String response;
        try {
            // 关闭 thinking 避免 Qwen3 等模型输出大量 <think...> 推理文本导致超时
            if (graphBuildLlmMaxTokens > 0) {
                response = llmService.chatWithTimeout(model, prompt, apiUrl, apiKey,
                        false, graphBuildLlmTimeoutSeconds, graphBuildLlmMaxTokens);
            } else {
                response = llmService.chatWithTimeout(model, prompt, apiUrl, apiKey,
                        false, graphBuildLlmTimeoutSeconds);
            }
        } catch (Exception e) {
            log.warn("[GraphBuild-Extract] LLM call failed (timeout={}s): {}",
                    graphBuildLlmTimeoutSeconds, e.getMessage());
            return null;
        }
        if (response == null || response.isBlank()) {
            log.warn("[GraphBuild-Extract] Empty LLM response");
            return null;
        }
        log.info("[GraphBuild-Extract] LLM responded, response length={}", response.length());
        return response;
    }

    /**
     * 清理 LLM 响应（markdown 围栏/前后杂文本）并解析为规范化抽取结果；不可解析返回 null
     */
    private ExtractionNormalizer.ExtractionResult parseLlmExtractionJson(String response) {
        String json = response.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("```json?|```", "").trim();
        }
        int braceStart = json.indexOf('{');
        if (braceStart > 0) {
            json = json.substring(braceStart);
        }
        if (!json.startsWith("{") && !json.startsWith("[")) {
            log.warn("[GraphBuild-Extract] Response is not JSON: {}", response);
            return null;
        }
        ExtractionNormalizer.ExtractionResult rawResult;
        try {
            rawResult = JSONUtil.toBean(json, ExtractionNormalizer.ExtractionResult.class);
        } catch (Exception je) {
            log.warn("[GraphBuild-Extract] JSON parse failed, trying regex fallback: {}", je.getMessage());
            rawResult = parseExtractionResultWithFallback(json);
        }
        log.info("[GraphBuild-Extract] Parsed result: {} entities, {} relations",
                rawResult.getEntities() != null ? rawResult.getEntities().size() : 0,
                rawResult.getRelations() != null ? rawResult.getRelations().size() : 0);
        return ExtractionNormalizer.normalize(rawResult);
    }

    /** gleaning 触发条件：抽到了实体但一条关系都没有（列举句/跨句关系漏抽高发场景） */
    static boolean shouldGlean(ExtractionNormalizer.ExtractionResult result) {
        return result != null
                && result.getEntities() != null && !result.getEntities().isEmpty()
                && (result.getRelations() == null || result.getRelations().isEmpty());
    }

    /**
     * 抽取结果退化判定：有关系但全部缺端点（LLM 输出被截断/格式退化的典型症状，
     * 2026-09-11 生产观测：含"营销六步法"整段的分片只抽出 1 实体 + 4 条空 RELATED_TO）。
     */
    static boolean isDegenerateExtraction(ExtractionNormalizer.ExtractionResult result) {
        if (result == null || result.getRelations() == null || result.getRelations().isEmpty()) return false;
        for (ExtractionNormalizer.Relation r : result.getRelations()) {
            String s = r.getSource() != null ? String.valueOf(r.getSource()).trim() : "";
            String t = r.getTarget() != null ? String.valueOf(r.getTarget()).trim() : "";
            if (!s.isEmpty() && !t.isEmpty()) return false;
        }
        return true;
    }

    /** 抽取结果质量评分（实体+关系总数），退化重试时择优用 */
    static int extractionScore(ExtractionNormalizer.ExtractionResult result) {
        if (result == null) return 0;
        int entities = result.getEntities() != null ? result.getEntities().size() : 0;
        int relations = result.getRelations() != null ? result.getRelations().size() : 0;
        return entities + relations;
    }

    /**
     * gleaning 补捞（LightRAG entity_continue_extraction 同款）：把首轮结果连同原文再问一次，
     * 只补漏不重复。返回 null 表示跳过（输入超护栏/LLM 失败/结果不可解析），调用方沿用首轮结果。
     */
    private ExtractionNormalizer.ExtractionResult gleanRelations(
            String text, String model, String apiUrl, String apiKey, ExtractionNormalizer.ExtractionResult first) {
        String firstJson = JSONUtil.toJsonStr(first);
        if (text.length() + firstJson.length() > GLEANING_MAX_INPUT_CHARS) {
            log.info("[GraphBuild-Glean] Skip gleaning: input too long ({} chars)",
                    text.length() + firstJson.length());
            return null;
        }
        String prompt = buildGleaningPrompt(text, firstJson);
        log.info("[GraphBuild-Glean] Running gleaning round, text length={}, firstResult={} chars",
                text.length(), firstJson.length());
        String response = callExtractionLlm(model, prompt, apiUrl, apiKey);
        if (response == null) return null;
        return parseLlmExtractionJson(response);
    }

    /** 组装 gleaning 补捞 Prompt（纯拼接，禁止 Formatter——同 buildExtractionPromptText） */
    static String buildGleaningPrompt(String text, String firstResultJson) {
        return "你之前从文本中抽取了实体和关系，第一轮抽取结果（JSON）如下：\n"
                + firstResultJson + "\n\n"
                + """
                请回顾原文，完成补捞：
                - 只补充第一轮遗漏的实体和实体间关系，以及修正明显的错误
                - 不要重复输出第一轮已抽取的实体
                - 特别注意：被列举但没有展开描述的实体（如"支持A、B、C"中的 B、C），补上它们与上下文实体的关系
                - 每个关系必须连接原文中出现的两个不同实体，方向从主体指向客体
                - 没有遗漏则返回 {"entities":[],"relations":[]}
                - 只返回JSON，无其他文字

                原文：
                """
                + text;
    }

    /** 合并首轮与补捞结果：列表拼接后统一 normalize（同名实体属性并集、description 择长、关系端点解析） */
    static ExtractionNormalizer.ExtractionResult mergeExtractionResults(
            ExtractionNormalizer.ExtractionResult first, ExtractionNormalizer.ExtractionResult gleaned) {
        List<ExtractionNormalizer.Entity> entities = new ArrayList<>();
        if (first.getEntities() != null) entities.addAll(first.getEntities());
        if (gleaned.getEntities() != null) entities.addAll(gleaned.getEntities());
        List<ExtractionNormalizer.Relation> relations = new ArrayList<>();
        if (first.getRelations() != null) relations.addAll(first.getRelations());
        if (gleaned.getRelations() != null) relations.addAll(gleaned.getRelations());
        ExtractionNormalizer.ExtractionResult merged = new ExtractionNormalizer.ExtractionResult();
        merged.setEntities(entities);
        merged.setRelations(relations);
        return ExtractionNormalizer.normalize(merged);
    }

    /**
     * JSON 解析兜底：当 JSONUtil.toBean 失败时，尝试正则提取 entities 和 relations 数组
     */
    private ExtractionNormalizer.ExtractionResult parseExtractionResultWithFallback(String json) {
        ExtractionNormalizer.ExtractionResult result = new ExtractionNormalizer.ExtractionResult();
        result.setEntities(new ArrayList<>());
        result.setRelations(new ArrayList<>());
        try {
            java.util.regex.Matcher entityMatcher = java.util.regex.Pattern.compile(
                    "\"entities\"\\s*[:=]?\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL).matcher(json);
            if (entityMatcher.find()) {
                String entityContent = entityMatcher.group(1).trim();
                if (!entityContent.isEmpty()) {
                    java.util.regex.Matcher textMatcher = java.util.regex.Pattern.compile(
                            "\"text\"\\s*[:=]?\\s*\"([^\"]+)\"").matcher(entityContent);
                    java.util.regex.Matcher labelMatcher = java.util.regex.Pattern.compile(
                            "\"label\"\\s*[:=]?\\s*\"([^\"]+)\"").matcher(entityContent);
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

            java.util.regex.Matcher relMatcher = java.util.regex.Pattern.compile(
                    "\"relations\"\\s*[:=]?\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL).matcher(json);
            if (relMatcher.find()) {
                String relContent = relMatcher.group(1).trim();
                if (!relContent.isEmpty()) {
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

    /** 表格型内容判定：以 | 开头的行数占比达到阈值视为 BOM 价目表/清单 */
    private static boolean isTableDominant(String text) {
        if (text == null) return false;
        String[] lines = text.split("\n");
        int tableLines = 0;
        for (String line : lines) {
            if (line.trim().startsWith("|")) tableLines++;
        }
        return tableLines >= 3 && tableLines * 3 >= lines.length;
    }

    /**
     * 构建 LLM 提取 Prompt（强调关系提取；实体附带 description 与 attributes）。
     *
     * <p>约束设计对齐 LightRAG 抽取 prompt 的经验：禁止值型/单据号实体、名称保持原文、
     * description 不得照抄名称、关系方向从主体指向客体、禁止输出互为反向的重复关系、
     * 命名一致性（跨单元实体对齐主要靠归一化名合并兜底）。</p>
     */
    static String buildExtractionPromptText(String text, Set<String> whitelist) {
        // 保留完整单元文本以利于关系提取（单元本身已按预算组装，不再二次截断）
        String truncated = text.substring(0, Math.min(text.length(), 8000));
        // 类型白名单提示（限制 LLM 输出类型集合，收敛类型爆炸）；
        // 排除值型/时间型类型（避免与"禁止值型实体"约束自相矛盾）；
        // 排序保证提示稳定——Set 无序，截断会导致每次构建的类型提示随机漂移
        String typeHint = whitelist.stream()
                .filter(t -> !VALUE_TYPE_EXCLUDE.contains(t))
                .sorted()
                .limit(160)
                .collect(Collectors.joining("、"));

        String tableRules = "";
        String flowRules;
        if (isTableDominant(text)) {
            // 表格专用规则（BOM 价目表通道）：解决"内存卡→包含→总价"式的表头汇聚垃圾边
            tableRules = """
                    - 这是产品/设备价目表格：每个设备/产品型号（如 华为B671-S2、TP LINK CT4WS-P V2）抽为一个实体（label 用"设备"或"产品"）
                    - 价格、数量、容量等数值一律作为设备实体的 attributes（如 {"text":"619","label":"价格"}），禁止把数值或含金额的行抽成实体
                    - 表头词与费用汇总词（总价、价格、单价、工费、辅材、费用、合计）禁止抽成实体
                    - 套餐/标准包/礼包名称（如"4个摄像头标准包"）抽为实体，套餐与设备之间输出"包含"关系，方向必须是 套餐→设备
                    """;
            flowRules = "";
        } else {
            flowRules = """
                    - 每个关系必须连接两个文本中出现的不同实体，方向从主体指向客体（如"产品包"包含"设备"、"人员"属于"组织"、"方法"包含"步骤"）
                    - 有序方法/流程（如"一备、二问、三看"六步法）要把框架整体和每个步骤都抽为实体，步骤与框架之间输出"属于"关系并保持原文序号
                    - 文本中的章节标题（如"XX业务营销六步法"这类独立成行的标题行）是有业务意义的框架概念时，必须抽为实体，并为其与正文中从属于该框架的步骤/条目实体建立"属于"关系（方向：条目→框架）
                    - 实体命名保持一致：同一事物在全文中用同一个名称
                    """;
        }

        return "从文本中提取实体和实体间的关系，返回JSON。\n\n"
                + "先找出所有实体，再找出实体之间的明确关系。\n\n"
                + """
                {"entities":[{"text":"实体名称","label":"实体类型","description":"一句话描述","attributes":[{"text":"属性值","label":"属性名"}]}],"relations":[{"source":"实体名称","target":"实体名称","label":"关系类型"}]}

                要求：
                - 实体必须是文本中有独立业务含义的事物（产品、设备、组织、人员、流程、服务、套餐、场景等）
                - 禁止把纯数值、日期时间、电话号码、订单号/流水号/编号等单据信息抽成实体；这类信息应作为相关实体的 attributes
                - 禁止把表单/UI界面的字段标签抽成实体（如"受理人""受理时间""主用DNS""商品数量：10"这类界面截图残留）
                - 禁止抽取含义空泛的占位词（价格、总价、型号、费用、产品名称、信息 等）；实体名出现中日/中西文字符混杂的乱码时直接跳过
                - 实体名称必须原样取自文本，不要改写、翻译或"纠正"原文
                - description 必须与实体名称不同，用一句话说明该实体是什么；不知道就简要概括，不得照抄名称
                - 同一条关系不要重复输出，也不要输出方向相反的同一关系
                - 关系类型要具体（如：属于、位于、使用、创建、包含、配置、管理、依赖、部署）
                - 实体类型（label）必须从以下集合中选择（选择最贴切的一个，不要自造类型）：
                """
                + typeHint + "\n\n"
                + tableRules + flowRules
                + """
                - 每个实体可附带 0-5 个属性（attributes），属性是文本中与该实体直接相关的键值信息（如价格、型号、规格、要求、数量），属性值必须是文本中出现的内容
                - 只返回JSON，无其他文字
                - 无关系则返回 {"entities":[],"relations":[]}

                文本：
                """
                + truncated;
    }

    // ==================== 解析 LLM 配置 ====================

    /**
     * 解析 LLM 配置：优先从 file 对应的 parse strategy 获取，fallback 到 KB 级 graphLlmModel。
     * 全库重建（无 fileIds）时回退到该知识库的任意文件解析，避免"清空后全量重建"必然失败。
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

        KbFile file = fileMapper.selectById(fileIds.get(0));
        if (file == null) {
            log.warn("[GraphBuild-LlmConfig] File not found for id={} -> returning empty config", fileIds.get(0));
            return new LlmConfig(null, null, null);
        }

        if (file.getParseStrategyId() != null) {
            KbParseStrategy strategy = parseStrategyMapper.selectById(file.getParseStrategyId());
            if (strategy != null && strategy.getLlmModel() != null && !strategy.getLlmModel().isEmpty()) {
                String llmModel = strategy.getLlmModel();
                ModelRecord modelRecord = modelRecordMapper.selectOne(
                        new LambdaQueryWrapper<ModelRecord>()
                                .eq(ModelRecord::getCode, llmModel)
                                .eq(ModelRecord::getStatus, "online")
                                .last("LIMIT 1"));
                if (modelRecord != null) {
                    log.info("[GraphBuild-LlmConfig] ModelRecord found: model={}, apiUrl={}",
                            llmModel, modelRecord.getApiUrl());
                    return new LlmConfig(llmModel, modelRecord.getApiUrl(), modelRecord.getApiKeyRef());
                }
                log.warn("[GraphBuild-LlmConfig] ModelRecord NOT FOUND for code={} with status=online", llmModel);
            }
        }

        // Fallback: 查 KB 级 graphLlmModel（parse strategy 未配置 LLM 时的兜底）
        log.info("[GraphBuild-LlmConfig] Strategy LLM not available, trying KB-level graphLlmModel for kb={}", kbId);
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb != null && kb.getGraphLlmModel() != null && !kb.getGraphLlmModel().isBlank()) {
            String llmModel = kb.getGraphLlmModel();
            ModelRecord modelRecord = modelRecordMapper.selectOne(
                    new LambdaQueryWrapper<ModelRecord>()
                            .eq(ModelRecord::getCode, llmModel)
                            .eq(ModelRecord::getStatus, "online")
                            .last("LIMIT 1"));
            if (modelRecord != null) {
                log.info("[GraphBuild-LlmConfig] KB-level ModelRecord found: model={}, apiUrl={}",
                        llmModel, modelRecord.getApiUrl());
                return new LlmConfig(llmModel, modelRecord.getApiUrl(), modelRecord.getApiKeyRef());
            }
            log.warn("[GraphBuild-LlmConfig] KB-level ModelRecord NOT FOUND for code={}", llmModel);
        }

        log.warn("[GraphBuild-LlmConfig] Returning empty LLM config -> graph build will skip LLM extraction");
        return new LlmConfig(null, null, null);
    }

    // ==================== 状态与辅助 ====================

    /**
     * 解析关系端点实体 ID（KG-01 + 端点身份统一）：
     * 1. 本单元提取实体映射；2. 图库按规范化名反查（跨单元已存在）；
     * 3. 登记为带 MENTIONS 证据的 UNKNOWN 实体。
     */
    private String resolveRelationEndpointId(String kbId, String rawName, String normalizedName,
                                             Map<String, String> entityIdByNormalizedName,
                                             ExtractionUnitAssembler.ExtractionUnit unit) {
        String id = entityIdByNormalizedName.get(normalizedName);
        if (id != null) return id;
        id = graphStore.findEntityId(kbId, normalizedName);
        if (id != null) return id;
        // 端点实体在任何单元都未被提取：登记为带 MENTIONS 证据的 UNKNOWN 实体，保证边引用有效且有证据
        String placeholderId = GraphIdHashing.entityId(kbId, normalizedName);
        graphStore.createEntity(kbId, placeholderId, rawName, normalizedName, EntityTypeNormalizer.UNKNOWN, null, null);
        attributeEntityMentions(kbId, rawName, normalizedName, placeholderId, unit);
        entityIdByNormalizedName.put(normalizedName, placeholderId);
        log.debug("[GraphBuild] Registered relation endpoint as evidence-backed UNKNOWN entity: {}", rawName);
        return placeholderId;
    }

    /**
     * 更新 chunk 图谱提取状态（kb_chunk.graph_indexed 三态）。
     * extractionResultJson 传 null 时不清空已有值（MyBatis-Plus updateById 忽略 null 字段）。
     */
    private void markChunkGraphState(String chunkId, int state, String extractionResultJson) {
        KbChunk toUpdate = new KbChunk();
        toUpdate.setId(chunkId);
        toUpdate.setGraphIndexed(state);
        toUpdate.setExtractionResult(extractionResultJson);
        chunkMapper.updateById(toUpdate);
    }

    /**
     * 解析实体类型白名单（优先级：KB 级 schema 配置 → yml 全局配置 → 默认集合）。
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

    /** 解析实体类型白名单（yml 配置优先，空则用默认集合；懒加载缓存） */
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
     * 更新图谱构建状态（UPSERT）。
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
     * 更新构建进度：只更新状态/进度，不覆盖 entityCount/relationCount（KB 总量口径，
     * 构建完成时才以 live 计数刷新——避免构建中显示误导性小数字）。
     */
    private void updateGraphProgress(String kbId, int processed, int total, int failedUnits) {
        int progress = total > 0 ? (int) ((double) processed / total * 100) : 0;
        try {
            KbGraphIndex index = graphIndexMapper.selectById(kbId);
            if (index == null) {
                index = new KbGraphIndex();
                index.setKbId(kbId);
                index.setStatus("building");
                index.setBuildProgress(progress);
                index.setEntityCount(0);
                index.setRelationCount(0);
                graphIndexMapper.insert(index);
            } else {
                index.setStatus("building");
                index.setBuildProgress(progress);
                graphIndexMapper.updateById(index);
            }
        } catch (Exception e) {
            log.warn("Failed to update graph progress: {}", e.getMessage());
        }
        log.info("[GraphBuild-Progress] kb={}, progress={}% ({}/{} units) failed={}",
                kbId, progress, processed, total, failedUnits);
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
     * 反序列化持久化的抽取结果（replay / 单元缓存）。
     *
     * @return 解析成功返回规范化结果（含空结果）；JSON 损坏返回 null
     */
    private ExtractionNormalizer.ExtractionResult deserializeExtractionResult(String extractionResultJson) {
        if (extractionResultJson == null || extractionResultJson.isBlank()) return null;
        try {
            ExtractionNormalizer.ExtractionResult result = JSONUtil.toBean(
                    extractionResultJson, ExtractionNormalizer.ExtractionResult.class);
            // 兜底填充 null 集合，防止下游 NPE
            ExtractionNormalizer.normalize(result);
            return result;
        } catch (Exception e) {
            log.warn("[GraphBuild] Failed to deserialize extraction result: {}", e.getMessage());
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
