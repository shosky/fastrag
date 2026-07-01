package com.fastrag.module.knowledge.chunking;

import cn.hutool.json.JSONUtil;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.parser.ParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingServiceImpl implements ChunkingService {

    private final KbParseStrategyMapper strategyMapper;

    @Override
    public List<ChunkData> chunk(String text, String strategyId) {
        KbParseStrategy strategy = strategyId != null ? strategyMapper.selectById(strategyId) : null;

        int chunkLength = 500;
        int overlap = 50;
        String delimiter = "\n\n";

        if (strategy != null && strategy.getAdvanced() != null) {
            try {
                Map<String, Object> advanced = JSONUtil.toBean(strategy.getAdvanced(), Map.class);
                if (advanced.containsKey("chunkLength")) chunkLength = ((Number) advanced.get("chunkLength")).intValue();
                if (advanced.containsKey("overlap")) overlap = ((Number) advanced.get("overlap")).intValue();
                if (advanced.containsKey("delimiter")) delimiter = (String) advanced.get("delimiter");
            } catch (Exception e) {
                log.warn("Failed to parse strategy advanced config", e);
            }
        }

        return ruleBasedChunk(text, chunkLength, delimiter, overlap);
    }

    @Override
    public List<ChunkData> chunkBySegments(List<ParseResult.ChunkTimeSegment> segments, String strategyId) {
        // 读取策略配置
        KbParseStrategy strategy = strategyId != null ? strategyMapper.selectById(strategyId) : null;
        int chunkLength = 500;
        if (strategy != null && strategy.getAdvanced() != null) {
            try {
                Map<String, Object> advanced = JSONUtil.toBean(strategy.getAdvanced(), Map.class);
                if (advanced.containsKey("chunkLength")) {
                    chunkLength = ((Number) advanced.get("chunkLength")).intValue();
                }
            } catch (Exception e) {
                log.warn("Failed to parse strategy advanced config for chunkBySegments", e);
            }
        }

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

    private List<ChunkData> ruleBasedChunk(String text, int chunkLength, String delimiter, int overlap) {
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

            // 按 delimiter 分块
            String[] parts = block.split(delimiter.isEmpty() ? "\n" : delimiter, -1);
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

                if (currentChunk.length() > 0) currentChunk.append(delimiter);
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
}
