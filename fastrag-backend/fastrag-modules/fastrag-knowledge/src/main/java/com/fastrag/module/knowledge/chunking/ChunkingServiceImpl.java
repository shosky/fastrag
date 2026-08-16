package com.fastrag.module.knowledge.chunking;

/**
 * 文本分块服务实现，提供三种核心分块算法。
 *
 * <p>核心职责：
 * <ul>
 *   <li><b>规则分块（ruleBasedChunk）</b>：按分隔符列表将纯文本切分为不超 chunkLength 的块，
 *       支持 PDF PAGE_BREAK 标记实现页感知分块，支持 [IMAGE:key] 占位符提取；
 *       当 chunk 超长时保留尾部 overlap 字符延续到下一个 chunk</li>
 *   <li><b>时间戳分段分块（chunkBySegments）</b>：针对音视频 ASR 结果，
 *       根据 segment 时间范围按文本长度比例线性分配 startTime/endTime，
 *       单个 segment 超长时按句号等标点二次拆分</li>
 *   <li><b>结构感知分块（structuralChunk）</b>：基于 DocNode 文档树进行分块，
 *       使用 headingPath 栈算法维护 H1-H6 层级上下文；
 *       区分不可切分单元（TABLE/CODE_BLOCK/DISPLAY_MATH/INLINE_MATH/IMAGE）和可切分单元（PARAGRAPH）；
 *       不可切分单元整体保留，超长时按行（表格）或函数边界（代码）二次拆分；
 *       标题采用延迟合并策略，等待下一个正文节点合并，避免标题独占一个无意义 chunk；
 *       支持 tableMode=ignore 跳过表格，但对纯表格文档（Excel 类）自动豁免以避免数据丢失</li>
 * </ul>
 *
 * <p>依赖组件：
 * <ul>
 *   <li>{@link StrategyConfigResolver} — 根据 strategyId 动态获取分块参数</li>
 *   <li>{@link MarkdownSerializer} — DocNode 序列化为 Markdown 文本，以及纯文本长度计算</li>
 * </ul>
 *
 * <p>内部辅助类 ChunkBuilder 用于在结构感知分块中累积多个 node 到一个 chunk，
 * 支持标题前缀注入、headingPath 格式化、图片 key 收集和 chunkType 标记。
 */
