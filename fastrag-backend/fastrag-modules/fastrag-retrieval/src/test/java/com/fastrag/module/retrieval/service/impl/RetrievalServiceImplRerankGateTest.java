package com.fastrag.module.retrieval.service.impl;

import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.ai.rerank.RerankService;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
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
import static org.mockito.Mockito.when;

/**
 * R3（P1-9）rerank 质量闸门：
 * <ul>
 *   <li>分级阈值：低于阈值丢弃（默认 0.05 级别，可经 rerankScoreThreshold 配置）</li>
 *   <li>四级降级：阈值过滤 → 全灭时阈值 ×0.7 放行 → 仍空 top-1 保底 → 模型异常原序返回+诊断标记</li>
 *   <li>passage 拼装：送 rerank 的文档 content 前拼 fileName 标题行（不改写返回条目 content）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RetrievalServiceImplRerankGateTest {

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
        // rerank 模型在线
        ModelRecord mr = new ModelRecord();
        mr.setCode("rr-model");
        mr.setStatus("online");
        mr.setApiUrl("http://rerank");
        mr.setApiKeyRef("key");
        when(modelRecordMapper.selectOne(any())).thenReturn(mr);
    }

    private RetrievalRequest.RetrievalConfig rerankCfg() {
        RetrievalRequest.RetrievalConfig cfg = new RetrievalRequest.RetrievalConfig();
        cfg.setMode("fulltext");
        cfg.setTopK(10);
        cfg.setEnableRerank(true);
        cfg.setRerankModel("rr-model");
        cfg.setContextAssemblyStrategy("concat"); // 前置重排（不经组装后重排路径）
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

    private void mockTwoCandidates() {
        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt())).thenReturn(List.of(
                chunk("c1", "f1", "内容A"),
                chunk("c2", "f2", "内容B")));
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

    private KbFile activeFile(String id, String name) {
        KbFile f = new KbFile();
        f.setId(id);
        f.setKbId("kb1");
        f.setName(name);
        return f;
    }

    /** 阈值过滤：默认阈值（0.05 级）以下的结果被丢弃 */
    @Test
    void rerankGate_dropsCandidatesBelowDefaultThreshold() {
        mockTwoCandidates();
        when(rerankService.rerank(eq("rr-model"), anyString(), anyList(), anyInt(), any(), any()))
                .thenReturn(List.of(
                        Map.of("index", 0, "relevance_score", 0.9d),
                        Map.of("index", 1, "relevance_score", 0.01d)));

        List<SearchResultItem> results = service.search(req(rerankCfg()));

        assertEquals(1, results.size(), "低于默认阈值的候选应被闸门丢弃");
        assertEquals("内容A", results.get(0).getContent());
        assertEquals(0.9d, results.get(0).getSimilarity(), 1e-9);
    }

    /** 全灭降级：全部低于阈值时按阈值 ×0.7 放行 */
    @Test
    void rerankGate_allBelowThreshold_relaxesThresholdByFactor0_7() {
        mockTwoCandidates();
        // 默认阈值 0.05 全灭；×0.7=0.035 后 0.04 过、0.03 不过
        when(rerankService.rerank(eq("rr-model"), anyString(), anyList(), anyInt(), any(), any()))
                .thenReturn(List.of(
                        Map.of("index", 0, "relevance_score", 0.04d),
                        Map.of("index", 1, "relevance_score", 0.03d)));

        List<SearchResultItem> results = service.search(req(rerankCfg()));

        assertEquals(1, results.size(), "全灭时应以 ×0.7 放宽阈值放行");
        assertEquals("内容A", results.get(0).getContent());
    }

    /** 仍空保底：放宽后仍为空时 top-1 保底（rerank 分最高者） */
    @Test
    void rerankGate_stillEmptyAfterRelax_fallsBackToTop1() {
        mockTwoCandidates();
        // 0.02/0.01 均低于放宽阈值 0.035 → top-1（0.02，index 1）保底
        when(rerankService.rerank(eq("rr-model"), anyString(), anyList(), anyInt(), any(), any()))
                .thenReturn(List.of(
                        Map.of("index", 1, "relevance_score", 0.02d),
                        Map.of("index", 0, "relevance_score", 0.01d)));

        List<SearchResultItem> results = service.search(req(rerankCfg()));

        assertEquals(1, results.size(), "放宽后仍空时应 top-1 保底");
        assertEquals("内容B", results.get(0).getContent(), "保底应取 rerank 分最高的候选");
    }

    /** 模型异常：原序返回 + 诊断标记（不抛出、不丢结果） */
    @Test
    void rerankGate_modelException_returnsOriginalOrderTopK() {
        mockTwoCandidates();
        when(rerankService.rerank(eq("rr-model"), anyString(), anyList(), anyInt(), any(), any()))
                .thenThrow(new RuntimeException("rerank gateway timeout"));

        List<SearchResultItem> results = service.search(req(rerankCfg()));

        assertEquals(2, results.size(), "模型异常应原序返回候选（诊断标记见 [RerankGate] warn 日志）");
        assertEquals("内容A", results.get(0).getContent(), "原序：第一个候选仍在前");
        assertEquals("内容B", results.get(1).getContent());
    }

    /** 可配置阈值：rerankScoreThreshold 覆盖默认值 */
    @Test
    void rerankGate_configurableThreshold_overridesDefault() {
        mockTwoCandidates();
        RetrievalRequest.RetrievalConfig cfg = rerankCfg();
        cfg.setRerankScoreThreshold(0.8);
        // 阈值 0.8 全灭 → 放宽 0.56 → 0.7 过、0.5 不过
        when(rerankService.rerank(eq("rr-model"), anyString(), anyList(), anyInt(), any(), any()))
                .thenReturn(List.of(
                        Map.of("index", 0, "relevance_score", 0.7d),
                        Map.of("index", 1, "relevance_score", 0.5d)));

        List<SearchResultItem> results = service.search(req(cfg));

        assertEquals(1, results.size());
        assertEquals("内容A", results.get(0).getContent());
    }

    /** passage 拼装：送 rerank 的文档 content 前拼 fileName 标题行 */
    @Test
    void rerankGate_passagesPrefixedWithFileNameTitleLine() {
        mockTwoCandidates();
        when(fileMapper.selectBatchIds(any())).thenReturn(List.of(activeFile("f1", "政策A.pdf")));
        when(rerankService.rerank(eq("rr-model"), anyString(), anyList(), anyInt(), any(), any()))
                .thenReturn(List.of(Map.of("index", 0, "relevance_score", 0.9d)));

        service.search(req(rerankCfg()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> docsCaptor = ArgumentCaptor.forClass((Class) List.class);
        org.mockito.Mockito.verify(rerankService).rerank(eq("rr-model"), anyString(),
                docsCaptor.capture(), anyInt(), any(), any());
        List<String> documents = docsCaptor.getValue();
        assertTrue(documents.get(0).startsWith("【政策A.pdf】"), "有文件名的候选应拼 fileName 标题行");
        assertTrue(documents.get(0).contains("内容A"), "标题行后保留原 content");
        // 无文件名的候选（f2 未查出）不拼标题行
        assertEquals("内容B", documents.get(1));
        // 拼装只作用于 rerank 输入，不改写返回条目 content
        List<SearchResultItem> results = service.search(req(rerankCfg()));
        assertEquals("内容A", results.get(0).getContent(), "返回条目 content 不得被标题行污染");
    }
}
