package com.fastrag.module.knowledge.consumer;

import com.fastrag.module.graph.util.ExtractionNormalizer;
import com.fastrag.module.graph.util.GraphIdHashing;
import com.fastrag.module.knowledge.chunking.ExtractionUnitAssembler;
import com.fastrag.module.knowledge.chunking.ExtractionUnitAssembler.ExtractionUnit;
import com.fastrag.module.knowledge.entity.KbChunk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v1.2 单元抽取架构纯逻辑测试：单元组装、退化抽取兜底、质量评分。
 * Neo4j 查询与 LLM 调用部分依赖真实环境，不在单测范围。
 */
class GraphBuildConsumerUnitTest {

    // ==================== 抽取单元组装（v1.2 核心） ====================

    @Test
    void assemble_partitionsByCharBudget_andKeepsOrder() {
        KbChunk c1 = chunkWithContent(1, 600);
        KbChunk c2 = chunkWithContent(2, 600);
        KbChunk c3 = chunkWithContent(3, 600);

        List<ExtractionUnit> units = ExtractionUnitAssembler.assemble(List.of(c1, c2, c3), 1000, GraphIdHashing::hashstr32);

        // 600+600 > 1000 → 每个单元 1 个分片，保持 chunkIndex 顺序
        assertEquals(3, units.size());
        assertEquals(0, units.get(0).index());
        assertEquals(1, units.get(1).index());
        assertEquals(2, units.get(2).index());
        assertEquals(600, units.get(0).content().length());
    }

    @Test
    void assemble_packsSmallChunks_intoOneUnit() {
        KbChunk c1 = chunkWithContent(1, 300);
        KbChunk c2 = chunkWithContent(2, 300);
        KbChunk c3 = chunkWithContent(3, 300);

        List<ExtractionUnit> units = ExtractionUnitAssembler.assemble(List.of(c1, c2, c3), 1000, GraphIdHashing::hashstr32);

        // 300×3 = 900 ≤ 1000 → 全部进一个单元（语义完整章节天然同框）
        assertEquals(1, units.size());
        assertEquals(3, units.get(0).chunks().size());
        assertTrue(units.get(0).content().contains("\n"));
    }

    @Test
    void assemble_oversizedChunk_getsOwnUnitWithoutTruncation() {
        KbChunk small = chunkWithContent(1, 300);
        KbChunk huge = chunkWithContent(2, 5000);
        KbChunk tail = chunkWithContent(3, 300);

        List<ExtractionUnit> units = ExtractionUnitAssembler.assemble(List.of(small, huge, tail), 1000, GraphIdHashing::hashstr32);

        // [small] [huge 独占、不截断] [tail]
        assertEquals(3, units.size());
        assertEquals(huge, units.get(1).chunks().get(0));
        assertEquals(5000, units.get(1).content().length());
    }

    @Test
    void assemble_contentHash_isDeterministicPerContent() {
        KbChunk c1 = chunkWithContent(1, 100);
        KbChunk c2 = chunkWithContent(2, 100);

        List<ExtractionUnit> units = ExtractionUnitAssembler.assemble(List.of(c1, c2), 1000, GraphIdHashing::hashstr32);
        List<ExtractionUnit> again = ExtractionUnitAssembler.assemble(List.of(c1, c2), 1000, GraphIdHashing::hashstr32);

        // 相同内容 → 相同哈希（单元级缓存键）
        assertEquals(units.get(0).contentHash(), again.get(0).contentHash());
        KbChunk edited = chunkWithContent(2, 100);
        edited.setContent(c2.getContent() + "新增内容");
        List<ExtractionUnit> changed = ExtractionUnitAssembler.assemble(List.of(c1, edited), 1000, GraphIdHashing::hashstr32);
        assertTrue(!units.get(0).contentHash().equals(changed.get(0).contentHash()));
    }

    @Test
    void assemble_emptyChunks_tolerated() {
        KbChunk empty = new KbChunk();
        empty.setId("f1_chunk_1");
        empty.setFileId("f1");
        empty.setChunkIndex(1);

        List<ExtractionUnit> units = ExtractionUnitAssembler.assemble(List.of(empty), 1000, GraphIdHashing::hashstr32);

        assertEquals(1, units.size());
        assertEquals("", units.get(0).content());
    }

    // ==================== 退化抽取兜底 ====================

    @Test
    void isDegenerateExtraction_allRelationsLackEndpoints_true() {
        ExtractionNormalizer.ExtractionResult r = new ExtractionNormalizer.ExtractionResult();
        ExtractionNormalizer.Entity e = new ExtractionNormalizer.Entity();
        e.setText("网络维护");
        e.setLabel("业务流程");
        r.setEntities(List.of(e));
        ExtractionNormalizer.Relation blank1 = new ExtractionNormalizer.Relation();
        blank1.setLabel("RELATED_TO");
        ExtractionNormalizer.Relation blank2 = new ExtractionNormalizer.Relation();
        blank2.setLabel("RELATED_TO");
        r.setRelations(List.of(blank1, blank2));

        assertTrue(GraphBuildConsumer.isDegenerateExtraction(r));
    }

    @Test
    void isDegenerateExtraction_normalOrEmptyRelations_false() {
        ExtractionNormalizer.ExtractionResult r = new ExtractionNormalizer.ExtractionResult();
        ExtractionNormalizer.Relation ok = new ExtractionNormalizer.Relation();
        ok.setSource("华为");
        ok.setTarget("任正非");
        ok.setLabel("创始人");
        r.setRelations(List.of(ok));
        assertFalse(GraphBuildConsumer.isDegenerateExtraction(r));

        // 无关系不判退化（那是 gleaning 的职责）
        ExtractionNormalizer.ExtractionResult noRel = new ExtractionNormalizer.ExtractionResult();
        noRel.setRelations(new ArrayList<>());
        assertFalse(GraphBuildConsumer.isDegenerateExtraction(noRel));
        assertFalse(GraphBuildConsumer.isDegenerateExtraction(null));
    }

    @Test
    void extractionScore_sumsEntitiesAndRelations() {
        ExtractionNormalizer.ExtractionResult r = new ExtractionNormalizer.ExtractionResult();
        ExtractionNormalizer.Entity e = new ExtractionNormalizer.Entity();
        e.setText("A");
        r.setEntities(List.of(e));
        ExtractionNormalizer.Relation rel = new ExtractionNormalizer.Relation();
        rel.setSource("A");
        rel.setTarget("B");
        r.setRelations(List.of(rel));

        assertEquals(2, GraphBuildConsumer.extractionScore(r));
        assertEquals(0, GraphBuildConsumer.extractionScore(null));
    }

    // ==================== 工具 ====================

    private static KbChunk chunkWithContent(int chunkIndex, int contentLen) {
        KbChunk c = new KbChunk();
        c.setId("f1_chunk_" + chunkIndex);
        c.setFileId("f1");
        c.setKbId("kb-1");
        c.setChunkIndex(chunkIndex);
        StringBuilder sb = new StringBuilder(contentLen);
        for (int i = 0; i < contentLen; i++) sb.append('a');
        c.setContent(sb.toString());
        return c;
    }
}
