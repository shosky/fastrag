package com.fastrag.module.knowledge.chunking;

/**
 * 父分片聚合器 — 将连续的子分片按标题层级（headingPath）聚合成父分片。
 *
 * <p>核心职责（父子分片）：
 * <ul>
 *   <li>按文档顺序扫描子分片，<b>连续且 headingPath 相同</b>的分片归为同一组（章节）</li>
 *   <li>组内 ≥1 个分片时生成父分片：内容 = 子分片内容按 "\n\n" 拼接（子分片已自带标题前缀，父分片自包含上下文）</li>
 *   <li>组总长超过 maxParentLength（= 子分片长度 × 2）时，按子分片边界二次切分为多个父分片，共享标题上下文</li>
 *   <li>每个章节组至少生成一个父分片，确保父子关系完整可用</li>
 *   <li>父分片 id = {fileId}_parent_{n}，chunk_index 取组内首个子分片位置（保证列表按文档顺序排列）</li>
 * </ul>
 *
 * <p>设计说明：聚合发生在<b>存储层</b>而非分片算法层——因为摄入流水线在分片之后还会
 * 按页归并图片 OCR 分片（IngestionConsumer），存储层聚合可以保证图片分片同样进入所属章节父分片。
 */
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ParentChunkAssembler {

    /** 聚合结果：父分片列表 + 子分片列表（子分片 parentId 已回填） */
    public record AssemblyResult(List<ChunkData> parents, List<ChunkData> children) {
        public boolean hasParents() {
            return parents != null && !parents.isEmpty();
        }
    }

    /**
     * 聚合父分片（按完整 headingPath 分组，auto 层级）。
     *
     * @param chunks          最终分片列表（含图片分片，按文档顺序）
     * @param fileId          文件 ID（用于生成父分片 ID）
     * @param maxParentLength 父分片最大长度（超过则按子分片边界二次切分）
     * @return 聚合结果；chunks 中的子分片会回填 parentId
     */
    public AssemblyResult assemble(List<ChunkData> chunks, String fileId, int maxParentLength) {
        return assemble(chunks, fileId, maxParentLength, null);
    }

    /**
     * 聚合父分片（支持按聚合层级分组）。
     *
     * @param aggLevel 聚合层级：H1/H2/H3 时按截断到对应层级的 headingPath 分组，
     *                 auto 或 null 时按完整 headingPath 分组（现状行为）
     */
    public AssemblyResult assemble(List<ChunkData> chunks, String fileId, int maxParentLength, String aggLevel) {
        if (chunks == null || chunks.isEmpty()) {
            return new AssemblyResult(List.of(), chunks == null ? List.of() : chunks);
        }
        if (chunks.size() < 2) {
            // 单分片文档无聚合意义
            return new AssemblyResult(List.of(), chunks);
        }

        int aggLevelValue = parseAggLevel(aggLevel);
        List<ChunkData> parents = new ArrayList<>();
        int parentSeq = 0;

        // 按连续相同分组键分组（文档顺序）
        List<ChunkData> group = new ArrayList<>();
        String groupKey = keyOf(chunks.get(0), aggLevelValue);
        for (ChunkData chunk : chunks) {
            String key = keyOf(chunk, aggLevelValue);
            if (!key.equals(groupKey) && !group.isEmpty()) {
                parentSeq = buildParents(group, fileId, maxParentLength, parentSeq, parents);
                group = new ArrayList<>();
                groupKey = key;
            }
            group.add(chunk);
        }
        if (!group.isEmpty()) {
            buildParents(group, fileId, maxParentLength, parentSeq, parents);
        }

        if (!parents.isEmpty()) {
            log.info("ParentChunkAssembler: {} chunks -> {} parents (maxParentLength={}, aggLevel={}, fileId={})",
                    chunks.size(), parents.size(), maxParentLength, aggLevel != null ? aggLevel : "auto", fileId);
        }
        return new AssemblyResult(parents, chunks);
    }

    /** 解析聚合层级字符串为标题深度（H1=1, H2=2, H3=3），auto/非法值返回 0（=完整路径） */
    private static int parseAggLevel(String aggLevel) {
        if (aggLevel == null || aggLevel.isBlank() || "auto".equalsIgnoreCase(aggLevel)) return 0;
        String level = aggLevel.toUpperCase().replace("H", "").trim();
        try {
            int v = Integer.parseInt(level);
            return v >= 1 && v <= 3 ? v : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 分组键：headingPath 为 null/空视为同一组（无标题上下文，如文档前言） */
    private static String keyOf(ChunkData chunk, int aggLevel) {
        String path = chunk.getHeadingPath();
        if (path == null || path.isEmpty()) return "";
        if (aggLevel <= 0) return path;
        // 截断到指定层级："第一章 > 1.1 背景" (H2=2) → "第一章"
        String[] segments = path.split("\\s*>\\s*");
        if (segments.length <= aggLevel) return path;
        return String.join(" > ", Arrays.copyOf(segments, aggLevel));
    }

    /**
     * 将一个连续同标题组切分为一个或多个父分片。
     * 组总长超过 maxParentLength 时按子分片边界二次切分（每组至少 1 个子分片）。
     *
     * @return 下一个可用的父分片序号
     */
    private static int buildParents(List<ChunkData> group, String fileId, int maxParentLength,
                                    int parentSeq, List<ChunkData> parents) {
        if (group.isEmpty()) {
            return parentSeq;
        }

        List<ChunkData> current = new ArrayList<>();
        int currentLen = 0;
        for (ChunkData chunk : group) {
            int len = chunk.getContent() != null ? chunk.getContent().length() : 0;
            if (!current.isEmpty() && currentLen + len > maxParentLength) {
                parents.add(createParent(current, fileId, parentSeq++));
                current = new ArrayList<>();
                currentLen = 0;
            }
            current.add(chunk);
            currentLen += len;
        }
        if (!current.isEmpty()) {
            parents.add(createParent(current, fileId, parentSeq++));
        }
        return parentSeq;
    }

    /** 由一组子分片构建父分片，并回填子分片的 parentId */
    private static ChunkData createParent(List<ChunkData> children, String fileId, int seq) {
        ChunkData first = children.get(0);
        String content = children.stream()
                .map(ChunkData::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n\n"));
        String parentId = fileId + "_parent_" + seq;
        for (ChunkData child : children) {
            child.setParentId(parentId);
        }
        return ChunkData.builder()
                .id(parentId)
                .index(first.getIndex())   // 取首个子分片位置，保证列表按文档顺序排列
                .content(content)
                .chunkType("parent")
                .title(first.getTitle())
                .headingPath(first.getHeadingPath())
                .pageNumber(first.getPageNumber())
                .pageRange(first.getPageRange())
                .build();
    }
}
