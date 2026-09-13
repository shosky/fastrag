package com.fastrag.module.knowledge.chunking;

import com.fastrag.module.knowledge.entity.KbChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * 图谱抽取单元组装器（v1.2 架构核心，LightRAG/GraphRAG 同款思路）。
 *
 * <p>把一个文件的全部分片按 chunkIndex 顺序重组为若干"抽取单元"——单元是语义上远比
 * 500 字碎片完整的中大文本块（默认 ~2000 字符 ≈ LightRAG 的 1200 token 默认值），
 * 单次 LLM 调用抽取一个单元的实体与关系。语义完整的章节（如"营销六步法"标题+六个步骤）
 * 天然落在同一单元内，跨分片关系在单元内直接抽出，无需 carry/stitch/backfill 补丁层。</p>
 *
 * <p>检索层的 500 字分片保持不变（服务向量检索精度）；本组装器只服务图谱抽取。</p>
 */
public final class ExtractionUnitAssembler {

    private ExtractionUnitAssembler() {}

    /** 抽取单元：一段重组文本 + 组成它的分片（证据归属与状态回写的依据） */
    public record ExtractionUnit(int index, String content, String contentHash, List<KbChunk> chunks) {
    }

    /**
     * 组装抽取单元：按序累积分片，累计超过 maxChars 即切一段。
     * 单个超长分片独占一个单元（不截断分片内容本身）；空内容分片按 0 长度参与。
     *
     * @param orderedChunks 同一文件内按 chunkIndex 升序的分片
     * @param maxChars      单元字符预算（graph.build.unit-max-chars）
     * @param hashFn        内容哈希函数（缓存键，如 GraphIdHashing::hashstr32）
     */
    public static List<ExtractionUnit> assemble(List<KbChunk> orderedChunks, int maxChars,
                                                java.util.function.Function<String, String> hashFn) {
        List<ExtractionUnit> units = new ArrayList<>();
        List<KbChunk> current = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        for (KbChunk c : orderedChunks) {
            int len = c.getContent() != null ? c.getContent().length() : 0;
            if (!current.isEmpty() && currentText.length() + len > maxChars) {
                units.add(buildUnit(units.size(), current, currentText.toString(), hashFn));
                current = new ArrayList<>();
                currentText = new StringBuilder();
            }
            current.add(c);
            if (len > 0) {
                if (currentText.length() > 0) currentText.append('\n');
                currentText.append(c.getContent());
            }
        }
        if (!current.isEmpty()) {
            units.add(buildUnit(units.size(), current, currentText.toString(), hashFn));
        }
        return units;
    }

    private static ExtractionUnit buildUnit(int index, List<KbChunk> chunks, String content,
                                            java.util.function.Function<String, String> hashFn) {
        return new ExtractionUnit(index, content, hashFn.apply(content), List.copyOf(chunks));
    }
}
