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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * R6-1（P2-6）图谱通道复用主查询向量：同一次 search 的主查询向量只 embed 一次，
 * 图谱通道的向量实体匹配直接复用（省一次 embedding API 往返），不再重复调用 embed。
 */
@ExtendWith(MockitoExtension.class)
class RetrievalServiceImplGraphVectorReuseTest {

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

        KnowledgeBase kb = new KnowledgeBase();
        kb.setId("kb1");
        kb.setEmbeddingModel("emb-x");
        when(kbMapper.selectById("kb1")).thenReturn(kb);
        ModelRecord mr = new ModelRecord();
        mr.setCode("emb-x");
        mr.setStatus("online");
        when(modelRecordMapper.selectOne(any())).thenReturn(mr);
    }

    /** 主查询向量 embed 一次，图谱通道向量实体匹配复用同一向量（而非二次 embed） */
    @Test
    void graphChannel_reusesMainQueryVector_singleEmbedCall() {
        List<Float> mainVector = List.of(0.1f, 0.2f);
        List<Float> secondCallVector = List.of(0.9f, 0.9f); // 若二次 embed 会拿到这个
        when(embeddingService.embed(any(), anyList(), any(), any()))
                .thenReturn(List.of(mainVector), List.of(secondCallVector));

        // 主检索：向量路命中
        when(milvusService.search(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(Map.of("id", "c1", "score", 0.9d)));
        when(chunkMapper.selectByIds(anyList())).thenReturn(List.of(chunk("c1", "f1", "主检索内容")));
        // 图谱通道：文本实体无命中，向量实体命中
        when(queryEnhanceService.expandGraph(any(), anyString(), anyInt(), anyInt(), any()))
                .thenReturn(Map.of("entities", List.of(), "relations", List.of()));
        when(graphStore.searchEntitiesByVector(eq("kb1"), anyList(), anyInt()))
                .thenReturn(List.of(Map.of("name", "实体X", "score", 0.9d)));
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk("gc1", "gf1", "图谱命中内容")));

        RetrievalRequest.RetrievalConfig cfg = new RetrievalRequest.RetrievalConfig();
        cfg.setMode("vector");
        cfg.setTopK(10);

        List<SearchResultItem> results = service.search(req(cfg));

        // 主断言：embed 只调用一次（主查询向量复用，图谱通道不再 embed）
        verify(embeddingService, times(1)).embed(any(), anyList(), any(), any());
        // 图谱通道收到的向量 = 主查询向量
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Float>> vecCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(graphStore).searchEntitiesByVector(eq("kb1"), vecCaptor.capture(), anyInt());
        assertEquals(mainVector, vecCaptor.getValue(), "图谱通道应复用主查询向量");
        assertTrue(results.stream().anyMatch(r -> "图谱命中内容".equals(r.getContent())),
                "图谱命中仍应参与融合");
    }

    private RetrievalRequest req(RetrievalRequest.RetrievalConfig cfg) {
        RetrievalRequest req = new RetrievalRequest();
        req.setKnowledgeId("kb1");
        req.setQuery("测试");
        req.setConfig(cfg);
        return req;
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
}
