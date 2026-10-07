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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R6（P2-6）多 KB 共享查询向量：同一查询文本 + 同一嵌入模型在短 TTL 内只 embed 一次
 * （agentic 请求内模型对多个 KB 连续 query_kb 时共享向量，不再每 KB 重复调 embedding API）。
 */
@ExtendWith(MockitoExtension.class)
class RetrievalServiceImplEmbedCacheTest {

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

        // 两个 KB 共用同一嵌入模型 emb-x（模型解析结果一致 → 缓存键一致）
        KnowledgeBase kb1 = new KnowledgeBase();
        kb1.setId("kb1");
        kb1.setEmbeddingModel("emb-x");
        KnowledgeBase kb2 = new KnowledgeBase();
        kb2.setId("kb2");
        kb2.setEmbeddingModel("emb-x");
        when(kbMapper.selectById(any())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return "kb2".equals(id) ? kb2 : kb1;
        });
        ModelRecord mr = new ModelRecord();
        mr.setCode("emb-x");
        mr.setStatus("online");
        when(modelRecordMapper.selectOne(any())).thenReturn(mr);

        when(embeddingService.embed(any(), anyList(), any(), any()))
                .thenReturn(List.of(List.of(0.1f, 0.2f)));
        when(milvusService.search(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(Map.of("id", "c1", "score", 0.9d)));
        when(chunkMapper.selectByIds(anyList())).thenReturn(List.of(chunk("c1", "f1", "内容")));
    }

    /** 同查询 + 同嵌入模型：第二个 KB 的检索复用缓存向量，不再二次 embed */
    @Test
    void embedCache_sameQueryAcrossKbs_embedsOnlyOnce() {
        service.search(req("kb1"));
        service.search(req("kb2"));

        verify(embeddingService, times(1)).embed(any(), anyList(), any(), any());
        // 两 KB 均拿到同一向量完成 Milvus 检索
        verify(milvusService, times(2)).search(anyString(), eq(List.of(0.1f, 0.2f)), anyInt());
    }

    /** 不同查询不走缓存（各 embed 一次） */
    @Test
    void embedCache_differentQueries_embedsSeparately() {
        service.search(req("kb1", "查询甲"));
        service.search(req("kb1", "查询乙"));

        verify(embeddingService, times(2)).embed(any(), anyList(), any(), any());
    }

    private RetrievalRequest req(String kbId) {
        return req(kbId, "同一个查询");
    }

    private RetrievalRequest req(String kbId, String query) {
        RetrievalRequest.RetrievalConfig cfg = new RetrievalRequest.RetrievalConfig();
        cfg.setMode("vector");
        cfg.setTopK(10);
        RetrievalRequest req = new RetrievalRequest();
        req.setKnowledgeId(kbId);
        req.setQuery(query);
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
