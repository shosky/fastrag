package com.fastrag.module.retrieval.service.impl;

import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.ai.rerank.RerankService;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * R4（P1-10）主路径召回池深与融合：
 * <ul>
 *   <li>multiChannelRecall 各路池深下限 max(5×topK, 50)、上限 500——
 *       显式配置过浅（如 vectorRecallCount=topK 级别）会被抬升，融合池不再饥饿</li>
 *   <li>EmbeddingTopK ≥ RerankTopK 钳制：召回池深不小于 rerank topK</li>
 *   <li>hybrid 主路径 weightedFusion → 带路权 RRF（vectorWeight/bm25Weight 归一为路权），
 *       两路命中均保留（对既有 hybrid 回归测试的行为兼容）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RetrievalServiceImplRecallPoolTest {

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
        // 向量通道可用性探测（Milvus 不可用即早退）：本类用例默认走可用路径
        lenient().when(milvusService.isAvailable()).thenReturn(true);
    }

    private RetrievalRequest.RetrievalConfig multiCfg(int topK) {
        RetrievalRequest.RetrievalConfig cfg = new RetrievalRequest.RetrievalConfig();
        cfg.setMode("hybrid");
        cfg.setTopK(topK);
        cfg.setEnableMultiRetrieval(true);
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

    private void mockVectorAndFulltextHit() {
        when(kbMapper.selectById("kb1")).thenReturn(null);
        when(embeddingService.embed(any(), anyList(), any(), any()))
                .thenReturn(List.of(List.of(0.1f, 0.2f)));
        when(milvusService.search(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(Map.of("id", "c1", "score", 0.9d)));
        when(chunkMapper.selectByIds(anyList())).thenReturn(List.of(chunk("c1", "f1", "向量路内容")));
        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(chunk("c2", "f2", "全文路内容")));
    }

    private KbChunk chunk(String id, String fileId, String content) {
        KbChunk c = new KbChunk();
        c.setId(id);
        c.setKbId("kb1");
        c.setFileId(fileId);
        c.setChunkIndex(0);
        c.setContent(content);
        return c;
    }

    /** 池深下限：未配置召回数时各路池深 = max(5×topK, 50)（向量路 Milvus 取 3×池深便于过滤） */
    @Test
    void multiChannel_defaultPoolDepth_atLeastMax5xTopKOr50() {
        mockVectorAndFulltextHit();

        service.search(req(multiCfg(10)));

        // topK=10 → 池深 max(50,50)=50 → Milvus 检索 150
        verify(milvusService).search(anyString(), anyList(), eq(150));
        verify(chunkMapper).fulltextSearch(eq("kb1"), anyString(), eq(50));
    }

    /** 显式配置过浅被抬升：vectorRecallCount=5（< 池深下限 50）按池深下限执行 */
    @Test
    void multiChannel_shallowConfiguredCount_raisedToPoolFloor() {
        mockVectorAndFulltextHit();
        RetrievalRequest.RetrievalConfig cfg = multiCfg(10);
        cfg.setVectorRecallCount(5);

        service.search(req(cfg));

        verify(milvusService).search(anyString(), anyList(), eq(150));
    }

    /** 上限 500：显式配置 800 被钳到 500（Milvus 3×池深=1500） */
    @Test
    void multiChannel_oversizedConfiguredCount_cappedAt500() {
        mockVectorAndFulltextHit();
        RetrievalRequest.RetrievalConfig cfg = multiCfg(10);
        cfg.setVectorRecallCount(800);

        service.search(req(cfg));

        verify(milvusService).search(anyString(), anyList(), eq(1500));
    }

    /** EmbeddingTopK ≥ RerankTopK：召回池深不小于 rerank topK（topK=60 时下限 max(300,50)=300 ≥ 60） */
    @Test
    void multiChannel_poolDepth_coversRerankTopK() {
        mockVectorAndFulltextHit();

        service.search(req(multiCfg(60)));

        // 池深 max(5×60,50)=300 → Milvus 900，全文 300
        verify(milvusService).search(anyString(), anyList(), eq(900));
        verify(chunkMapper).fulltextSearch(eq("kb1"), anyString(), eq(300));
    }

    /** hybrid 主路径 RRF：两路命中均保留（行为兼容既有回归），等权下同 rank 按路序 */
    @Test
    void hybrid_fusionKeepsBothChannels_afterRrfSwap() {
        mockVectorAndFulltextHit();
        RetrievalRequest.RetrievalConfig cfg = new RetrievalRequest.RetrievalConfig();
        cfg.setMode("hybrid");
        cfg.setTopK(10);
        cfg.setEnableGraphExpand(false);
        cfg.setEnableKeywordMatch(false);
        cfg.setEnableClauseRecall(false);

        List<SearchResultItem> results = service.search(req(cfg));

        assertEquals(2, results.size(), "hybrid 主路径 RRF 后两路结果都应保留");
        assertTrue(results.stream().anyMatch(r -> "向量路内容".equals(r.getContent())));
        assertTrue(results.stream().anyMatch(r -> "全文路内容".equals(r.getContent())));
    }
}