import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.module.knowledge.config.StrategyConfigResolver;
import com.fastrag.module.knowledge.model.ParseStrategyConfig;
import com.fastrag.module.knowledge.parser.DocNode;
import com.fastrag.module.knowledge.parser.MarkdownSerializer;
import com.fastrag.module.knowledge.parser.ParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingServiceImpl implements ChunkingService {

    private final StrategyConfigResolver configResolver;
    private final MarkdownSerializer markdownSerializer;
    private final EmbeddingService embeddingService;
    private final com.fastrag.module.platform.mapper.ModelRecordMapper modelRecordMapper;

    /** 函数/类定义前缀（代码块按函数拆分用，lookahead 保留匹配前缀） */
    private static final String FUNC_SPLIT_PATTERN =
            "(?m)(?=^(\\s*(public|private|protected|static|def |class |function |interface |enum )))";

    /** Embedding 分批大小（语义切片句子向量化） */
    private static final int EMBED_BATCH_SIZE = 32;

    /** 语义切片断句用正则（后视保留分隔符） */
    private static final String SENTENCE_SPLIT_REGEX = "(?<=[。！？.!?\\n])";

    /** 递归切分的句子边界兜底分隔符 */
    private static final List<String> SENTENCE_FALLBACK_DELIMITERS = List.of("。", "！", "？", "\n", "，", ";", "；");

    @Override
    public List<ChunkData> chunk(String text, String strategyId) {
        return chunk(text, configResolver.resolve(strategyId));
    }

    /**
     * 按显式配置规则分片（预览场景：临时策略对象构造的 config，未落库）。
     */
    @Override
    public List<ChunkData> chunk(String text, ParseStrategyConfig config) {
        ParseStrategyConfig.ChunkConfig cfg = config.getChunk();
        List<String> delimiters = cfg.getDelimiters();
        String delimiterPattern = buildDelimiterPattern(delimiters);
        // join 使用分隔符列表首个（与切分语义一致），缺省 "\n\n"
        String joinDelimiter = (delimiters != null && !delimiters.isEmpty()) ? delimiters.get(0) : "\n\n";
        return ruleBasedChunk(text, cfg.getChunkLength(), delimiterPattern, joinDelimiter, cfg.getOverlap(),
                cfg.isTitlePrefix(), cfg.isHeadingPath());
    }

    // ========== 递归字符切分（rule_recursive） ==========

    @Override
    public List<ChunkData> recursiveChunk(String text, String strategyId) {
        ParseStrategyConfig config = configResolver.resolve(strategyId);
        ParseStrategyConfig.ChunkConfig cfg = config.getChunk();
        int chunkLength = Math.max(1, cfg.getChunkLength());
        int overlap = Math.max(0, cfg.getOverlap());
        List<String> delimiters = cfg.getDelimiters() != null ? cfg.getDelimiters() : List.of("\n\n");

        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 递归切分 → 原子片段（每片 ≤ chunkLength）
        List<String> pieces = recursiveSplit(text, delimiters, chunkLength);

        // 2. 贪心合并短片段（join 用分隔符列表首个，与规则路径一致）
        String joinDelimiter = delimiters.isEmpty() ? "\n\n" : delimiters.get(0);
        List<ChunkData> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int chunkIndex = 0;

        for (String piece : pieces) {
            if (piece == null || piece.trim().isEmpty()) continue;
            String trimmed = piece.trim();
            if (current.length() + trimmed.length() > chunkLength && current.length() > 0) {
                chunks.add(buildChunk(chunkIndex++, current.toString().trim(), 0, new ArrayList<>()));
                // overlap 尾部延续到下一个 chunk
                String chunkText = current.toString();
                String tail = overlap > 0 && chunkText.length() > overlap
                        ? chunkText.substring(chunkText.length() - overlap) : "";
                current = new StringBuilder(tail);
            }
            if (current.length() > 0) current.append(joinDelimiter);
            current.append(trimmed);
        }
        if (current.length() > 0) {
            chunks.add(buildChunk(chunkIndex, current.toString().trim(), 0, new ArrayList<>()));
        }

        log.info("Recursive chunking: {} chars -> {} chunks (chunkLength={}, delimiters={})",
                text.length(), chunks.size(), chunkLength, delimiters);
        return chunks;
    }

    /**
     * 递归切分：按分隔符优先级逐级降级，直到所有片段 ≤ chunkLength。
     *
     * <p>手写 indexOf 扫描切分（非正则），支持任意字符串分隔符且保留分隔符；
     * 分隔符用尽仍超长时，回退到句子边界分隔符，最后按 chunkLength 硬切。
     */
    private List<String> recursiveSplit(String text, List<String> delimiters, int chunkLength) {
        List<String> remaining = new ArrayList<>(delimiters);
        if (remaining.isEmpty()) {
            remaining.addAll(SENTENCE_FALLBACK_DELIMITERS);
        }
        return doRecursiveSplit(text, remaining, chunkLength);
    }

    private List<String> doRecursiveSplit(String text, List<String> delimiters, int chunkLength) {
        if (text.length() <= chunkLength || delimiters.isEmpty()) {
            return hardSplit(text, chunkLength);
        }

        String delim = delimiters.get(0);
        List<String> nextDelims = delimiters.subList(1, delimiters.size());

        // 按当前分隔符切分（保留分隔符）
        List<String> parts = splitKeepDelimiter(text, delim);
        if (parts.size() <= 1) {
            // 文本中不含该分隔符 → 降级到下一级分隔符
            return doRecursiveSplit(text, nextDelims, chunkLength);
        }

        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (part.trim().isEmpty()) continue;
            if (part.length() <= chunkLength) {
                result.add(part);
            } else {
                // 超长片段：递归用下一级分隔符继续切
                result.addAll(doRecursiveSplit(part, nextDelims, chunkLength));
            }
        }
        return result;
    }

    /** 按分隔符切分并保留分隔符（indexOf 扫描，非正则，支持任意字符串） */
    private static List<String> splitKeepDelimiter(String text, String delim) {
        List<String> parts = new ArrayList<>();
        if (delim == null || delim.isEmpty()) {
            parts.add(text);
            return parts;
        }
        int start = 0;
        int idx;
        while ((idx = text.indexOf(delim, start)) >= 0) {
            parts.add(text.substring(start, idx + delim.length()));
            start = idx + delim.length();
        }
        parts.add(text.substring(start));
        return parts;
    }

    /** 硬切：优先在句子边界处断，无句子边界时按 chunkLength 字符硬切 */
    private static List<String> hardSplit(String text, int chunkLength) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        if (text.length() <= chunkLength) {
            result.add(text);
            return result;
        }

        StringBuilder current = new StringBuilder();
        for (String sentence : text.split(SENTENCE_SPLIT_REGEX)) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.length() > chunkLength) {
                // 单个句子超长（无标点长串）：先落盘已有内容，再按 chunkLength 字符硬切
                if (current.length() > 0) {
                    result.add(current.toString().trim());
                    current = new StringBuilder();
                }
                for (int i = 0; i < trimmed.length(); i += chunkLength) {
                    String part = trimmed.substring(i, Math.min(i + chunkLength, trimmed.length())).trim();
                    if (!part.isEmpty()) result.add(part);
                }
                continue;
            }
            if (current.length() + trimmed.length() > chunkLength && current.length() > 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(trimmed);
        }
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        return result;
    }

    // ========== 语义切片（semantic） ==========

    /**
     * 按模型 code 查询可用的 Embedding 模型记录（限定 purpose=EMBEDDING：
     * 对话模型如 Qwen3-8B 即使配置了 apiUrl，也不能用于 /v1/embeddings）。
     */
    private com.fastrag.module.platform.entity.ModelRecord resolveEmbeddingModelRecord(String model) {
        if (model == null || model.isBlank()) return null;
        try {
            return modelRecordMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.fastrag.module.platform.entity.ModelRecord>()
                            .eq(com.fastrag.module.platform.entity.ModelRecord::getCode, model)
                            .eq(com.fastrag.module.platform.entity.ModelRecord::getPurpose, "EMBEDDING")
                            .eq(com.fastrag.module.platform.entity.ModelRecord::getStatus, "online")
                            .last("LIMIT 1"));
        } catch (Exception e) {
            log.debug("Failed to resolve embedding model record '{}': {}", model, e.getMessage());
            return null;
        }
    }

    @Override
    public List<ChunkData> semanticChunk(String text, String strategyId, String kbEmbeddingModel) {
        ParseStrategyConfig config = configResolver.resolve(strategyId);
        ParseStrategyConfig.ChunkConfig cfg = config.getChunk();
        int chunkLength = Math.max(1, cfg.getChunkLength());
        double threshold = Math.max(0, Math.min(100, cfg.getSemanticThreshold())) / 100.0;
        String model = cfg.getEmbeddingModel() != null && !cfg.getEmbeddingModel().isBlank()
                ? cfg.getEmbeddingModel() : kbEmbeddingModel;

        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 拆句
        List<String> sentences = new ArrayList<>();
        for (String s : text.split(SENTENCE_SPLIT_REGEX)) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) sentences.add(trimmed);
        }
        if (sentences.size() <= 1) {
            return chunk(text, strategyId);
        }

        // 2. 无 Embedding 模型 → 回退规则分片（语义切片不可用，保证摄入不中断）
        if (model == null || model.isBlank()) {
            log.warn("Semantic chunking requires an embedding model "
                    + "(strategy chunk.embeddingModel or KB embeddingModel), falling back to rule-based chunking");
            return chunk(text, strategyId);
        }

        // 3. 解析模型 API 配置（与存储路径一致：模型记录 apiUrl，云端服务正常使用；
        //    查不到记录时走默认网关），并校验模型类型：
        //    策略级模型在模型表中无 EMBEDDING 记录（如误配对话模型 Qwen3-8B）时，
        //    自动回退知识库级 embedding 模型 —— 历史错误配置自愈，避免 400/连接失败
        String apiUrl = null;
        String apiKey = null;
        com.fastrag.module.platform.entity.ModelRecord modelRecord = resolveEmbeddingModelRecord(model);
        if (modelRecord == null && kbEmbeddingModel != null && !kbEmbeddingModel.isBlank()
                && !kbEmbeddingModel.equals(model)) {
            log.warn("Strategy embeddingModel '{}' is not an available EMBEDDING model, "
                    + "falling back to KB embedding model '{}'", model, kbEmbeddingModel);
            model = kbEmbeddingModel;
            modelRecord = resolveEmbeddingModelRecord(model);
        }
        if (modelRecord != null) {
            apiUrl = modelRecord.getApiUrl();
            apiKey = modelRecord.getApiKeyRef();
        }

        // 4. 批量向量化（分批 32，异常整体回退）
        List<List<Float>> vectors;
        try {
            vectors = new ArrayList<>();
            for (int i = 0; i < sentences.size(); i += EMBED_BATCH_SIZE) {
                int end = Math.min(i + EMBED_BATCH_SIZE, sentences.size());
                vectors.addAll(embeddingService.embed(model, sentences.subList(i, end), apiUrl, apiKey));
            }
        } catch (Exception e) {
            log.warn("Semantic chunking embedding failed (model={}), falling back to rule-based chunking: {}",
                    model, e.getMessage());
            return chunk(text, strategyId);
        }
        if (vectors.size() != sentences.size()) {
            log.warn("Semantic chunking embedding count mismatch ({} vs {}), falling back to rule-based chunking",
                    vectors.size(), sentences.size());
            return chunk(text, strategyId);
        }

        // 4. 相邻相似度断点 + chunkLength 硬上限累积
        List<ChunkData> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int chunkIndex = 0;
        for (int i = 0; i < sentences.size(); i++) {
            boolean breakHere = false;
            // 语义断点：与上一句相似度低于阈值
            if (i > 0) {
                double sim = cosineSimilarity(vectors.get(i - 1), vectors.get(i));
                if (sim < threshold) breakHere = true;
            }
            // 硬上限：加上本句超长且已有内容
            if (current.length() > 0 && current.length() + sentences.get(i).length() > chunkLength) {
                breakHere = true;
            }
            if (breakHere) {
                chunks.add(buildChunk(chunkIndex++, current.toString().trim(), 0, new ArrayList<>()));
                current = new StringBuilder();
            }
            if (current.length() > 0) current.append("\n");
            current.append(sentences.get(i));
        }
        if (current.length() > 0) {
            chunks.add(buildChunk(chunkIndex, current.toString().trim(), 0, new ArrayList<>()));
        }

        log.info("Semantic chunking: {} sentences -> {} chunks (model={}, threshold={})",
                sentences.size(), chunks.size(), model, threshold);
        return chunks;
    }

    /** 余弦相似度（维度不一致返回 0） */
    private static double cosineSimilarity(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.isEmpty() || a.size() != b.size()) return 0.0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i), y = b.get(i);
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        if (na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    @Override
    public List<ChunkData> chunkBySegments(List<ParseResult.ChunkTimeSegment> segments, String strategyId) {
        // 读取策略配置
        int chunkLength = configResolver.resolve(strategyId).getChunk().getChunkLength();

        List<ChunkData> chunks = new ArrayList<>();
        if (segments == null || segments.isEmpty()) {
            return chunks;
        }

        int chunkIndex = 0;
        for (ParseResult.ChunkTimeSegment seg : segments) {
            String text = seg.getText() != null ? seg.getText().trim() : "";
            if (text.isEmpty()) {
                continue;
            }

            // 如果 segment 文本超过 chunkLength，按句号拆分
            if (text.length() > chunkLength) {
                double start = seg.getStartTime() != null ? seg.getStartTime() : 0.0;
                double end = seg.getEndTime() != null ? seg.getEndTime() : start;
                double duration = end - start;

                // 按句号、问号、感叹号、换行拆分
                String[] sentences = text.split("(?<=[。！？\n])");
                StringBuilder current = new StringBuilder();
                double textRatioStart = 0.0;

                for (String sentence : sentences) {
                    String trimmed = sentence.trim();
                    if (trimmed.isEmpty()) continue;

                    if (current.length() + trimmed.length() > chunkLength && current.length() > 0) {
                        // 估算当前 chunk 占据的时间比例
                        double ratio = (double) current.length() / text.length();
                        double chunkStart = start + duration * textRatioStart;
                        double chunkEnd = start + duration * (textRatioStart + ratio);

                        chunks.add(ChunkData.builder()
                                .id("chunk_" + chunkIndex)
                                .index(chunkIndex)
                                .content(current.toString().trim())
                                .startTime(chunkStart)
                                .endTime(chunkEnd)
                                .build());
                        chunkIndex++;
                        textRatioStart += ratio;
                        current = new StringBuilder();
                    }
                    current.append(trimmed);
                }

                // 最后一段
                if (current.length() > 0) {
                    double ratio = (double) current.length() / text.length();
                    double chunkStart = start + duration * textRatioStart;
                    double chunkEnd = start + duration * (textRatioStart + ratio);

                    chunks.add(ChunkData.builder()
                            .id("chunk_" + chunkIndex)
                            .index(chunkIndex)
                            .content(current.toString().trim())
                            .startTime(chunkStart)
                            .endTime(chunkEnd)
                            .build());
                    chunkIndex++;
                }
            } else {
                // 文本未超过 chunkLength，保留为单个 chunk
                chunks.add(ChunkData.builder()
                        .id("chunk_" + chunkIndex)
                        .index(chunkIndex)
                        .content(text)
                        .startTime(seg.getStartTime())
                        .endTime(seg.getEndTime())
                        .build());
                chunkIndex++;
            }
        }

        log.info("Created {} time-based chunks from {} segments (chunkLength={})",
                chunks.size(), segments.size(), chunkLength);
        return chunks;
    }

    /** Markdown 标题行正则：匹配 # ~ ###### 开头的标题（仅用于单行匹配） */
    private static final java.util.regex.Pattern MARKDOWN_HEADING =
            java.util.regex.Pattern.compile("^(#{1,6})\\s+.+$");

    /** Markdown 代码围栏行正则：3 个及以上反引号或波浪线开头（``` 或 ~~~，可带语言标注） */
    private static final java.util.regex.Pattern FENCE_LINE =
            java.util.regex.Pattern.compile("^(`{3,}|~{3,}).*$");

    private List<ChunkData> ruleBasedChunk(String text, int chunkLength, String delimiterPattern,
                                           String joinDelimiter, int overlap,
                                           boolean titlePrefix, boolean headingPath) {
        List<ChunkData> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return chunks;
        }

        // 归一化换行符：\r\n → \n，确保分隔符匹配不受操作系统差异影响
        text = text.replace("\r\n", "\n");

        // 先按 PAGE_BREAK 分割（PDF 页感知）
        String[] pageBlocks = text.split("\\[PAGE_BREAK:\\d+\\]");
        java.util.regex.Pattern pageBreakPattern = java.util.regex.Pattern.compile("\\[PAGE_BREAK:(\\d+)\\]");
        java.util.regex.Matcher pageMatcher = pageBreakPattern.matcher(text);

        // 收集每个 PAGE_BREAK 的页码
        List<Integer> pageNumbers = new ArrayList<>();
        while (pageMatcher.find()) {
            pageNumbers.add(Integer.parseInt(pageMatcher.group(1)));
        }

        boolean headingEnabled = titlePrefix || headingPath;
        int chunkIndex = 0;
        int currentPage = 1;
        List<String> headingStack = new ArrayList<>();
        String currentTitle = null;
        List<String> pendingHeadings = new ArrayList<>();  // 挂起的纯标题行（等待正文到来时合并）
        int pendingHeadStartIdx = -1;                      // 挂起标题中首个在 stack 中的下标
        boolean[] inFence = {false};                       // 代码围栏状态（跨 part / 跨页块持久）

        for (int blockIdx = 0; blockIdx < pageBlocks.length; blockIdx++) {
            String block = pageBlocks[blockIdx].trim();
            if (block.isEmpty()) continue;

            // 该 block 对应的页码
            if (blockIdx > 0 && blockIdx - 1 < pageNumbers.size()) {
                currentPage = pageNumbers.get(blockIdx - 1);
            }

            // 按分隔符分块（delimiterPattern 为多个分隔符的 regex 组合）
            String[] parts = block.split(delimiterPattern, -1);
            StringBuilder currentChunk = new StringBuilder();
            List<String> currentImageKeys = new ArrayList<>();
            // 当前 chunk 内容中已包含标题行的最深 stack 下标（之前的祖先才需要做前缀注入）
            int chunkHeadStartIdx = Integer.MAX_VALUE;
            // currentChunk 是否仅含上一次 flush 的 overlap 残尾（避免产出垃圾小 chunk）
            boolean pureOverlapTail = false;

            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;

                // 按 Markdown 标题行将 part 拆分为「标题 + 正文」片段（围栏内不识别标题）
                for (PartSegment seg : splitPartByHeadings(trimmed, headingEnabled, inFence)) {
                    boolean hasHeading = seg.headingLine() != null;
                    String body = seg.body().trim();

                    if (hasHeading) {
                        // 标题开启新章节：先刷新当前累积（携带上一章节的元数据）
                        if (currentChunk.length() > 0 && !pureOverlapTail) {
                            chunks.add(buildChunkWithHeading(chunkIndex++, currentChunk.toString().trim(),
                                    currentPage, currentImageKeys, titlePrefix, headingPath,
                                    currentTitle, headingStack, chunkHeadStartIdx));
                        }
                        currentChunk = new StringBuilder();
                        currentImageKeys = new ArrayList<>();
                        chunkHeadStartIdx = Integer.MAX_VALUE;
                        pureOverlapTail = false;

                        headingStack = updateHeadingPathByText(headingStack, seg.level(), seg.title());
                        currentTitle = seg.title();

                        if (body.isEmpty()) {
                            // 纯标题行：挂起等待与后续正文合并（避免标题单独成 chunk）
                            if (pendingHeadings.isEmpty()) {
                                pendingHeadStartIdx = headingStack.size() - 1;
                            }
                            pendingHeadings.add(seg.headingLine());
                            continue;
                        }
                    }

                    // 组装内容单元：挂起标题 + 本段标题 + 正文（标题与正文永不分离）
                    StringBuilder unit = new StringBuilder();
                    int unitHeadStartIdx = Integer.MAX_VALUE;
                    if (!pendingHeadings.isEmpty()) {
                        unit.append(String.join("\n", pendingHeadings));
                        unitHeadStartIdx = Math.min(unitHeadStartIdx, pendingHeadStartIdx);
                        pendingHeadings.clear();
                    }
                    if (hasHeading) {
                        if (unit.length() > 0) unit.append('\n');
                        unit.append(seg.headingLine());
                        unitHeadStartIdx = Math.min(unitHeadStartIdx, headingStack.size() - 1);
                    }
                    if (!body.isEmpty()) {
                        if (unit.length() > 0) unit.append('\n');
                        unit.append(body);
                    }

                    // 提取 [IMAGE:key] 占位符
                    String unitText = unit.toString();
                    List<String> imageKeysInPart = new ArrayList<>();
                    java.util.regex.Pattern imgPattern = java.util.regex.Pattern.compile("\\[IMAGE:([^\\]]+)\\]");
                    java.util.regex.Matcher imgMatcher = imgPattern.matcher(unitText);
                    StringBuffer cleanPart = new StringBuffer();
                    while (imgMatcher.find()) {
                        imageKeysInPart.add(imgMatcher.group(1));
                        imgMatcher.appendReplacement(cleanPart, "");
                    }
                    imgMatcher.appendTail(cleanPart);
                    String cleanText = cleanPart.toString().trim();
                    if (cleanText.isEmpty() && imageKeysInPart.isEmpty()) continue;

                    // 单个单元超长时，先按字符强制拆分为多个子片段
                    if (cleanText.length() > chunkLength) {
                        // 先刷新当前累积的 chunk
                        if (currentChunk.length() > 0 && !pureOverlapTail) {
                            chunks.add(buildChunkWithHeading(chunkIndex++, currentChunk.toString().trim(),
                                    currentPage, currentImageKeys, titlePrefix, headingPath,
                                    currentTitle, headingStack, chunkHeadStartIdx));
                        }
                        currentChunk = new StringBuilder();
                        currentImageKeys = new ArrayList<>();
                        chunkHeadStartIdx = Integer.MAX_VALUE;
                        pureOverlapTail = false;
                        // 将超长单元切分为不超过 chunkLength 的子 chunk
                        List<String> subChunks = forceSplit(cleanText, chunkLength, overlap);
                        for (int i = 0; i < subChunks.size(); i++) {
                            // 首个子 chunk 已含标题行，后续子 chunk 用完整路径做前缀
                            int prefixIdx = (i == 0) ? Math.min(unitHeadStartIdx, headingStack.size())
                                    : headingStack.size();
                            chunks.add(buildChunkWithHeading(chunkIndex++, subChunks.get(i).trim(),
                                    currentPage, imageKeysInPart, titlePrefix, headingPath,
                                    currentTitle, headingStack, prefixIdx));
                        }
                        continue;
                    }

                    if (currentChunk.length() + cleanText.length() > chunkLength && currentChunk.length() > 0) {
                        // 保存当前 chunk
                        chunks.add(buildChunkWithHeading(chunkIndex++, currentChunk.toString().trim(),
                                currentPage, currentImageKeys, titlePrefix, headingPath,
                                currentTitle, headingStack, chunkHeadStartIdx));
                        // overlap
                        String chunkText = currentChunk.toString();
                        String overlapText = chunkText.substring(Math.max(0, chunkText.length() - overlap));
                        currentChunk = new StringBuilder(overlapText);
                        currentImageKeys = new ArrayList<>();
                        chunkHeadStartIdx = Integer.MAX_VALUE;
                        pureOverlapTail = true;
                    }

                    if (currentChunk.length() > 0) currentChunk.append(joinDelimiter);
                    currentChunk.append(cleanText);
                    currentImageKeys.addAll(imageKeysInPart);
                    chunkHeadStartIdx = Math.min(chunkHeadStartIdx, unitHeadStartIdx);
                    pureOverlapTail = false;
                }
            }

            // page 内最后一段
            if (currentChunk.length() > 0 && !pureOverlapTail) {
                chunks.add(buildChunkWithHeading(chunkIndex++, currentChunk.toString().trim(),
                        currentPage, currentImageKeys, titlePrefix, headingPath,
                        currentTitle, headingStack, chunkHeadStartIdx));
            } else if (!pendingHeadings.isEmpty() && headingEnabled) {
                // 文档以标题结尾且无后续正文：将挂起标题作为独立 chunk 输出
                chunks.add(buildChunkWithHeading(chunkIndex++, String.join("\n", pendingHeadings).trim(),
                        currentPage, Collections.emptyList(), titlePrefix, headingPath,
                        currentTitle, headingStack, pendingHeadStartIdx));
                pendingHeadings.clear();
            }
        }

        return chunks;
    }

    /** part 按标题行拆分后的片段：一个标题（可空）+ 其后的正文行 */
    private record PartSegment(String headingLine, int level, String title, String body) {}

    /**
     * 将 part 按行扫描，遇到 Markdown 标题行即开启新片段。
     * 标题未启用时整段作为单一无标题片段返回。
     *
     * @param inFence 单元素布尔持有器：跨 part 持久的代码围栏状态。
     *                代码块内常含空行，分隔符切分会把一个围栏拆进多个 part，
     *                故状态在调用方持有、跨 part / 跨页块传递；围栏内不识别标题（# 注释行等）
     */
    private List<PartSegment> splitPartByHeadings(String part, boolean enabled, boolean[] inFence) {
        List<PartSegment> segments = new ArrayList<>();
        if (!enabled) {
            segments.add(new PartSegment(null, 0, null, part));
            return segments;
        }
        String curHeading = null;
        int curLevel = 0;
        String curTitle = null;
        StringBuilder body = new StringBuilder();
        for (String line : part.split("\n", -1)) {
            String t = line.trim();
            if (FENCE_LINE.matcher(t).matches()) {
                // 围栏行：翻转状态，本身作为正文保留
                inFence[0] = !inFence[0];
                if (body.length() > 0) body.append('\n');
                body.append(line);
            } else if (!inFence[0] && MARKDOWN_HEADING.matcher(t).matches()) {
                if (curHeading != null || body.length() > 0) {
                    segments.add(new PartSegment(curHeading, curLevel, curTitle, body.toString()));
                }
                curHeading = t;
                curLevel = countLeadingHashes(t);
                curTitle = t.replaceFirst("^#{1,6}\\s+", "").trim();
                body = new StringBuilder();
            } else {
                if (body.length() > 0) body.append('\n');
                body.append(line);
            }
        }
        if (curHeading != null || body.length() > 0) {
            segments.add(new PartSegment(curHeading, curLevel, curTitle, body.toString()));
        }
        return segments;
    }

    /** 统计行首连续 # 的个数（标题级别） */
    private static int countLeadingHashes(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == '#') n++;
        return Math.max(1, Math.min(6, n));
    }

    /**
     * 基于 Markdown 标题级别更新 headingPath 栈（纯文本版本，无需 DocNode）。
     * 语义与 {@link #updateHeadingPath(List, DocNode)} 一致：
     * 同级替换，更高级弹出子级，当前标题压入对应深度。
     */
    private List<String> updateHeadingPathByText(List<String> stack, int level, String title) {
        level = Math.max(1, Math.min(6, level));
        List<String> newStack = new ArrayList<>(stack);
        while (newStack.size() >= level) {
            newStack.remove(newStack.size() - 1);
        }
        newStack.add(title != null ? title : "");
        return newStack;
    }

    /**
     * 构建标题栈前 count 项的 Markdown 前缀（规则分片路径）。
     * count 语义：stack 中下标 < count 的祖先标题不在 chunk content 内，需要以前缀形式补全。
     */
    private static String buildHeadingPrefix(List<String> stack, int count) {
        if (stack == null || stack.isEmpty() || count <= 0) return "";
        int n = Math.min(count, stack.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append("\n\n");
            sb.append("#".repeat(i + 1)).append(" ").append(stack.get(i));
        }
        return sb.toString();
    }

    /**
     * 带标题上下文的 buildChunk（规则分片路径使用）。
     * titlePrefix=true 时将 content 未包含的祖先标题作为 Markdown 前缀注入 content；
     * headingPath=true 时设置 title 和 headingPath 元数据。
     *
     * @param contentHeadStartIdx content 中已包含标题行的最深 stack 下标，
     *                            之前的祖先（下标 < 该值）以前缀注入；MAX_VALUE 表示 content 无标题行
     */
    private ChunkData buildChunkWithHeading(int index, String content, int pageNumber,
                                             List<String> imageKeys,
                                             boolean titlePrefix, boolean headingPath,
                                             String title, List<String> headingStack,
                                             int contentHeadStartIdx) {
        // titlePrefix: 将 content 未包含的祖先标题前缀注入
        int prefixCount = titlePrefix ? Math.min(contentHeadStartIdx, headingStack.size()) : 0;
        String prefix = buildHeadingPrefix(headingStack, prefixCount);
        String fullContent = prefix.isEmpty() ? content : prefix + "\n\n" + content;

        ChunkData.ChunkDataBuilder builder = ChunkData.builder()
                .id("chunk_" + index)
                .index(index)
                .content(fullContent)
                .pageNumber(pageNumber)
                .pageRange(String.valueOf(pageNumber));
        if (!imageKeys.isEmpty()) {
            builder.imageKeys(imageKeys);
        }
        // headingPath: 设置 title 和 headingPath 元数据
        if (headingPath) {
            if (title != null) builder.title(title);
            String path = formatHeadingPath(headingStack);
            if (path != null) builder.headingPath(path);
        }
        return builder.build();
    }

    /**
     * 将超长文本按 chunkLength 强制拆分（硬切），每个子片段带 overlap 尾部重叠。
     */
    private List<String> forceSplit(String text, int chunkLength, int overlap) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        int effectiveLength = Math.max(chunkLength, overlap + 1); // 防止 overlap >= chunkLength 死循环
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + effectiveLength, text.length());
            result.add(text.substring(start, end));
            if (end >= text.length()) break;
            start = end - overlap;
        }
        return result;
    }

    private ChunkData buildChunk(int index, String content, int pageNumber, List<String> imageKeys) {
        ChunkData.ChunkDataBuilder builder = ChunkData.builder()
                .id("chunk_" + index)
                .index(index)
                .content(content)
                .pageNumber(pageNumber)
                .pageRange(String.valueOf(pageNumber));
        if (!imageKeys.isEmpty()) {
            builder.imageKeys(imageKeys);
        }
        return builder.build();
    }

    // ========== 结构感知分片（基于 DocNode） ==========

    @Override
    public List<ChunkData> structuralChunk(List<DocNode> nodes, String strategyId) {
        ParseStrategyConfig config = configResolver.resolve(strategyId);
        ParseStrategyConfig.ChunkConfig chunkCfg = config.getChunk();
        ParseStrategyConfig.ParseConfig parseCfg = config.getParse();
        int chunkLength = chunkCfg.getChunkLength();
        int overlap = chunkCfg.getOverlap();
        boolean ignoreTables = "ignore".equals(parseCfg.getTableMode());

        // C3 修复：纯表格文档（Excel 类，nodes 仅含 HEADING+TABLE）忽略 tableMode=ignore，
        // 否则跳过 TABLE 节点会导致全部数据静默丢失
        if (ignoreTables && isPureTableDocument(nodes)) {
            log.warn("tableMode=ignore configured but document is pure table (Excel-like), "
                    + "keeping TABLE nodes to avoid data loss");
            ignoreTables = false;
        }

        List<ChunkData> result = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) return result;

        int chunkIndex = 0;
        ChunkBuilder current = new ChunkBuilder(chunkCfg.isTitlePrefix(), chunkCfg.isHeadingPath());
        String currentTitle = null;
        List<String> headingStack = new ArrayList<>();
        String pendingHeading = null; // 延迟合并的标题，等待下一个正文节点
        Integer pendingHeadingPage = null;

        for (DocNode node : nodes) {
            if (node == null) continue;

            // 更新标题上下文
            if (node.getType() == DocNode.NodeType.HEADING) {
                // 先保存当前 chunk（纯标题 current 不 flush，等待正文合并）
                if (current.length() > 0 && current.hasBodyContent()) {
                    result.add(flushWithOverlap(current, currentTitle, headingStack, chunkIndex++, overlap));
                }
                // 连续标题（如 PPT 空标题页）：前一个 pending 标题先入 chunk，避免被新标题覆盖丢失
                if (pendingHeading != null) {
                    current.appendHeading(pendingHeading, pendingHeadingPage);
                    pendingHeading = null;
                    pendingHeadingPage = null;
                }
                headingStack = updateHeadingPath(headingStack, node);
                currentTitle = node.getTitle();
                // 不立即加入 current，存为 pending，等下一个正文节点来的时候合并
                // 避免标题单独占一个 chunk（对于 RAG 检索无意义）
                pendingHeading = markdownSerializer.serializeNode(node);
                pendingHeadingPage = node.getPageNumber();
                continue;
            }

            // tableMode=ignore：跳过表格节点
            if (ignoreTables && node.getType() == DocNode.NodeType.TABLE) {
                continue;
            }

            // 有 pending 的标题待合并 —— 用 appendHeading：标题不算正文，
            // 防止「仅含标题的 current」在后续 flush 中被单独产出（标题孤 chunk）
            if (pendingHeading != null) {
                current.appendHeading(pendingHeading, pendingHeadingPage);
                pendingHeading = null;
                pendingHeadingPage = null;
            }

            if (isAtomicUnit(node)) {
                // 不可切分单元 — 整体保留
                String serialized = markdownSerializer.serializeNode(node);
                int plainLen = markdownSerializer.plainTextLength(serialized);

                // 仅当 current 已含正文时才 flush；纯标题 current 与原子单元合并在同一 chunk，
                // 超长交给 handleAtomicOverflow 按行拆分（标题前缀只附加到首个子 chunk）
                if (current.hasBodyContent() && current.length() + plainLen > chunkLength) {
                    result.add(flushWithOverlap(current, currentTitle, headingStack, chunkIndex++, overlap));
                }
                current.append(serialized, node.getPageNumber(), node);
            } else {
                // 可切分单元 — 文本段落：按句切句后累积进当前 chunk，接近 chunkLength 再落盘
                String text = node.getContent();
                if (text == null || text.trim().isEmpty()) continue;

                List<String> sentences = splitText(text, chunkLength);
                for (String sentence : sentences) {
                    if (sentence.trim().isEmpty()) continue;
                    if (current.hasBodyContent() && current.length() + sentence.length() > chunkLength) {
                        result.add(flushWithOverlap(current, currentTitle, headingStack, chunkIndex++, overlap));
                    }
                    current.appendInline(sentence, node.getPageNumber());
                }
            }
        }

        // 最后一个 chunk：先合并尾部 pending 标题。
        // 连续标题场景下 current 可能仅含上一个 pending 标题（如 B'），若直接 flush 会得到
        // 「内容=B' 但 title=最新标题 C」的元数据错位 chunk；合并后再 flush 保证标题行不丢失且元数据一致
        if (pendingHeading != null) {
            current.appendHeading(pendingHeading, pendingHeadingPage);
            pendingHeading = null;
            pendingHeadingPage = null;
        }
        if (current.length() > 0) {
            result.add(flushWithOverlap(current, currentTitle, headingStack, chunkIndex++, overlap));
        }

        // Atomic Unit 超长拆分
        result = handleAtomicOverflow(result, chunkLength);

        log.info("Structural chunking: {} nodes -> {} chunks (chunkLength={})",
                nodes.size(), result.size(), chunkLength);
        return result;
    }

    /**
     * 保存当前 chunk，并将 chunk 内容尾部 overlap 字符保留给下一个 chunk（与规则路径行为一致）。
     *
     * @return 构建完成的 chunk
     */
    private ChunkData flushWithOverlap(ChunkBuilder current, String title, List<String> headingStack,
                                       int chunkIndex, int overlap) {
        ChunkData chunk = current.build(title, headingStack, chunkIndex);
        String content = chunk.getContent();
        String tail = (overlap > 0 && content != null && content.length() > overlap)
                ? content.substring(content.length() - overlap)
                : "";
        current.reset(tail);
        return chunk;
    }

    /**
     * headingPath 栈替换算法
     *
     * 维护一个列表模拟固定深度栈（H1-H6），遇到 H_n 时：
     * 1. 截断栈中 index >= n 的元素（离开子章节）
     * 2. 将当前标题放入栈的 index = n-1 位置
     * 3. 返回更新后的栈
     *
     * 示例：
     *   H1 "第一章"    → [第一章]
     *   H2 "1.1 背景"   → [第一章, 1.1 背景]
     *   H3 "1.1.1 定义"  → [第一章, 1.1 背景, 1.1.1 定义]
     *   H2 "1.2 方案"   → [第一章, 1.2 方案]  (index>=2 被截断)
     */
    private List<String> updateHeadingPath(List<String> stack, DocNode heading) {
        int level = heading.getLevel() != null ? heading.getLevel() : 1;
        // 限制 level 1-6
        level = Math.max(1, Math.min(6, level));

        List<String> newStack = new ArrayList<>(stack);
        // 截断：移除深度 >= level 的元素
        while (newStack.size() >= level) {
            newStack.remove(newStack.size() - 1);
        }
        // 放入当前标题
        newStack.add(heading.getTitle() != null ? heading.getTitle() : "");
        return newStack;
    }

    /**
     * 将 headingPath 栈格式化为字符串
     */
    private static String formatHeadingPath(List<String> stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.stream()
                .filter(Objects::nonNull)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" > "));
    }

    /**
     * 判断是否为不可切分单元（Atomic Unit）
     */
    private boolean isAtomicUnit(DocNode node) {
        return switch (node.getType()) {
            case TABLE, CODE_BLOCK, DISPLAY_MATH, INLINE_MATH, IMAGE -> true;
            default -> false;
        };
    }

    /**
     * 判断文档是否为纯表格文档（仅 HEADING + TABLE，或含表格标题型短 PARAGRAPH）。
     * 用于 C3 修复：tableMode=ignore 对 Excel 这类纯表格文档不生效，避免数据静默丢失。
     */
    private boolean isPureTableDocument(List<DocNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return false;
        for (DocNode node : nodes) {
            if (node == null) continue;
            switch (node.getType()) {
                case HEADING, TABLE -> {
                    // 允许
                }
                case PARAGRAPH -> {
                    // Excel 表格辅助段落允许不破坏"纯表格文档"判定：
                    // - 表格标题行（如"附表1：xxx"，短文本且无正文标点）
                    // - 表尾说明行（以"说明/注/备注/注释"开头，如"说明：1、24年审计..."）
                    // 其余正文型长段落则视为混合文档
                    String content = node.getContent();
                    if (content == null) return false;
                    boolean isTableNote = content.startsWith("说明") || content.startsWith("注")
                            || content.startsWith("备注") || content.startsWith("注释");
                    if (content.length() > 1000
                            || !isTableNote && (content.length() > 100
                            || content.contains("。") || content.contains("，") || content.contains("；"))) {
                        return false;
                    }
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Atomic Unit 超长拆分策略
     * - 表格：按行拆分，每个子 chunk 复制表头
     * - 代码块：按函数/类边界拆分
     * - 公式/图片：保持完整
     */
    private List<ChunkData> handleAtomicOverflow(List<ChunkData> chunks, int maxLen) {
        List<ChunkData> result = new ArrayList<>();
        // 收集已存在的 chunkIndex，找出最大值以避免冲突
        int maxIndex = chunks.stream().mapToInt(ChunkData::getIndex).max().orElse(0);
        int overflowIdx = maxIndex + 1;

        for (ChunkData chunk : chunks) {
            if (chunk == null) continue;
            if (markdownSerializer.plainTextLength(chunk.getContent()) <= maxLen) {
                result.add(chunk);
                continue;
            }
            String chunkType = chunk.getChunkType() != null ? chunk.getChunkType() : "text";
            switch (chunkType) {
                case "table" -> {
                    List<ChunkData> split = splitTableByRow(chunk, maxLen);
                    for (ChunkData s : split) {
                        s.setIndex(overflowIdx++);
                        s.setId("chunk_" + s.getIndex());
                    }
                    result.addAll(split);
                }
                case "code" -> {
                    List<ChunkData> split = splitCodeByFunction(chunk, maxLen);
                    for (ChunkData s : split) {
                        s.setIndex(overflowIdx++);
                        s.setId("chunk_" + s.getIndex());
                    }
                    result.addAll(split);
                }
                default -> result.add(chunk); // 公式、图片保持完整
            }
        }
        return result;
    }

    /**
     * 表格按行拆分，每个子 chunk 携带 "表头 | 数据行" 的片段。
     * 兼容标题前缀：前缀（标题 Markdown）只附加到第一个子 chunk。
     */
    private List<ChunkData> splitTableByRow(ChunkData chunk, int maxLen) {
        List<ChunkData> result = new ArrayList<>();
        String content = chunk.getContent();
        if (content == null || content.isEmpty()) {
            result.add(chunk);
            return result;
        }

        String[] lines = content.split("\n");

        // 定位表头行（首个以 | 开头的行），其前的行视为前缀（如标题）
        int headerIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("|")) {
                headerIdx = i;
                break;
            }
        }
        if (headerIdx < 0 || lines.length < headerIdx + 3) {
            // 没有找到表格结构，不够拆
            result.add(chunk);
            return result;
        }

        String prefix = headerIdx > 0
                ? String.join("\n", Arrays.copyOfRange(lines, 0, headerIdx)).trim()
                : "";
        String header = lines[headerIdx];
        String separator = lines[headerIdx + 1];
        StringBuilder current = new StringBuilder();
        boolean firstSubChunk = true;

        for (int i = headerIdx + 2; i < lines.length; i++) {
            String line = lines[i];
            String candidate = current.length() > 0
                    ? current + "\n" + line
                    : buildTableHeader(prefix, header, separator, firstSubChunk);

            if (markdownSerializer.plainTextLength(candidate) > maxLen && current.length() > 0) {
                result.add(buildTableSubChunk(chunk, current.toString(), firstSubChunk));
                firstSubChunk = false;
                // 新片段从表头开始
                current = new StringBuilder(buildTableHeader(prefix, header, separator, firstSubChunk));
            } else {
                if (current.length() == 0) {
                    current.append(buildTableHeader(prefix, header, separator, firstSubChunk));
                }
                current.append("\n").append(line);
            }
        }
        if (current.length() > 0) {
            result.add(buildTableSubChunk(chunk, current.toString(), firstSubChunk));
        }
        return result;
    }

    private static String buildTableHeader(String prefix, String header, String separator, boolean firstSubChunk) {
        String head = header + "\n" + separator;
        if (firstSubChunk && !prefix.isEmpty()) {
            return prefix + "\n" + head;
        }
        return head;
    }

    private static ChunkData buildTableSubChunk(ChunkData chunk, String content, boolean firstSubChunk) {
        ChunkData.ChunkDataBuilder builder = ChunkData.builder()
                .content(content)
                .title(chunk.getTitle())
                .headingPath(chunk.getHeadingPath())
                .pageNumber(chunk.getPageNumber())
                .pageRange(chunk.getPageRange())
                .chunkType("table");
        if (chunk.getImageKeys() != null) builder.imageKeys(chunk.getImageKeys());
        return builder.build();
    }

    /**
     * 代码块按函数/类边界拆分（兼容标题前缀与代码围栏）。
     * 子 chunk 恢复 ```lang 围栏；前缀只附加到第一个子 chunk。
     */
    private List<ChunkData> splitCodeByFunction(ChunkData chunk, int maxLen) {
        List<ChunkData> result = new ArrayList<>();
        String content = chunk.getContent();
        if (content == null || content.isEmpty()) {
            result.add(chunk);
            return result;
        }

        // 提取前缀 + 代码围栏语言 + 代码体
        String prefix = "";
        String lang = "";
        String body = content;
        int fenceStart = content.indexOf("```");
        if (fenceStart >= 0) {
            prefix = content.substring(0, fenceStart).trim();
            String afterFence = content.substring(fenceStart + 3);
            int langEnd = afterFence.indexOf('\n');
            lang = langEnd >= 0 ? afterFence.substring(0, langEnd).trim() : "";
            int bodyStart = langEnd >= 0 ? langEnd + 1 : 0;
            int fenceEnd = afterFence.lastIndexOf("```");
            body = fenceEnd > bodyStart ? afterFence.substring(bodyStart, fenceEnd) : afterFence.substring(bodyStart);
        }

        // 按函数/类定义切分（lookahead 保留匹配前缀）
        String[] parts = body.split(FUNC_SPLIT_PATTERN);
        if (parts.length <= 1) {
            // 无法按函数拆分，按行数均分
            return splitCodeByLine(chunk, maxLen);
        }

        StringBuilder current = new StringBuilder();
        boolean firstSubChunk = true;
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            String candidate = current.length() > 0 ? current + "\n" + trimmed : trimmed;
            if (markdownSerializer.plainTextLength(candidate) > maxLen && current.length() > 0) {
                result.add(buildCodeSubChunk(chunk, prefix, lang, current.toString(), firstSubChunk));
                firstSubChunk = false;
                current = new StringBuilder(trimmed);
            } else {
                if (current.length() > 0) current.append("\n");
                current.append(trimmed);
            }
        }
        if (current.length() > 0) {
            result.add(buildCodeSubChunk(chunk, prefix, lang, current.toString(), firstSubChunk));
        }
        return result;
    }

    /**
     * 代码块按行数均分（当无法按函数边界拆分时）
     */
    private List<ChunkData> splitCodeByLine(ChunkData chunk, int maxLen) {
        List<ChunkData> result = new ArrayList<>();
        String content = chunk.getContent();

        String prefix = "";
        String lang = "";
        String body = content;
        int fenceStart = content.indexOf("```");
        if (fenceStart >= 0) {
            prefix = content.substring(0, fenceStart).trim();
            String afterFence = content.substring(fenceStart + 3);
            int langEnd = afterFence.indexOf('\n');
            lang = langEnd >= 0 ? afterFence.substring(0, langEnd).trim() : "";
            int bodyStart = langEnd >= 0 ? langEnd + 1 : 0;
            int fenceEnd = afterFence.lastIndexOf("```");
            body = fenceEnd > bodyStart ? afterFence.substring(bodyStart, fenceEnd) : afterFence.substring(bodyStart);
        }

        String[] lines = body.split("\n");
        StringBuilder current = new StringBuilder();
        boolean firstSubChunk = true;

        for (String line : lines) {
            String candidate = current.length() > 0 ? current + "\n" + line : line;
            if (markdownSerializer.plainTextLength(candidate) > maxLen && current.length() > 0) {
                result.add(buildCodeSubChunk(chunk, prefix, lang, current.toString(), firstSubChunk));
                firstSubChunk = false;
                current = new StringBuilder(line);
            } else {
                if (current.length() > 0) current.append("\n");
                current.append(line);
            }
        }
        if (current.length() > 0) {
            result.add(buildCodeSubChunk(chunk, prefix, lang, current.toString(), firstSubChunk));
        }
        return result;
    }

    private static ChunkData buildCodeSubChunk(ChunkData chunk, String prefix, String lang,
                                               String body, boolean firstSubChunk) {
        String fenced = "```" + lang + "\n" + body.trim() + "\n```";
        String content = firstSubChunk && !prefix.isEmpty() ? prefix + "\n\n" + fenced : fenced;
        ChunkData.ChunkDataBuilder builder = ChunkData.builder()
                .content(content)
                .title(chunk.getTitle())
                .headingPath(chunk.getHeadingPath())
                .pageNumber(chunk.getPageNumber())
                .pageRange(chunk.getPageRange())
                .chunkType("code");
        if (chunk.getImageKeys() != null) builder.imageKeys(chunk.getImageKeys());
        return builder.build();
    }

    /**
     * 将文本按句子边界切分，每段不超过 chunkLength
     */
    private List<String> splitText(String text, int chunkLength) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;

        // 按句号、问号、感叹号、换行符拆分
        String[] sentences = text.split("(?<=[。！？.!?\\n])");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) continue;

            if (current.length() + trimmed.length() > chunkLength && current.length() > 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (current.length() > 0) current.append(" ");
            current.append(trimmed);
        }
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        return result;
    }

    /**
     * 将分隔符列表编译为正则（按长度降序，保证 "\n\n" 优先于 "\n" 匹配）
     */
    private static String buildDelimiterPattern(List<String> delimiters) {
        if (delimiters == null || delimiters.isEmpty()) return "\n";
        return delimiters.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(java.util.regex.Pattern::quote)
                .collect(Collectors.joining("|"));
    }

    /**
     * Chunk 构建器 — 用于累积多个 node 到一个 chunk 中。
     * 支持标题前缀、headingPath 元数据开关、图片 key / chunkType 收集（F3 修复）。
     */
    private static class ChunkBuilder {
        private final StringBuilder content = new StringBuilder();
        private Integer pageNumber = null;
        private String pageRange = null;
        private final List<String> imageKeys = new ArrayList<>();
        private String typeHint = "text";
        private final boolean titlePrefixEnabled;
        private final boolean headingPathEnabled;
        /** 是否已追加正文内容（连续标题场景下，纯标题 current 不应被 flush） */
        private boolean hasBodyContent = false;

        ChunkBuilder(boolean titlePrefixEnabled, boolean headingPathEnabled) {
            this.titlePrefixEnabled = titlePrefixEnabled;
            this.headingPathEnabled = headingPathEnabled;
        }

        /** 追加块级内容（标题/原子单元），以空行分隔 */
        public void append(String text, Integer nodePage) {
            if (content.length() > 0) {
                content.append("\n\n");
            }
            content.append(text);
            updatePage(nodePage);
            hasBodyContent = true;
        }

        /** 追加标题（不标记正文——连续标题时避免纯标题 chunk 被 flush） */
        public void appendHeading(String text, Integer nodePage) {
            if (content.length() > 0) {
                content.append("\n\n");
            }
            content.append(text);
            updatePage(nodePage);
        }

        /** 是否已包含正文内容 */
        public boolean hasBodyContent() {
            return hasBodyContent;
        }

        /** 追加原子单元（携带类型与图片 key） */
        public void append(String serialized, Integer nodePage, DocNode node) {
            append(serialized, nodePage);
            switch (node.getType()) {
                case TABLE -> typeHint = "table";
                case CODE_BLOCK -> typeHint = "code";
                case IMAGE -> {
                    typeHint = "image";
                    if (node.getImageKey() != null) {
                        imageKeys.add(node.getImageKey());
                    }
                }
                default -> { /* 其他类型不改变 chunkType */ }
            }
        }

        /** 追加行内内容（句子），以单个换行连接，保持句间连续 */
        public void appendInline(String text, Integer nodePage) {
            if (content.length() > 0) {
                content.append("\n");
            }
            content.append(text);
            updatePage(nodePage);
            hasBodyContent = true;
        }

        /** 重置构建器（overlap 尾部延续）。tail 仅为上一切片的残尾，不算正文，防止被单独 flush */
        public void reset(String tail) {
            content.setLength(0);
            content.append(tail);
            pageNumber = null;
            pageRange = null;
            imageKeys.clear();
            typeHint = "text";
            hasBodyContent = false;
        }

        public int length() {
            return content.length();
        }

        public ChunkData build(String title, List<String> headingStack, int index) {
            String rawContent = content.toString().trim();
            // 将父级标题层级作为 Markdown 前缀加入 content
            // content 中已包含最近标题（通过 pendingHeading 合并），
            // 这里补充祖先标题，使得 chunk 自包含完整文档上下文
            String headingPrefix = titlePrefixEnabled ? buildParentHeadingPrefix(headingStack, rawContent) : "";
            String fullContent = headingPrefix.isEmpty() ? rawContent : headingPrefix + "\n\n" + rawContent;

            ChunkData.ChunkDataBuilder builder = ChunkData.builder()
                    .id("chunk_" + index)
                    .index(index)
                    .content(fullContent)
                    .pageNumber(pageNumber)
                    .pageRange(pageRange)
                    .chunkType(typeHint);
            if (headingPathEnabled) {
                if (title != null) builder.title(title);
                String path = formatHeadingPath(headingStack);
                if (path != null) builder.headingPath(path);
            }
            if (!imageKeys.isEmpty()) {
                builder.imageKeys(new ArrayList<>(imageKeys));
            }
            return builder.build();
        }

        private void updatePage(Integer nodePage) {
            if (nodePage != null) {
                if (pageNumber == null) {
                    pageNumber = nodePage;
                    pageRange = String.valueOf(nodePage);
                } else if (!nodePage.equals(pageNumber)) {
                    pageRange = pageNumber + "-" + nodePage;
                }
            }
        }

        /**
         * 构建父级标题的 Markdown 前缀。
         * content 中已包含最近标题，故跳过 stack 最后一个元素避免重复。
         * 示例：stack=[H1, H2, H3]，content="### H3\n正文"
         *        → 前缀 = "# H1\n\n## H2"
         */
        private String buildParentHeadingPrefix(List<String> stack, String existingContent) {
            if (stack == null || stack.isEmpty()) return "";
            // 跳过最近标题（已在 content 中），最多保留 5 个父级
            int end = Math.max(0, stack.size() - 1);
            if (end == 0) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < end; i++) {
                if (i > 0) sb.append("\n\n");
                sb.append("#".repeat(i + 1)).append(" ").append(stack.get(i));
            }
            return sb.toString();
        }
    }
}
