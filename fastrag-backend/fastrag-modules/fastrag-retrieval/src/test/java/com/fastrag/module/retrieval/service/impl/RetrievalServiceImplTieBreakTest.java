package com.fastrag.module.retrieval.service.impl;

import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.ai.rerank.RerankService;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.platform.service.ConfigManageService;
import com.fastrag.module.publish.mapper.KbLogMapper;
import com.fastrag.module.retrieval.model.RetrievalRequest;
import com.fastrag.module.retrieval.model.SearchResultItem;
import com.fastrag.module.retrieval.service.QueryEnhanceService;
import com.fastrag.module.retrieval.service.RetrievalLogService;
import com.fastrag.security.filter.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * R5（P2-5）排序 tie-break：rrfFusionMulti / deduplicateResults / MMR 的排序比较器
 * 追加 fileId → chunkIndex → chunkType 次级键——同分结果确定性排序（跨请求可复现）。
 */
@ExtendWith(MockitoExtension.class)
class RetrievalServiceImplTieBreakTest {

    @Mock private KbChunkMapper chunkMapper;
    @Mock private KbFileMapper fileMapper;
    @Mock private KnowledgeBaseMapper kbMapper;
    @Mock private KbQaPairMapper qaPairMapper;
    @Mock private RetrievalLogService logService;
    @Mock private KbLogMapper kbLogMapper;
    @Mock private MilvusService milvusService;
    @Mock private EmbeddingService embeddingService;
    @Mock private RerankService rerankService;
    @Mock private QueryEnhanceService queryEnhanceService;
    @Mock private ModelRecordMapper modelRecordMapper;
    @Mock private ConfigManageService configService;
    @Mock private GraphStore graphStore;

    @InjectMocks private RetrievalServiceImpl service;

    @BeforeEach
    void setup() {
        LoginUser user = LoginUser.builder().userId("tester").username("tester")
                .roles(List.of("admin")).permissions(List.of("*")).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private RetrievalRequest.RetrievalConfig vectorCfg() {
        RetrievalRequest.RetrievalConfig cfg = new RetrievalRequest.RetrievalConfig();
        cfg.setMode("vector");
        cfg.setTopK(10);
        cfg.setEnableGraphExpand(false);
        cfg.setEnableKeywordMatch(false);
        cfg.setEnableClauseRecall(false);
        return cfg;
    }

    private RetrievalRequest req(RetrievalRequest.RetrievalConfig cfg) {
        RetrievalRequest req = new RetrievalRequest();
        req.setKnowledgeId("kb1");
        req.setQuery("测试");
        req.setConfig(cfg);
        return req;
    }

    private KbChunk chunk(String id, String fileId, int chunkIndex, String content) {
        KbChunk c = new KbChunk();
        c.setId(id);
        c.setKbId("kb1");
        c.setFileId(fileId);
        c.setChunkIndex(chunkIndex);
        c.setContent(content);
        return c;
    }

    /** RRF 融合同分 tie-break：两路各一个 rank0 命中（RRF 分相等），fileId 小者在前 */
    @Test
    void rrfFusion_tiedScores_orderedByFileId() {
        // 向量路命中 f-c，全文路命中 f-a（都只在单路 rank0，RRF 分同为 w/(k+0)）
        when(kbMapper.selectById("kb1")).thenReturn(null);
        when(embeddingService.embed(any(), anyList(), any(), any()))
                .thenReturn(List.of(List.of(0.1f, 0.2f)));
        when(milvusService.search(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(Map.of("id", "c1", "score", 0.9d)));
        when(chunkMapper.selectByIds(anyList())).thenReturn(List.of(chunk("c1", "f-c", 0, "向量路内容")));
        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(chunk("c2", "f-a", 0, "全文路内容")));

        RetrievalRequest.RetrievalConfig cfg = new RetrievalRequest.RetrievalConfig();
        cfg.setMode("hybrid");
        cfg.setTopK(10);
        cfg.setEnableGraphExpand(false);
        cfg.setEnableKeywordMatch(false);
        cfg.setEnableClauseRecall(false);

        List<SearchResultItem> results = service.search(req(cfg));

        assertEquals(2, results.size());
        assertEquals("f-a", results.get(0).getFileId(), "RRF 同分应按 fileId 次级键排序（f-a < f-c）");
        assertEquals("f-c", results.get(1).getFileId());
    }

    /** 内容去重 tie-break：同内容同相似度保留 fileId 较小者（与到达顺序无关） */
    @Test
    void deduplicateResults_tiedSimilarity_keepsSmallerFileId() {
        when(kbMapper.selectById("kb1")).thenReturn(null);
        when(embeddingService.embed(any(), anyList(), any(), any()))
                .thenReturn(List.of(List.of(0.1f, 0.2f)));
        // 同分 0.9，两个 chunk 内容相同、fileId 不同——到达顺序 f-c 在前
        when(milvusService.search(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(
                        Map.of("id", "c1", "score", 0.9d),
                        Map.of("id", "c2", "score", 0.9d)));
        when(chunkMapper.selectByIds(anyList())).thenReturn(List.of(
                chunk("c1", "f-c", 0, "完全相同的内容"),
                chunk("c2", "f-a", 0, "完全相同的内容")));

        List<SearchResultItem> results = service.search(req(vectorCfg()));

        assertEquals(1, results.size(), "同内容应去重为一条");
        assertEquals("f-a", results.get(0).getFileId(), "去重同分应保留 tie-break 键较小（fileId 小）者");
    }

    /** MMR 选择 tie-break：两候选 MMR 分相等时先选 tie-break 键较小者 */
    @Test
    void mmrSelection_tiedScores_picksSmallerTieBreakKeyFirst() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId("kb1");
        kb.setEmbeddingModel("emb-x");
        when(kbMapper.selectById("kb1")).thenReturn(kb);
        ModelRecord mr = new ModelRecord();
        mr.setCode("emb-x");
        mr.setStatus("online");
        when(modelRecordMapper.selectOne(any())).thenReturn(mr);
        when(embeddingService.embed(any(), anyList(), any(), any()))
                .thenReturn(List.of(List.of(1f, 0f), List.of(0f, 1f))); // 正交向量 → 相互相似度 0
        when(milvusService.search(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(
                        Map.of("id", "c1", "score", 0.9d),
                        Map.of("id", "c2", "score", 0.9d)));
        when(chunkMapper.selectByIds(anyList())).thenReturn(List.of(
                chunk("c1", "f-c", 0, "内容C"),
                chunk("c2", "f-a", 0, "内容A")));

        RetrievalRequest.RetrievalConfig cfg = vectorCfg();
        cfg.setEnableMMR(true);
        cfg.setMmrLambda(1.0); // 纯相关性 → 两候选 MMR 分同为 0.9，选择即 tie

        List<SearchResultItem> results = service.search(req(cfg));

        assertEquals(2, results.size());
        assertEquals("f-a", results.get(0).getFileId(), "MMR 同分首轮选择应取 tie-break 键较小者");
    }
}
