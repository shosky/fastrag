package com.fastrag.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.hutool.core.util.StrUtil;
import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.ai.layout.LayoutAnalysisService;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.module.graph.service.GraphService;
import com.fastrag.module.knowledge.chunking.ChunkData;
import com.fastrag.module.knowledge.config.StrategyConfigResolver;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.knowledge.model.AiChunkApplyRequest;
import com.fastrag.module.knowledge.model.AiChunkGroup;
import com.fastrag.module.knowledge.model.AiChunkLayoutBlock;
import com.fastrag.module.knowledge.model.AiChunkParagraph;
import com.fastrag.module.knowledge.model.AiChunkResult;
import com.fastrag.module.knowledge.model.ParseStrategyConfig;
import com.fastrag.module.knowledge.parser.DocNode;
import com.fastrag.module.knowledge.parser.DocumentParser;
import com.fastrag.module.knowledge.parser.MediaExtractor;
import com.fastrag.module.knowledge.parser.ParseResult;
import com.fastrag.module.knowledge.service.AiChunkService;
import com.fastrag.module.knowledge.storage.StorageService;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/**
 * AI分片 服务实现（设计文档 docs/design/ai-chunk-preview-semantic.md）。
 *
 * <p>核心流水线：下载原文件 → DocumentParser 解析（含 OCR 兜底）→ 段落对齐模型装配
 * （DocNode 优先，纯文本 PAGE_SPLIT 回退）→ 结构级跨页合并（段落/表格/代码）→
 * LLM 分组（主）/ Embedding 段落相似度（回退）/ 长度累积（兜底）。
 *
 * <p>apply 契约（§7.3）：尊重用户确认的边界，只做「删旧 → 向量化 → 落库 → 写 parsed.md/json」，
 * 不调用任何分片器、不重新解析原文件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChunkServiceImpl implements AiChunkService {

    private final KbFileMapper fileMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final KbChunkMapper chunkMapper;
    private final ModelRecordMapper modelRecordMapper;
    private final DocumentParser documentParser;
    private final MediaExtractor mediaExtractor;
    private final LayoutAnalysisService layoutAnalysisService;
    private final MinioService minioService;
    private final MilvusService milvusService;
    private final GraphService graphService;
    private final StorageService storageService;
    private final LlmService llmService;
    private final EmbeddingService embeddingService;
    private final StrategyConfigResolver configResolver;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** SSE 单独线程执行（解析 + LLM 可能较慢，不能占用请求线程） */
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    /** Embedding 分批大小（与 ChunkingServiceImpl 对齐） */
    private static final int EMBED_BATCH_SIZE = 32;

    /** 页面被视为「有文字层」的最小字符数：低于此值视作扫描/图片页，版面分析走 OCR */
    private static final int TEXT_LAYER_CHARS = 200;

    /** 版面分析缓存版本：分析引擎/格式演进时 +1，旧缓存自动失效重算（当前为 PDFBox 几何 + VLM 分层） */
    private static final int LAYOUT_CACHE_VERSION = 2;

    /** LLM 分组单批段落数（超长文档分批请求） */
    private static final int LLM_GROUP_BATCH = 50;

    /** LLM 分组提示词中单段文本截断长度 */
    private static final int LLM_TEXT_TRUNCATE = 200;

    private static final Pattern PAGE_BREAK_PATTERN = Pattern.compile("\\[PAGE_BREAK:(\\d+)\\]");

    /** 句子终止符（跨页合并判定：页尾未终止 → 续写） */
    private static final String SENTENCE_TERMINATORS = "。！？；.!?;";

    // =========================================================================
    // 公共入口
    // =========================================================================

    @Override
    public AiChunkResult analyze(String kbId, String fileId) {
        return analyze(kbId, fileId, true);
    }

    @Override
    public AiChunkResult analyze(String kbId, String fileId, boolean withChunks) {
        KbFile f = requireFile(kbId, fileId);
        KnowledgeBase kb = kbMapper.selectById(kbId);

        ParseResult parseResult = parseFile(f);
        List<AiChunkParagraph> paragraphs = buildParagraphs(parseResult, f.getExtension());
        mergeCrossPage(paragraphs);
        List<AiChunkParagraph.RectItem> imageBoxes = fillPdfRects(f, paragraphs);
        // 版面分析缓存命中时随预览返回（秒开）；未命中由前端另走 SSE /ai-chunk/layout 按需生成
        List<AiChunkLayoutBlock> layoutBlocks = readLayoutCache(layoutCacheKey(kbId, fileId));

        List<AiChunkGroup> groups = new ArrayList<>();
        String chunkSource = null;
        if (withChunks) {
            AutoChunkOutcome outcome = autoChunk(kb, f, paragraphs);
            backfillChunkIds(paragraphs, outcome.groups());
            groups = outcome.groups();
            chunkSource = outcome.source();
        }

        return AiChunkResult.builder()
                .fileId(fileId)
                .fileName(f.getName())
                .extension(f.getExtension())
                .kbId(kbId)
                .document(AiChunkResult.DocumentInfo.builder()
                        .type(f.getExtension() == null ? "unknown" : f.getExtension().replace(".", ""))
                        .pages(Math.max(parseResult.getPages(), maxPage(paragraphs)))
                        .mergedBlocks((int) paragraphs.stream().filter(AiChunkParagraph::isCrossPage).count())
                        .build())
                .paragraphs(paragraphs)
                .imageBoxes(imageBoxes)
                .layoutBlocks(layoutBlocks)
                .chunks(groups)
                .chunkSource(chunkSource)
                .build();
    }

    @Override
    public SseEmitter stream(String kbId, String fileId) {
        SseEmitter emitter = new SseEmitter(600_000L);
        streamExecutor.execute(() -> {
            try {
                // 阶段进度 + 数据事件（协议见设计文档 §7.2）
                emit(emitter, "progress", stage(8, "parse", null));
                KbFile f = requireFile(kbId, fileId);
                KnowledgeBase kb = kbMapper.selectById(kbId);
                ParseResult parseResult = parseFile(f);

                emit(emitter, "progress", stage(35, "merge", null));
                List<AiChunkParagraph> paragraphs = buildParagraphs(parseResult, f.getExtension());
                mergeCrossPage(paragraphs);
                fillPdfRects(f, paragraphs);

                // 左侧片段：跨页合并块在每页各推一个 fragment（共享同一 paragraphId）
                for (AiChunkParagraph p : paragraphs) {
                    for (Integer page : (p.getPages() != null ? p.getPages() : List.of(p.getPage()))) {
                        ObjectNode frag = objectMapper.createObjectNode();
                        frag.put("page", page);
                        frag.put("paragraphId", p.getId());
                        frag.put("type", p.getType());
                        frag.put("text", truncate(p.getText(), 400));
                        frag.put("pageRange", p.getPageRange());
                        frag.put("crossPage", p.isCrossPage());
                        emit(emitter, "fragment", frag);
                    }
                }

                emit(emitter, "progress", stage(55, "llm-chunk", null));
                AutoChunkOutcome outcome = autoChunk(kb, f, paragraphs);
                backfillChunkIds(paragraphs, outcome.groups());

                // 分片卡逐个推送（右侧逐个出现）
                for (AiChunkGroup g : outcome.groups()) {
                    ObjectNode node = objectMapper.valueToTree(g);
                    emit(emitter, "chunk", node);
                }

                ObjectNode done = objectMapper.createObjectNode();
                done.put("pages", Math.max(parseResult.getPages(), maxPage(paragraphs)));
                done.put("mergedBlocks", (int) paragraphs.stream().filter(AiChunkParagraph::isCrossPage).count());
                done.put("chunks", outcome.groups().size());
                done.put("chunkSource", outcome.source());
                emit(emitter, "done", done);
                emitter.complete();
            } catch (Exception e) {
                log.warn("[AiChunk] stream failed for file {}: {}", fileId, e.getMessage(), e);
                try {
                    ObjectNode err = objectMapper.createObjectNode();
                    err.put("message", e.getMessage() == null ? "AI分片 解析失败" : e.getMessage());
                    emitter.send(SseEmitter.event().name("error").data(err, MediaType.APPLICATION_JSON));
                } catch (Exception ignore) {
                    // emitter 已关闭
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * SSE 版面分析（原件渲染分块画框数据源）：
     * <ol>
     *   <li>MinIO {@code {kbId}/{fileId}/layout.json} 命中 → 一次全量 layout 事件 + done</li>
     *   <li>未命中（仅 PDF）→ 逐页渲染 150 DPI PNG → VLM 版面分析 → layout 事件逐页推送；
     *       并发 5（借鉴入库图片 OCR 模式），单页失败跳过并计数</li>
     *   <li>全部完成 → 有结果则回写缓存 → done</li>
     * </ol>
     * 版面与分片无关、文件不变即有效，每个文件只生成一次。
     */
    @Override
    public SseEmitter streamLayout(String kbId, String fileId) {
        SseEmitter emitter = new SseEmitter(600_000L);
        streamExecutor.execute(() -> {
            try {
                KbFile f = requireFile(kbId, fileId);
                String cacheKey = layoutCacheKey(kbId, fileId);

                List<AiChunkLayoutBlock> cached = readLayoutCache(cacheKey);
                if (cached != null) {
                    emitSync(emitter, "layout", objectMapper.valueToTree(cached));
                    ObjectNode done0 = objectMapper.createObjectNode();
                    done0.put("cached", true);
                    done0.put("blocks", cached.size());
                    emitSync(emitter, "done", done0);
                    emitter.complete();
                    return;
                }
                if (f.getExtension() == null || !f.getExtension().toLowerCase().replace(".", "").equals("pdf")) {
                    ObjectNode unsupported = objectMapper.createObjectNode();
                    unsupported.put("unsupported", true);
                    emitSync(emitter, "done", unsupported);
                    emitter.complete();
                    return;
                }

                // 分层版面分析（正规 PDF 走 PDFBox 几何，扫描页才 OCR）：
                // ① 有文字层的页 → PdfLayoutAnalyzer 几何聚类（像素精确、零 VLM 调用）
                // ② 无文字层的页（扫描/图片页）→ 渲染 PNG 走 VLM OCR 版面分析
                byte[] pdfBytes;
                try (InputStream in = minioService.download(f.getObjectKey())) {
                    pdfBytes = in.readAllBytes();
                }
                final int total;
                final List<MediaExtractor.PdfImageBox> allImgs;
                try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
                    total = doc.getNumberOfPages();
                    // 图片盒全量提取一次（pt、顶左），几何页按 page 过滤复用
                    allImgs = mediaExtractor.extractContentImageBoxes(doc);
                }
                // PDFBox 的 PDDocument 非线程安全：几何遍历单线程、VLM 渲染各线程独立 load 副本
                List<AiChunkLayoutBlock> all = Collections.synchronizedList(new ArrayList<>());
                final int[] okPages = {0};
                List<Integer> vlmPages = new ArrayList<>();
                try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
                    for (int p = 0; p < total; p++) {
                        List<PdfLayoutAnalyzer.Line> plines = extractPageLines(doc, p);
                        int chars = 0;
                        for (PdfLayoutAnalyzer.Line l : plines) {
                            chars += l.text().length();
                        }
                        if (chars < TEXT_LAYER_CHARS) {
                            vlmPages.add(p); // 文字量过少 → 视作扫描/图片页，走 OCR
                            continue;
                        }
                        try {
                            org.apache.pdfbox.pdmodel.common.PDRectangle mb = doc.getPage(p).getMediaBox();
                            List<float[]> pageImgs = new ArrayList<>();
                            for (MediaExtractor.PdfImageBox b : allImgs) {
                                if (b.page() == p + 1) {
                                    pageImgs.add(new float[]{b.x(), b.y(), b.width(), b.height()});
                                }
                            }
                            List<AiChunkLayoutBlock> blocks =
                                    PdfLayoutAnalyzer.analyze(p + 1, mb.getWidth(), mb.getHeight(), plines, pageImgs);
                            all.addAll(blocks);
                            emitSync(emitter, "layout", objectMapper.valueToTree(blocks));
                            okPages[0]++;
                            emitSync(emitter, "progress",
                                    stage(okPages[0] * 100 / total, "layout", okPages[0] + "/" + total));
                        } catch (Exception e) {
                            log.warn("[AiChunk] layout(geo) page {} failed: {}", p + 1, e.getMessage());
                        }
                    }
                }
                // 无文字层的页：并发调 VLM（每线程独立 doc，避免 PDFBox 非线程安全问题）
                if (!vlmPages.isEmpty()) {
                    Semaphore semaphore = new Semaphore(5);
                    ExecutorService pool = Executors.newFixedThreadPool(5);
                    List<Future<?>> futures = new ArrayList<>();
                    try {
                        for (int idx : vlmPages) {
                            final int pageIdx = idx;
                            futures.add(pool.submit(() -> {
                                try {
                                    semaphore.acquire();
                                    try (PDDocument pageDoc = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
                                        BufferedImage img = new PDFRenderer(pageDoc).renderImageWithDPI(pageIdx, 150);
                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        ImageIO.write(img, "png", baos);
                                        List<AiChunkLayoutBlock> pageBlocks = layoutAnalysisService
                                                .analyzePage(baos.toByteArray(), null)
                                                .stream()
                                                .map(b -> AiChunkLayoutBlock.builder()
                                                        .page(pageIdx + 1).type(b.type())
                                                        .x(b.x()).y(b.y()).width(b.width()).height(b.height())
                                                        .text(b.text()).build())
                                                .collect(Collectors.toList());
                                        all.addAll(pageBlocks);
                                        emitSync(emitter, "layout", objectMapper.valueToTree(pageBlocks));
                                        okPages[0]++;
                                        emitSync(emitter, "progress",
                                                stage(okPages[0] * 100 / total, "layout", okPages[0] + "/" + total));
                                    } finally {
                                        semaphore.release();
                                    }
                                } catch (Exception e) {
                                    log.warn("[AiChunk] layout(vlm) page {} failed: {}", pageIdx + 1, e.getMessage());
                                }
                            }));
                        }
                        for (Future<?> fu : futures) {
                            try {
                                fu.get();
                            } catch (Exception ignore) {
                                // 单页失败已计数跳过
                            }
                        }
                    } finally {
                        pool.shutdown();
                    }
                }
                // 只有全部页成功才写缓存；部分页失败则不落盘，下次打开可重试整份
                if (!all.isEmpty() && okPages[0] >= total) {
                    Map<String, Object> payload = Map.of("v", LAYOUT_CACHE_VERSION, "blocks", all);
                    minioService.upload(cacheKey,
                            new ByteArrayInputStream(objectMapper.writeValueAsBytes(payload)), "application/json");
                }
                ObjectNode done = objectMapper.createObjectNode();
                done.put("pages", total);
                done.put("blocks", all.size());
                emitSync(emitter, "done", done);
                emitter.complete();
            } catch (Exception e) {
                log.warn("[AiChunk] streamLayout failed for file {}: {}", fileId, e.getMessage(), e);
                try {
                    ObjectNode err = objectMapper.createObjectNode();
                    err.put("message", e.getMessage() == null ? "版面分析失败" : e.getMessage());
                    emitter.send(SseEmitter.event().name("error").data(err, MediaType.APPLICATION_JSON));
                } catch (Exception ignore) {
                    // emitter 已关闭
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /** SSE 并发发送保护：逐页并行分析时 layout 事件可能来自不同工作线程 */
    private void emitSync(SseEmitter emitter, String event, Object data) throws Exception {
        synchronized (emitter) {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        }
    }

    /** 版面分析缓存 key（遵循 {kbId}/{fileId}/xxx 派生产物约定） */
    private String layoutCacheKey(String kbId, String fileId) {
        return kbId + "/" + fileId + "/layout.json";
    }

    /** 读版面分析缓存；版本不符（引擎演进）、不存在或损坏返回 null */
    private List<AiChunkLayoutBlock> readLayoutCache(String cacheKey) {
        try (InputStream in = minioService.download(cacheKey)) {
            JsonNode root = objectMapper.readTree(in.readAllBytes());
            if (root.path("v").asInt() != LAYOUT_CACHE_VERSION || !root.path("blocks").isArray()) {
                log.info("[AiChunk] layout cache version mismatch/invalid, ignoring: {}", cacheKey);
                return null;
            }
            List<AiChunkLayoutBlock> list = new ArrayList<>();
            for (JsonNode n : root.path("blocks")) {
                list.add(objectMapper.treeToValue(n, AiChunkLayoutBlock.class));
            }
            return list;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int apply(String kbId, String fileId, AiChunkApplyRequest request) {
        KbFile f = requireFile(kbId, fileId);
        if (f.getDeletedAt() != null) {
            throw BusinessException.badRequest("文件已在回收站中，请先恢复后再应用 AI分片");
        }
        if ("processing".equals(f.getStatus())) {
            throw BusinessException.badRequest("文件正在处理中，请等待完成后再应用 AI分片");
        }
        if ("qa".equals(f.getProcessingMode())) {
            throw BusinessException.badRequest("QA 模式文件不支持 AI分片");
        }
        if (request == null || request.getChunks() == null || request.getChunks().isEmpty()) {
            throw BusinessException.badRequest("分片列表为空：请先手动生成分片或一键自动分片");
        }
        for (AiChunkApplyRequest.Item item : request.getChunks()) {
            if (item.getText() == null || item.getText().isBlank()) {
                throw BusinessException.badRequest("存在内容为空的分片，请编辑后重试");
            }
        }

        // 1. 清理旧分片与向量（与 reChunkFile 相同模式，但之后不走 process 重解析）。
        //    保留 manual 手动分片：用户手工分片不随 AI 分片应用清空（origin 标记）
        List<KbChunk> autoChunks = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getKbId, kbId)
                .eq(KbChunk::getFileId, fileId)
                .and(w -> w.isNull(KbChunk::getOrigin).or().ne(KbChunk::getOrigin, "manual")));
        List<String> autoEmbeddingIds = autoChunks.stream()
                .map(KbChunk::getEmbeddingId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        int chunkDeleted = chunkMapper.delete(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getKbId, kbId)
                .eq(KbChunk::getFileId, fileId)
                .and(w -> w.isNull(KbChunk::getOrigin).or().ne(KbChunk::getOrigin, "manual")));
        log.info("[AiChunk] apply: deleted {} auto chunks (manual kept) for file {}", chunkDeleted, fileId);

        String collection = "kb_" + kbId.replace("-", "_");
        if (!autoEmbeddingIds.isEmpty()) {
            try {
                milvusService.deleteByIds(collection, autoEmbeddingIds);
            } catch (Exception e) {
                log.warn("[AiChunk] apply: Milvus cleanup failed for {}: {}", fileId, e.getMessage());
            }
        }
        try {
            graphService.deleteFileGraph(kbId, fileId);
        } catch (Exception e) {
            log.warn("[AiChunk] apply: graph cleanup failed for {}: {}", fileId, e.getMessage());
        }
        // deleteFileGraph 已清空文件图谱，重置保留 manual 分片 graphIndexed=0 使其随增量构建重建
        try {
            chunkMapper.update(null, new LambdaUpdateWrapper<KbChunk>()
                    .eq(KbChunk::getKbId, kbId)
                    .eq(KbChunk::getFileId, fileId)
                    .eq(KbChunk::getOrigin, "manual")
                    .set(KbChunk::getGraphIndexed, 0)
                    .set(KbChunk::getExtractionResult, null));
        } catch (Exception e) {
            log.warn("[AiChunk] apply: failed to reset manual graphIndexed for {}: {}", fileId, e.getMessage());
        }

        // 2. 尊重边界：请求里的分片即最终分片，只做向量化与落库
        List<ChunkData> chunks = new ArrayList<>();
        for (int i = 0; i < request.getChunks().size(); i++) {
            AiChunkApplyRequest.Item item = request.getChunks().get(i);
            chunks.add(ChunkData.builder()
                    .id(fileId + "_chunk_" + i)
                    .index(i)
                    .content(item.getText())
                    .pageRange(item.getPageRange())
                    .chunkType("text")
                    .build());
        }
        storageService.storeChunks(kbId, fileId, chunks);

        // 3. 写 parsed.md（分片文本按序序列化）+ parsed.json（分片清单与段落映射）
        writeParsedArtifacts(kbId, fileId, f, request);

        // 4. 更新文件状态（chunkCount = 本次落库 auto + 保留的 manual）
        f.setStatus("completed");
        f.setProgress(100);
        f.setStage("");
        long manualCount = chunkMapper.selectCount(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getKbId, kbId)
                .eq(KbChunk::getFileId, fileId)
                .eq(KbChunk::getOrigin, "manual")
                .ne(KbChunk::getChunkType, "parent"));
        f.setChunkCount((int) (chunks.size() + manualCount));
        f.setUpdatedAt(LocalDateTime.now());
        fileMapper.updateById(f);

        log.info("[AiChunk] applied {} chunks for file {} in kb {}", chunks.size(), fileId, kbId);
        return chunks.size();
    }

    // =========================================================================
    // 解析与段落对齐模型
    // =========================================================================

    private KbFile requireFile(String kbId, String fileId) {
        KbFile f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getId, fileId)
                .eq(KbFile::getKbId, kbId));
        if (f == null) throw BusinessException.notFound("文件不存在");
        return f;
    }

    private ParseResult parseFile(KbFile f) {
        try (InputStream in = minioService.download(f.getObjectKey())) {
            byte[] bytes = in.readAllBytes();
            // 与 previewChunks 一致：传 null 策略避免触发 enhanceWithLlm（预览路径）
            return documentParser.parse(new ByteArrayInputStream(bytes), f.getExtension(), null);
        } catch (Exception e) {
            log.warn("[AiChunk] parse failed for {}: {}", f.getId(), e.getMessage());
            throw BusinessException.badRequest("文档解析失败：" + e.getMessage());
        }
    }

    /** DocNode 优先装配段落对齐模型；无节点（纯文本路径）时按 PAGE_BREAK + 空行回退 */
    private List<AiChunkParagraph> buildParagraphs(ParseResult r, String extension) {
        List<AiChunkParagraph> paragraphs = new ArrayList<>();
        if (r.getNodes() != null && !r.getNodes().isEmpty()) {
            Deque<String[]> headingStack = new ArrayDeque<>(); // [level, title]
            int order = 0;
            for (DocNode node : r.getNodes()) {
                if (node.getType() == DocNode.NodeType.PAGE_BREAK) continue;
                int page = node.getPageNumber() != null && node.getPageNumber() > 0 ? node.getPageNumber() : 1;
                switch (node.getType()) {
                    case HEADING -> {
                        // 维护标题栈（弹出层级 >= 当前的）
                        while (!headingStack.isEmpty()
                                && Integer.parseInt(headingStack.peek()[0]) >= nvl(node.getLevel(), 1)) {
                            headingStack.pop();
                        }
                        headingStack.push(new String[]{String.valueOf(nvl(node.getLevel(), 1)),
                                node.getTitle() == null ? "" : node.getTitle()});
                        paragraphs.add(paragraph(++order, "heading",
                                node.getTitle() == null ? "" : node.getTitle(), page, headingPath(headingStack)));
                    }
                    case TABLE -> paragraphs.add(paragraph(++order, "table", tableText(node), page,
                            headingPath(headingStack)));
                    case CODE_BLOCK -> paragraphs.add(paragraph(++order, "code",
                            node.getContent() == null ? "" : node.getContent(), page, headingPath(headingStack)));
                    case LIST, LIST_ITEM -> paragraphs.add(paragraph(++order, "list",
                            node.getContent() == null ? "" : node.getContent(), page, headingPath(headingStack)));
                    case IMAGE -> {
                        String imgText = node.getImageCaption() != null ? node.getImageCaption()
                                : (node.getImageKey() == null ? "图片" : "[图片] " + node.getImageKey());
                        AiChunkParagraph img = paragraph(++order, "image", imgText, page, headingPath(headingStack));
                        img.setImageKey(node.getImageKey());
                        paragraphs.add(img);
                    }
                    case DISPLAY_MATH, INLINE_MATH -> paragraphs.add(paragraph(++order, "paragraph",
                            node.getLatex() != null ? node.getLatex()
                                    : (node.getContent() == null ? "" : node.getContent()),
                            page, headingPath(headingStack)));
                    default -> {
                        if (node.getContent() != null && !node.getContent().isBlank()) {
                            paragraphs.add(paragraph(++order, "paragraph", node.getContent(), page,
                                    headingPath(headingStack)));
                        }
                    }
                }
            }
        } else {
            // 纯文本回退：按 PAGE_BREAK 分页，页内按空行分段
            String text = r.getText() == null ? "" : r.getText();
            Matcher m = PAGE_BREAK_PATTERN.matcher(text);
            List<int[]> breaks = new ArrayList<>(); // [start, pageIndex]
            int page = 1;
            int cursor = 0;
            int order = 0;
            List<String> parts = new ArrayList<>();
            List<Integer> partPages = new ArrayList<>();
            while (m.find()) {
                parts.add(text.substring(cursor, m.start()));
                partPages.add(page);
                page = Integer.parseInt(m.group(1));
                cursor = m.end();
            }
            parts.add(text.substring(cursor));
            partPages.add(page);
            for (int i = 0; i < parts.size(); i++) {
                int p = partPages.get(i);
                for (String block : parts.get(i).split("\\n\\s*\\n")) {
                    String trimmed = block.trim();
                    if (trimmed.isEmpty()) continue;
                    String type = trimmed.startsWith("#") ? "heading" : "paragraph";
                    paragraphs.add(paragraph(++order, type, trimmed, p, ""));
                }
            }
        }
        if (paragraphs.isEmpty()) {
            throw BusinessException.badRequest("文档解析结果为空，无法 AI分片");
        }
        return paragraphs;
    }

    private AiChunkParagraph paragraph(int order, String type, String text, int page, String headingPath) {
        return AiChunkParagraph.builder()
                .id("p_" + String.format("%04d", order))
                .order(order)
                .type(type)
                .text(text)
                .page(page)
                .pages(new ArrayList<>(List.of(page)))
                .pageRange(String.valueOf(page))
                .headingPath(headingPath)
                .build();
    }

    private String headingPath(Deque<String[]> stack) {
        if (stack.isEmpty()) return "";
        return stack.stream()
                .sorted(Comparator.comparingInt(a -> Integer.parseInt(a[0])))
                .map(a -> a[1])
                .filter(t -> !t.isBlank())
                .collect(Collectors.joining(" > "));
    }

    private String tableText(DocNode node) {
        StringBuilder sb = new StringBuilder();
        if (node.getHeaders() != null && !node.getHeaders().isEmpty()) {
            sb.append(String.join("\t", node.getHeaders())).append('\n');
        }
        if (node.getRows() != null) {
            for (List<String> row : node.getRows()) {
                sb.append(String.join("\t", row)).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    // =========================================================================
    // 结构级跨页合并（设计文档 §5）
    // =========================================================================

    /**
     * 就地合并跨页割裂块。判定信号：
     * <ul>
     *   <li>段落：前段页尾句末无终止符 && 后段在更大页 && 均为 paragraph → 拼接</li>
     *   <li>表格：相邻 TABLE 且后表页 > 前表页 且 表头（首行）一致 → 行合并</li>
     *   <li>代码：相邻 CODE_BLOCK 且后块页 > 前块页 → 行拼接</li>
     * </ul>
     * 保守策略：标题/新小节起点不回并。
     */
    private void mergeCrossPage(List<AiChunkParagraph> paragraphs) {
        List<AiChunkParagraph> merged = new ArrayList<>();
        for (AiChunkParagraph cur : paragraphs) {
            AiChunkParagraph prev = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            boolean did = false;
            if (prev != null && cur.getPage() > lastPage(prev)) {
                did = switch (prev.getType()) {
                    case "paragraph" -> tryMergeText(prev, cur);
                    case "table" -> tryMergeTable(prev, cur);
                    case "code" -> tryMergeCode(prev, cur);
                    default -> false;
                };
            }
            if (!did) merged.add(cur);
        }
        // 重排 order / id 保持稳定
        for (int i = 0; i < merged.size(); i++) {
            AiChunkParagraph p = merged.get(i);
            p.setOrder(i + 1);
            p.setId("p_" + String.format("%04d", i + 1));
            List<Integer> pages = p.getPages().stream().distinct().sorted().toList();
            p.setPages(new ArrayList<>(pages));
            p.setPage(pages.get(0));
            p.setPageRange(pages.size() > 1
                    ? pages.get(0) + "-" + pages.get(pages.size() - 1)
                    : String.valueOf(pages.get(0)));
            p.setCrossPage(pages.size() > 1);
        }
        paragraphs.clear();
        paragraphs.addAll(merged);
    }

    private boolean tryMergeText(AiChunkParagraph prev, AiChunkParagraph cur) {
        if (!"paragraph".equals(cur.getType())) return false;
        String prevText = prev.getText();
        if (prevText.isEmpty()) return false;
        char last = prevText.charAt(prevText.length() - 1);
        if (SENTENCE_TERMINATORS.indexOf(last) >= 0) return false; // 句已终止，不并
        // 下一页首行不是标题/列表形态才视为续写
        String curText = cur.getText();
        if (curText.startsWith("#") || curText.startsWith("-") || curText.startsWith("•")) return false;
        prev.setText(prevText + (last == ' ' ? "" : "") + curText);
        absorb(prev, cur);
        return true;
    }

    private boolean tryMergeTable(AiChunkParagraph prev, AiChunkParagraph cur) {
        if (!"table".equals(cur.getType())) return false;
        String prevHeader = firstLine(prev.getText());
        String curHeader = firstLine(cur.getText());
        if (prevHeader == null || curHeader == null || !prevHeader.equals(curHeader)) return false;
        // 丢弃续表重复表头行后拼接
        String curBody = cur.getText().substring(cur.getText().indexOf('\n') + 1);
        prev.setText(prev.getText() + "\n" + curBody);
        absorb(prev, cur);
        return true;
    }

    private boolean tryMergeCode(AiChunkParagraph prev, AiChunkParagraph cur) {
        if (!"code".equals(cur.getType())) return false;
        prev.setText(prev.getText() + "\n" + cur.getText());
        absorb(prev, cur);
        return true;
    }

    private void absorb(AiChunkParagraph host, AiChunkParagraph absorbed) {
        host.getPages().addAll(absorbed.getPages());
    }

    private int lastPage(AiChunkParagraph p) {
        List<Integer> pages = p.getPages();
        return pages.isEmpty() ? p.getPage() : Collections.max(pages);
    }

    private String firstLine(String text) {
        int idx = text.indexOf('\n');
        return idx < 0 ? text : text.substring(0, idx);
    }

    // =========================================================================
    // 自动分片：LLM 主路径 / Embedding 回退 / 长度兜底（设计文档 §6）
    // =========================================================================

    private record AutoChunkOutcome(List<AiChunkGroup> groups, String source) {
    }

    private AutoChunkOutcome autoChunk(KnowledgeBase kb, KbFile f, List<AiChunkParagraph> paragraphs) {
        ParseStrategyConfig.ChunkConfig cfg = configResolver.resolve(f.getParseStrategyId()).getChunk();
        int chunkLength = Math.max(1, cfg.getChunkLength());
        double threshold = Math.max(0, Math.min(100, cfg.getSemanticThreshold())) / 100.0;

        // 1) LLM 主路径
        String llmModel = kb == null ? null : kb.getGraphLlmModel();
        if (llmModel != null && !llmModel.isBlank()) {
            try {
                List<AiChunkGroup> groups = llmGroup(llmModel, paragraphs, chunkLength);
                if (groups != null) return new AutoChunkOutcome(groups, "llm");
            } catch (Exception e) {
                log.warn("[AiChunk] LLM grouping failed, falling back to embedding: {}", e.getMessage());
            }
        }

        // 2) Embedding 段落相似度回退
        String embModel = kb == null ? null : kb.getEmbeddingModel();
        if (embModel != null && !embModel.isBlank()) {
            try {
                List<AiChunkGroup> groups = embeddingGroup(embModel, paragraphs, threshold, chunkLength);
                return new AutoChunkOutcome(groups, "embedding");
            } catch (Exception e) {
                log.warn("[AiChunk] embedding grouping failed, falling back to rule: {}", e.getMessage());
            }
        }

        // 3) 长度累积兜底
        return new AutoChunkOutcome(ruleGroup(paragraphs, chunkLength), "rule");
    }

    /** LLM 分组：段落清单 → 分组 JSON；输出无效（覆盖不全/乱序）时抛异常走回退 */
    private List<AiChunkGroup> llmGroup(String model, List<AiChunkParagraph> paragraphs, int chunkLength) {
        ModelRecord rec = resolveRecord(model);
        String apiUrl = rec != null ? rec.getApiUrl() : null;
        String apiKey = rec != null ? rec.getApiKeyRef() : null;

        List<List<Integer>> groupedIndexes = new ArrayList<>();
        int consumed = 0;
        while (consumed < paragraphs.size()) {
            int end = Math.min(consumed + LLM_GROUP_BATCH, paragraphs.size());
            List<AiChunkParagraph> batch = paragraphs.subList(consumed, end);
            StringBuilder list = new StringBuilder();
            for (int i = 0; i < batch.size(); i++) {
                list.append(consumed + i + 1).append(". [").append(batch.get(i).getType()).append("] ")
                        .append(truncate(batch.get(i).getText(), LLM_TEXT_TRUNCATE).replace('\n', ' ')).append('\n');
            }
            String prompt = """
                    你是文档语义分片器。下面是文档的段落清单（编号从 %d 到 %d，按原文顺序）。
                    请把段落划分为语义完整的分片，规则：
                    1. 每片 1~8 个段落，目标总长度 300~%d 字符；
                    2. 表格、代码块不可拆分，单独成片或与紧密相关的段落同片；
                    3. 标题与其后首个正文段落应同片；
                    4. 输出仅为 JSON：数组的数组，元素为段落编号，按升序覆盖全部编号且不重复。
                    示例：[[1,2],[3],[4,5,6]]

                    段落清单：
                    %s""".formatted(consumed + 1, end, chunkLength, list);

            String response = llmService.chatWithTimeout(model, prompt, apiUrl, apiKey, false, 60);
            List<List<Integer>> parsed = parseGroups(response, consumed + 1, end);
            if (parsed == null) throw new IllegalStateException("LLM 分组输出无效");
            groupedIndexes.addAll(parsed);
            consumed = end;
        }

        List<AiChunkGroup> groups = new ArrayList<>();
        int idx = 0;
        for (List<Integer> indexes : groupedIndexes) {
            List<AiChunkParagraph> members = indexes.stream().map(i -> paragraphs.get(i - 1)).toList();
            groups.add(group(idx++, "auto", members));
        }
        return groups;
    }

    private List<List<Integer>> parseGroups(String response, int from, int to) {
        try {
            String json = stripFences(response);
            int s = json.indexOf('[');
            int e = json.lastIndexOf(']');
            if (s < 0 || e <= s) return null;
            JsonNode root = objectMapper.readTree(json.substring(s, e + 1));
            if (!root.isArray()) return null;
            Set<Integer> seen = new TreeSet<>();
            List<List<Integer>> out = new ArrayList<>();
            for (JsonNode arr : root) {
                if (!arr.isArray()) return null;
                List<Integer> group = new ArrayList<>();
                for (JsonNode n : arr) {
                    int v = n.asInt(-1);
                    if (v < from || v > to || !seen.add(v)) return null;
                    group.add(v);
                }
                if (group.isEmpty()) return null;
                Collections.sort(group);
                out.add(group);
            }
            if (seen.size() != to - from + 1) return null; // 必须全覆盖
            // 整体升序校验
            Integer prev = null;
            for (List<Integer> g : out) {
                if (prev != null && g.get(0) <= prev) return null;
                prev = g.get(g.size() - 1);
            }
            return out;
        } catch (Exception ex) {
            return null;
        }
    }

    private String stripFences(String s) {
        String t = s == null ? "" : s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            int fence = t.lastIndexOf("```");
            if (fence >= 0) t = t.substring(0, fence);
        }
        return t;
    }

    /** Embedding 段落相似度分组：相邻段落余弦 < threshold 或超长 → 断点 */
    private List<AiChunkGroup> embeddingGroup(String model, List<AiChunkParagraph> paragraphs,
                                              double threshold, int chunkLength) {
        ModelRecord rec = resolveRecord(model);
        String apiUrl = rec != null ? rec.getApiUrl() : null;
        String apiKey = rec != null ? rec.getApiKeyRef() : null;

        List<String> inputs = paragraphs.stream()
                .map(p -> truncate(p.getText(), 500))
                .toList();
        List<List<Float>> vectors = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i += EMBED_BATCH_SIZE) {
            vectors.addAll(embeddingService.embed(model,
                    inputs.subList(i, Math.min(i + EMBED_BATCH_SIZE, inputs.size())), apiUrl, apiKey));
        }
        if (vectors.size() != paragraphs.size()) {
            throw new IllegalStateException("embedding 数量不匹配: " + vectors.size() + "/" + paragraphs.size());
        }

        List<AiChunkGroup> groups = new ArrayList<>();
        List<AiChunkParagraph> current = new ArrayList<>();
        int currentLen = 0;
        for (int i = 0; i < paragraphs.size(); i++) {
            AiChunkParagraph p = paragraphs.get(i);
            boolean boundary = false;
            if (!current.isEmpty()) {
                double sim = cosine(vectors.get(i - 1), vectors.get(i));
                if (sim < threshold) boundary = true;              // 语义断点
                if (currentLen + p.getText().length() > chunkLength) boundary = true; // 超长
            }
            if (boundary) {
                groups.add(group(groups.size(), "auto", current));
                current = new ArrayList<>();
                currentLen = 0;
            }
            current.add(p);
            currentLen += p.getText().length();
        }
        if (!current.isEmpty()) groups.add(group(groups.size(), "auto", current));
        return groups;
    }

    /** 长度累积兜底：段落为最小单位，累计至 chunkLength 断片 */
    private List<AiChunkGroup> ruleGroup(List<AiChunkParagraph> paragraphs, int chunkLength) {
        List<AiChunkGroup> groups = new ArrayList<>();
        List<AiChunkParagraph> current = new ArrayList<>();
        int currentLen = 0;
        for (AiChunkParagraph p : paragraphs) {
            if (!current.isEmpty() && currentLen + p.getText().length() > chunkLength) {
                groups.add(group(groups.size(), "auto", current));
                current = new ArrayList<>();
                currentLen = 0;
            }
            current.add(p);
            currentLen += p.getText().length();
        }
        if (!current.isEmpty()) groups.add(group(groups.size(), "auto", current));
        return groups;
    }

    private AiChunkGroup group(int index, String source, List<AiChunkParagraph> members) {
        TreeSet<Integer> pages = new TreeSet<>();
        for (AiChunkParagraph m : members) pages.addAll(m.getPages());
        return AiChunkGroup.builder()
                .id("chunk_" + index)
                .index(index)
                .source(source)
                .paragraphIds(members.stream().map(AiChunkParagraph::getId).toList())
                .text(members.stream().map(AiChunkParagraph::getText).collect(Collectors.joining("\n\n")))
                .pageRange(pages.size() > 1
                        ? pages.first() + "-" + pages.last()
                        : String.valueOf(pages.first() == null ? 1 : pages.first()))
                .build();
    }

    private void backfillChunkIds(List<AiChunkParagraph> paragraphs, List<AiChunkGroup> groups) {
        Map<String, String> pid2chunk = new HashMap<>();
        for (AiChunkGroup g : groups) {
            for (String pid : g.getParagraphIds()) pid2chunk.put(pid, g.getId());
        }
        for (AiChunkParagraph p : paragraphs) p.setChunkId(pid2chunk.get(p.getId()));
    }

    // =========================================================================
    // 工具
    // =========================================================================

    private ModelRecord resolveRecord(String model) {
        try {
            return modelRecordMapper.selectOne(new LambdaQueryWrapper<ModelRecord>()
                    .eq(ModelRecord::getCode, model)
                    .eq(ModelRecord::getStatus, "online")
                    .last("LIMIT 1"));
        } catch (Exception e) {
            log.debug("[AiChunk] resolve model '{}' failed: {}", model, e.getMessage());
            return null;
        }
    }

    private double cosine(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.size() != b.size()) return 0.0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            na += a.get(i) * a.get(i);
            nb += b.get(i) * b.get(i);
        }
        if (na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private int maxPage(List<AiChunkParagraph> paragraphs) {
        return paragraphs.stream().flatMap(p -> p.getPages().stream()).max(Integer::compareTo).orElse(1);
    }

    // =========================================================================
    // PDF 文字块坐标（rects）— 供前端 pdf.js 在原文件上画分片边界（设计文档 §8.2）
    // =========================================================================

    /**
     * 为 PDF 段落回填 {@code rects}（每页一个文字块盒，<b>归一化 0~1 顶左原点</b>，与版面分析块坐标系统一）。
     * <p>行盒提取后按<b>内容锚定</b>分配给段落（段落行文本与页面行逐行模糊对齐，两指针推进），
     * 相比旧的「按文本行数顺序猜测」可自愈漂移，且跨页合并段落在每页只消费本页部分，
     * 不会把两页后续段落整体顶错位。纯图片/扫描页无文字层 → 无盒。</p>
     *
     * @return PDF 内容图片的真实渲染位置盒（同归一化坐标系）。PDF 解析不产 IMAGE 段落，
     *         图片框走此旁路通道返回给前端原件可视化，不参与段落/分片对齐模型。
     */
    private List<AiChunkParagraph.RectItem> fillPdfRects(KbFile f, List<AiChunkParagraph> paragraphs) {
        if (f.getExtension() == null || !f.getExtension().toLowerCase().replace(".", "").equals("pdf")) {
            return List.of();
        }
        try (InputStream in = minioService.download(f.getObjectKey())) {
            byte[] bytes = in.readAllBytes();
            try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(bytes)) {
                Map<Integer, List<PdfLayoutAnalyzer.Line>> pageLines = new HashMap<>();
                for (int p = 0; p < doc.getNumberOfPages(); p++) {
                    pageLines.put(p + 1, extractPageLines(doc, p));
                }
                // 按页聚合段落（保持 order 序；跨页段落在每个出现页各对齐一次）
                Map<Integer, List<AiChunkParagraph>> pageParas = new LinkedHashMap<>();
                for (AiChunkParagraph para : paragraphs) {
                    List<Integer> pages = (para.getPages() != null && !para.getPages().isEmpty())
                            ? para.getPages() : List.of(para.getPage());
                    for (Integer page : pages) {
                        pageParas.computeIfAbsent(page, k -> new ArrayList<>()).add(para);
                    }
                }
                for (Map.Entry<Integer, List<AiChunkParagraph>> e : pageParas.entrySet()) {
                    List<PdfLayoutAnalyzer.Line> lines = pageLines.getOrDefault(e.getKey(), List.of());
                    if (lines.isEmpty()) continue;
                    org.apache.pdfbox.pdmodel.common.PDRectangle mb = doc.getPage(e.getKey() - 1).getMediaBox();
                    float pageW = mb.getWidth();
                    float pageH = mb.getHeight();
                    if (pageW <= 0 || pageH <= 0) continue;
                    List<AiChunkParagraph> paras = e.getValue();
                    List<String> lineTexts = lines.stream().map(l -> l.text()).collect(Collectors.toList());
                    List<String> paraTexts = paras.stream().map(AiChunkParagraph::getText).collect(Collectors.toList());
                    int[][] ranges = assignLineRanges(paraTexts, lineTexts);
                    for (int i = 0; i < paras.size(); i++) {
                        int start = ranges[i][0], end = ranges[i][1];
                        if (start < 0) continue; // 本页未锚定（继承标题/OCR 差异）→ 无盒且不占行
                        float x1 = Float.MAX_VALUE, y1 = Float.MAX_VALUE, x2 = -1f, y2 = -1f;
                        for (int k = start; k < end; k++) {
                            float[] r = lines.get(k).rect();
                            if (r[0] < x1) x1 = r[0];
                            if (r[1] < y1) y1 = r[1];
                            if (r[0] + r[2] > x2) x2 = r[0] + r[2];
                            if (r[1] + r[3] > y2) y2 = r[1] + r[3];
                        }
                        if (x2 < x1 || y2 < y1) continue;
                        // 行盒只覆盖基线+字高，上下各留 ~15% 字高余量更贴合视觉块
                        float pad = (y2 - y1) * 0.15f;
                        AiChunkParagraph para = paras.get(i);
                        if (para.getRects() == null) para.setRects(new ArrayList<>());
                        para.getRects().add(AiChunkParagraph.RectItem.builder()
                                .page(e.getKey())
                                .x(x1 / pageW)
                                .y(Math.max(0, y1 - pad) / pageH)
                                .width((x2 - x1) / pageW)
                                .height((y2 - y1 + pad * 2) / pageH)
                                .build());
                    }
                }
                return mediaExtractor.extractContentImageBoxes(doc).stream()
                        .map(b -> {
                            org.apache.pdfbox.pdmodel.common.PDRectangle mb = doc.getPage(b.page() - 1).getMediaBox();
                            return AiChunkParagraph.RectItem.builder()
                                    .page(b.page())
                                    .x(b.x() / mb.getWidth())
                                    .y(b.y() / mb.getHeight())
                                    .width(b.width() / mb.getWidth())
                                    .height(b.height() / mb.getHeight())
                                    .build();
                        })
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("[AiChunk] fillPdfRects failed for {}: {}", f.getId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * 内容锚定行分配：把页面行序列按段落顺序对齐（两指针 + 模糊行匹配）。
     * <p>对每个段落：从当前行指针起向后找第一个能与其行序列匹配的行作为锚点
     * （跨页续写段允许从行序列中段开始），随后逐行推进直到不匹配或行序耗尽——
     * 段落在本页的部分即消费的行区间。锚不到的段落（继承标题、OCR 文本差异）返回 [-1,-1)，
     * 不移动指针、不挤占后续段落。</p>
     *
     * @return ranges[i] = [start, end)（第 i 个段落消费的行区间；start=-1 表示未锚定）
     */
    static int[][] assignLineRanges(List<String> paragraphTexts, List<String> lineTexts) {
        int n = paragraphTexts.size();
        int[][] ranges = new int[n][2];
        for (int[] r : ranges) {
            r[0] = -1;
            r[1] = -1;
        }
        // 行文本归一化防御：不依赖调用方是否已 normalize
        List<String> normLines = new ArrayList<>(lineTexts.size());
        for (String s : lineTexts) {
            normLines.add(normalizeLine(s));
        }
        int j = 0;
        for (int i = 0; i < n; i++) {
            List<String> seq = normalizeLines(paragraphTexts.get(i));
            if (seq.isEmpty()) continue;
            int anchorLine = -1, anchorSeq = -1;
            for (int s = j; s < normLines.size() && anchorLine < 0; s++) {
                for (int m = 0; m < seq.size(); m++) {
                    if (lineMatches(normLines.get(s), seq.get(m))) {
                        anchorLine = s;
                        anchorSeq = m;
                        break;
                    }
                }
            }
            if (anchorLine < 0) continue;
            int s = anchorLine, m = anchorSeq, last = anchorLine;
            while (s + 1 < normLines.size() && m + 1 < seq.size()
                    && lineMatches(normLines.get(s + 1), seq.get(m + 1))) {
                s++;
                m++;
                last = s;
            }
            ranges[i][0] = anchorLine;
            ranges[i][1] = last + 1;
            j = last + 1;
        }
        return ranges;
    }

    /** 段落文本 → 规范化行序列（空白折叠、全角空格归一、去空行） */
    static List<String> normalizeLines(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            String norm = normalizeLine(line);
            if (!norm.isEmpty()) out.add(norm);
        }
        return out;
    }

    private static String normalizeLine(String line) {
        if (line == null) return "";
        return line.replace('\u3000', ' ').replaceAll("\\s+", " ").trim();
    }

    /** 行模糊匹配：完全相等；或双方足够长时允许子串包含（OCR/连字符/空白差异容错） */
    static boolean lineMatches(String a, String b) {
        if (a == null || b == null || a.length() < 2 || b.length() < 2) return false;
        if (a.equals(b)) return true;
        return a.length() >= 6 && b.length() >= 6 && (a.contains(b) || b.contains(a));
    }

    /** 逐页用 PDFTextStripper 提取行盒、行文本与字体信息（y 聚行阈值 2pt；顶左原点 pt） */
    private List<PdfLayoutAnalyzer.Line> extractPageLines(PDDocument doc, int pageIndex) throws IOException {
        List<float[]> rects = new ArrayList<>();
        List<StringBuilder> texts = new ArrayList<>();
        List<Float> fonts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        texts.add(new StringBuilder());
        final float[] lastY = {Float.NaN};
        final float[] x1 = {Float.MAX_VALUE};
        final float[] ymin = {Float.MAX_VALUE};
        final float[] x2 = {-1f};
        final float[] ymax = {-1f};
        final float[] curFont = {0f};
        final String[] curName = {""};
        final float[] curAscent = {0f};
        final float[] curDescent = {0f};
        Runnable flush = () -> {
            if (x2[0] > 0) {
                // 视觉行盒 = 基线范围 + ascent(上) + descent(下)：
                // getYDirAdj 是基线位置，文字在基线之上 ~ascent、之下 ~descent（fontDescriptor 度量，10em/1000）
                float top = ymin[0] - curAscent[0];
                float height = (ymax[0] - ymin[0]) + curAscent[0] + curDescent[0];
                rects.add(new float[]{x1[0], top, x2[0] - x1[0], height});
                texts.add(new StringBuilder());
                fonts.add(curFont[0]);
                names.add(curName[0]);
                x1[0] = Float.MAX_VALUE;
                ymin[0] = Float.MAX_VALUE;
                x2[0] = -1f;
                ymax[0] = -1f;
                curFont[0] = 0f;
                curName[0] = "";
                curAscent[0] = 0f;
                curDescent[0] = 0f;
            }
        };
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) {
                if (positions.isEmpty()) return;
                float y0 = positions.get(0).getYDirAdj();
                float x0 = positions.get(0).getXDirAdj();
                boolean newY = Float.isNaN(lastY[0]) || Math.abs(y0 - lastY[0]) > 2f;
                // 同一行基线但 x 出现大间隙（表格单元格通常是同 y 不同 x 的多个 showText）
                // → 也拆行，保证每个单元格独立成行，供几何表格检测按列聚类
                float gap = Math.max(6f, curFont[0] * 1.5f);
                boolean newX = !newY && x2[0] > 0 && (x0 - x2[0]) > gap;
                if (newY || newX) {
                    flush.run();
                }
                texts.get(texts.size() - 1).append(text == null ? "" : text);
                // 取行首字符的字号、字体名与字体度量（ascent/descent，10em/1000 折算 pt）
                float f = positions.get(0).getFontSize();
                if (f > 0) curFont[0] = f;
                String nm = null;
                try {
                    if (positions.get(0).getFont() != null) nm = positions.get(0).getFont().getName();
                } catch (Exception ignore) {
                    // 字体信息获取失败不影响几何
                }
                if (nm != null && !nm.isEmpty()) curName[0] = nm;
                try {
                    float size = positions.get(0).getFontSizeInPt() > 0
                            ? positions.get(0).getFontSizeInPt() : f;
                    float a = 0, d = 0;
                    if (positions.get(0).getFont() != null
                            && positions.get(0).getFont().getFontDescriptor() != null) {
                        org.apache.pdfbox.pdmodel.font.PDFontDescriptor fd =
                                positions.get(0).getFont().getFontDescriptor();
                        a = Math.abs(fd.getAscent());
                        d = Math.abs(fd.getDescent());
                    }
                    // 多数字体度量以 1000 为单位；缺失/异常时回退 0.75em / 0.2em
                    curAscent[0] = (a > 0 ? a : 750f) / 1000f * size;
                    curDescent[0] = (d > 0 ? d : 200f) / 1000f * size;
                } catch (Exception ignore) {
                    curAscent[0] = f * 0.75f;
                    curDescent[0] = f * 0.2f;
                }
                for (TextPosition tp : positions) {
                    float y = tp.getYDirAdj();
                    float x = tp.getXDirAdj();
                    float w = tp.getWidthDirAdj();
                    lastY[0] = y;
                    // ymin/ymax 只累计基线位置，视觉范围由 flush 用 ascent/descent 展开
                    if (x < x1[0]) x1[0] = x;
                    if (y < ymin[0]) ymin[0] = y;
                    if (y > ymax[0]) ymax[0] = y;
                    if (x + w > x2[0]) x2[0] = x + w;
                }
            }
        };
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.getText(doc);
        flush.run();
        List<PdfLayoutAnalyzer.Line> out = new ArrayList<>(rects.size());
        for (int i = 0; i < rects.size(); i++) {
            out.add(new PdfLayoutAnalyzer.Line(rects.get(i), normalizeLine(texts.get(i).toString()),
                    fonts.get(i), names.get(i)));
        }
        return out;
    }

    private int nvl(Integer v, int def) {
        return v == null ? def : v;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private ObjectNode stage(int percent, String stage, String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("percent", percent);
        node.put("stage", stage);
        if (message != null) node.put("message", message);
        return node;
    }

    private void emit(SseEmitter emitter, String event, Object data) throws Exception {
        emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
    }

    /** 写 parsed.md（向后兼容 GET /markdown）与 parsed.json（分片清单与段落映射） */
    private void writeParsedArtifacts(String kbId, String fileId, KbFile f, AiChunkApplyRequest request) {
        try {
            String md = request.getChunks().stream()
                    .map(AiChunkApplyRequest.Item::getText)
                    .collect(Collectors.joining("\n\n"));
            minioService.upload(kbId + "/" + fileId + "/parsed.md",
                    new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)), "text/markdown");

            ObjectNode root = objectMapper.createObjectNode();
            root.put("fileId", fileId);
            root.put("kbId", kbId);
            root.put("fileName", f.getName());
            root.put("appliedAt", LocalDateTime.now().toString());
            ArrayNode chunks = objectMapper.createArrayNode();
            for (int i = 0; i < request.getChunks().size(); i++) {
                AiChunkApplyRequest.Item item = request.getChunks().get(i);
                ObjectNode c = chunks.addObject();
                c.put("id", fileId + "_chunk_" + i);
                c.put("index", i);
                c.put("source", item.getSource() == null ? "auto" : item.getSource());
                if (item.getPageRange() != null) c.put("pageRange", item.getPageRange());
                ArrayNode pids = c.putArray("paragraphIds");
                if (item.getParagraphIds() != null) item.getParagraphIds().forEach(pids::add);
            }
            root.set("chunks", chunks);
            minioService.upload(kbId + "/" + fileId + "/parsed.json",
                    new ByteArrayInputStream(objectMapper.writeValueAsBytes(root)), "application/json");
        } catch (Exception e) {
            log.warn("[AiChunk] write parsed artifacts failed for {}: {}", fileId, e.getMessage());
        }
    }
}
