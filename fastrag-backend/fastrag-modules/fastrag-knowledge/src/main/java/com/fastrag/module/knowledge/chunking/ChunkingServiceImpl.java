package com.fastrag.module.knowledge.chunking;

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

    /** 函数/类定义前缀（代码块按函数拆分用，lookahead 保留匹配前缀） */
    private static final String FUNC_SPLIT_PATTERN =
            "(?m)(?=^(\\s*(public|private|protected|static|def |class |function |interface |enum )))";

    @Override
    public List<ChunkData> chunk(String text, String strategyId) {
        ParseStrategyConfig config = configResolver.resolve(strategyId);
        ParseStrategyConfig.ChunkConfig cfg = config.getChunk();
        List<String> delimiters = cfg.getDelimiters();
        String delimiterPattern = buildDelimiterPattern(delimiters);
        // join 使用分隔符列表首个（与切分语义一致），缺省 "\n\n"
        String joinDelimiter = (delimiters != null && !delimiters.isEmpty()) ? delimiters.get(0) : "\n\n";
        return ruleBasedChunk(text, cfg.getChunkLength(), delimiterPattern, joinDelimiter, cfg.getOverlap());
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

    private List<ChunkData> ruleBasedChunk(String text, int chunkLength, String delimiterPattern,
                                           String joinDelimiter, int overlap) {
        List<ChunkData> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return chunks;
        }

        // 先按 PAGE_BREAK 分割（PDF 页感知）
        String[] pageBlocks = text.split("\\[PAGE_BREAK:\\d+\\]");
        java.util.regex.Pattern pageBreakPattern = java.util.regex.Pattern.compile("\\[PAGE_BREAK:(\\d+)\\]");
        java.util.regex.Matcher pageMatcher = pageBreakPattern.matcher(text);

        // 收集每个 PAGE_BREAK 的页码
        List<Integer> pageNumbers = new ArrayList<>();
        while (pageMatcher.find()) {
            pageNumbers.add(Integer.parseInt(pageMatcher.group(1)));
        }

        int chunkIndex = 0;
        int currentPage = 1;

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

            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;

                // 提取 [IMAGE:key] 占位符
                List<String> imageKeysInPart = new ArrayList<>();
                java.util.regex.Pattern imgPattern = java.util.regex.Pattern.compile("\\[IMAGE:([^\\]]+)\\]");
                java.util.regex.Matcher imgMatcher = imgPattern.matcher(trimmed);
                StringBuffer cleanPart = new StringBuffer();
                while (imgMatcher.find()) {
                    imageKeysInPart.add(imgMatcher.group(1));
                    imgMatcher.appendReplacement(cleanPart, "");
                }
                imgMatcher.appendTail(cleanPart);
                String cleanText = cleanPart.toString().trim();
                if (cleanText.isEmpty() && imageKeysInPart.isEmpty()) continue;

                if (currentChunk.length() + cleanText.length() > chunkLength && currentChunk.length() > 0) {
                    // 保存当前 chunk
                    chunks.add(buildChunk(chunkIndex++, currentChunk.toString().trim(),
                            currentPage, currentImageKeys));
                    // overlap
                    String chunkText = currentChunk.toString();
                    String overlapText = chunkText.substring(Math.max(0, chunkText.length() - overlap));
                    currentChunk = new StringBuilder(overlapText);
                    currentImageKeys = new ArrayList<>();
                }

                if (currentChunk.length() > 0) currentChunk.append(joinDelimiter);
                currentChunk.append(cleanText);
                currentImageKeys.addAll(imageKeysInPart);
            }

            // page 内最后一段
            if (currentChunk.length() > 0) {
                chunks.add(buildChunk(chunkIndex++, currentChunk.toString().trim(),
                        currentPage, currentImageKeys));
            }
        }

        return chunks;
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
                // 先保存当前 chunk
                if (current.length() > 0) {
                    result.add(flushWithOverlap(current, currentTitle, headingStack, chunkIndex++, overlap));
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

            // 有 pending 的标题待合并
            if (pendingHeading != null) {
                current.append(pendingHeading, pendingHeadingPage);
                pendingHeading = null;
                pendingHeadingPage = null;
            }

            if (isAtomicUnit(node)) {
                // 不可切分单元 — 整体保留
                String serialized = markdownSerializer.serializeNode(node);
                int plainLen = markdownSerializer.plainTextLength(serialized);

                // 如果当前 chunk 已有内容且加上这个单元会超限，先保存当前 chunk
                if (current.length() > 0 && current.length() + plainLen > chunkLength) {
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
                    if (current.length() > 0 && current.length() + sentence.length() > chunkLength) {
                        result.add(flushWithOverlap(current, currentTitle, headingStack, chunkIndex++, overlap));
                    }
                    current.appendInline(sentence, node.getPageNumber());
                }
            }
        }

        // 最后一个 chunk（含末尾可能的 pending heading）
        if (current.length() > 0) {
            result.add(flushWithOverlap(current, currentTitle, headingStack, chunkIndex++, overlap));
        } else if (pendingHeading != null) {
            // 文档末尾的标题没有正文，单独作为一个 chunk（但这种情况很少见）
            ChunkBuilder solo = new ChunkBuilder(chunkCfg.isTitlePrefix(), chunkCfg.isHeadingPath());
            solo.append(pendingHeading, pendingHeadingPage);
            result.add(solo.build(currentTitle, headingStack, chunkIndex++));
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
        }

        /** 重置构建器（overlap 尾部延续） */
        public void reset(String tail) {
            content.setLength(0);
            content.append(tail);
            pageNumber = null;
            pageRange = null;
            imageKeys.clear();
            typeHint = "text";
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
