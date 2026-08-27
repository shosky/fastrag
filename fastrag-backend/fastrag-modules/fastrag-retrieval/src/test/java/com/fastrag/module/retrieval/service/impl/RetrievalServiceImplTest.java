package com.fastrag.module.retrieval.service.impl;

import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.ai.rerank.RerankService;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbQaPair;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RetrievalServiceImpl 检索参数生效性测试：
 * <ul>
 *   <li>similarityThreshold 支持 0 值（不过滤）</li>
 *   <li>bm25RecallCount 作为 fulltext 候选数</li>
 *   <li>enableKeywordMatch 命中问答对时 QA 结果优先</li>
 *   <li>请求未传 mode/topK 时不覆盖 KB 已保存配置</li>
 *   <li>hybrid 模式加权融合（vectorWeight/bm25Weight）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RetrievalServiceImplTest {

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
    void setupSecurity() {
        // 超级权限用户，跳过 KB ACL 校验（权限校验逻辑由 checkKbAccess 覆盖，此处聚焦检索逻辑）
        LoginUser user = LoginUser.builder().userId("tester").username("tester").roles(List.of("admin")).permissions(List.of("*")).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    // ---- helpers ----

    private RetrievalRequest req(String kbId, String query, RetrievalRequest.RetrievalConfig cfg) {
        RetrievalRequest req = new RetrievalRequest();
        req.setKnowledgeId(kbId);
        req.setQuery(query);
        req.setConfig(cfg);
        return req;
    }

    private RetrievalRequest.RetrievalConfig cfg() {
        return new RetrievalRequest.RetrievalConfig();
    }

    private KbChunk chunk(String id, String fileId, String content) {
        return chunkWithIndex(id, fileId, 0, content);
    }

    private KbChunk chunkWithIndex(String id, String fileId, int chunkIndex, String content) {
        KbChunk c = new KbChunk();
        c.setId(id);
        c.setKbId("kb1");
        c.setFileId(fileId);
        c.setChunkIndex(chunkIndex);
        c.setContent(content);
        return c;
    }

    private KbFile activeFile(String id) {
        KbFile f = new KbFile();
        f.setId(id);
        f.setKbId("kb1");
        f.setDeletedAt(null);
        return f;
    }

    private KbQaPair qaPair(String id, String question, String answer) {
        KbQaPair q = new KbQaPair();
        q.setId(id);
        q.setKbId("kb1");
        q.setFileId("f-qa");
        q.setQuestion(question);
        q.setAnswer(answer);
        return q;
    }

    // ---- tests ----

    /** 阈值传 0.0 时不再被忽略：低于系统默认 0.2 的低分结果应保留 */
    @Test
    void search_vector_thresholdZero_keepsLowScoreResults() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("vector");
        cfg.setTopK(10);
        cfg.setSimilarityThreshold(0.0);

        when(kbMapper.selectById("kb1")).thenReturn(null); // 无 KB 配置
        when(embeddingService.embed(any(), anyList(), any(), any()))
                .thenReturn(List.of(List.of(0.1f, 0.2f)));
        when(milvusService.search(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(Map.of("id", "c1", "score", 0.1d))); // score 0.1 < 系统默认 0.2
        when(chunkMapper.selectByIds(anyList())).thenReturn(List.of(chunk("c1", "f1", "内容1")));
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));

        List<SearchResultItem> results = service.search(req("kb1", "测试", cfg));

        assertEquals(1, results.size());
        assertEquals("内容1", results.get(0).getContent());
    }

    /** 对照：阈值 0.3 时低分结果被过滤 */
    @Test
    void search_vector_thresholdAboveScore_filtersLowScore() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("vector");
        cfg.setTopK(10);
        cfg.setSimilarityThreshold(0.3);

        when(kbMapper.selectById("kb1")).thenReturn(null);
        when(embeddingService.embed(any(), anyList(), any(), any()))
                .thenReturn(List.of(List.of(0.1f, 0.2f)));
        when(milvusService.search(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(Map.of("id", "c1", "score", 0.1d)));
        when(chunkMapper.selectByIds(anyList())).thenReturn(List.of(chunk("c1", "f1", "内容1")));
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));

        List<SearchResultItem> results = service.search(req("kb1", "测试", cfg));

        assertTrue(results.isEmpty());
    }

    /** bm25RecallCount 应作为 fulltext 候选数传入 SQL（max(topK, bm25RecallCount)） */
    @Test
    void search_fulltext_usesBm25RecallCountAsLimit() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("fulltext");
        cfg.setTopK(5);
        cfg.setBm25RecallCount(50);

        AtomicInteger capturedLimit = new AtomicInteger();
        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt())).thenAnswer(inv -> {
            capturedLimit.set(inv.getArgument(2));
            return List.of(chunk("c1", "f1", "内容1"));
        });
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));

        List<SearchResultItem> results = service.search(req("kb1", "测试", cfg));

        assertEquals(50, capturedLimit.get(), "fulltext SQL limit 应使用 bm25RecallCount");
        assertEquals(1, results.size());
    }

    /** enableKeywordMatch=true 且命中问答对时，QA 结果排在最前 */
    @Test
    void search_keywordMatch_qaResultsFirst() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("fulltext");
        cfg.setTopK(10);
        cfg.setEnableKeywordMatch(true);
        cfg.setQaRecallCount(5);

        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(chunk("c1", "f1", "普通块内容")));
        when(qaPairMapper.selectList(any())).thenReturn(List.of(qaPair("q1", "续费政策是什么", "续费政策答案")));
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));

        List<SearchResultItem> results = service.search(req("kb1", "续费政策", cfg));

        assertEquals(2, results.size());
        assertEquals("qa", results.get(0).getSource(), "关键词命中时 QA 结果应优先返回");
        assertEquals("普通块内容", results.get(1).getContent());
    }

    /** 分词模糊匹配：query "DeepSeek V4参数" 应命中 question "DeepSeek V4 Pro 参数"（字面 LIKE 匹配不到） */
    @Test
    void search_keywordMatch_tokenizedMatch_qaQuestionWithExtraWords() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("fulltext");
        cfg.setTopK(10);

        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(chunk("c1", "f1", "普通块内容")));
        when(qaPairMapper.selectList(any())).thenReturn(List.of(
                qaPair("q1", "DeepSeek V4 Pro 参数", "DeepSeek V4 Pro 参数说明")));
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));

        List<SearchResultItem> results = service.search(req("kb1", "DeepSeek V4参数", cfg));

        assertEquals(2, results.size());
        assertEquals("qa", results.get(0).getSource(), "分词模糊匹配应命中 QA 对");
        assertTrue(results.get(0).getContent().contains("DeepSeek V4 Pro 参数"));
    }

    /** enableKeywordMatch 未传时系统默认开启：命中问答对时 QA 结果优先返回 */
    @Test
    void search_keywordMatch_defaultEnabled_whenNotProvided() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("fulltext");
        cfg.setTopK(10);
        // 不传 enableKeywordMatch → 走系统默认开启

        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(chunk("c1", "f1", "普通块内容")));
        when(qaPairMapper.selectList(any())).thenReturn(List.of(qaPair("q1", "小微ICT营销6步法", "1. 问询\n2. 看")));
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));

        List<SearchResultItem> results = service.search(req("kb1", "小微ICT营销6步法", cfg));

        assertEquals(2, results.size());
        assertEquals("qa", results.get(0).getSource(), "系统默认开启关键词匹配时 QA 结果应优先返回");
    }

    /** 图谱通道默认参与：未传 enableGraphExpand 时，图谱命中结果与主检索结果 RRF 融合 */
    @Test
    void search_graphChannel_defaultEnabled_fusedIntoResults() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("fulltext");
        cfg.setTopK(10);
        // 不传 enableGraphExpand → 系统默认开启

        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(chunk("c1", "f1", "普通块内容")));
        // 图谱通道：expandGraph 命中实体 → 实体名 LIKE 召回 chunk
        when(queryEnhanceService.expandGraph(eq("kb1"), anyString(), anyInt(), anyInt(), any()))
                .thenReturn(Map.of("entities", List.of(Map.of("name", "故障根因分析")), "relations", List.of()));
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk("gc1", "gf1", "图谱命中内容")));
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));
        when(fileMapper.selectById("gf1")).thenReturn(activeFile("gf1"));

        List<SearchResultItem> results = service.search(req("kb1", "故障根因分析", cfg));

        assertEquals(2, results.size(), "图谱通道结果应参与 RRF 融合");
        assertTrue(results.stream().anyMatch(r -> "graph".equals(r.getSource())), "结果应包含图谱通道召回项");
        assertTrue(results.stream().anyMatch(r -> "普通块内容".equals(r.getContent())), "主检索结果应保留");
    }

    /** 图谱通道显式关闭时，不进行图谱召回 */
    @Test
    void search_graphChannel_disabled_skipsGraphRecall() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("fulltext");
        cfg.setTopK(10);
        cfg.setEnableGraphExpand(false);

        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(chunk("c1", "f1", "普通块内容")));
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));

        List<SearchResultItem> results = service.search(req("kb1", "测试", cfg));

        assertEquals(1, results.size());
        assertTrue(results.stream().noneMatch(r -> "graph".equals(r.getSource())), "关闭后不应有图谱结果");
    }

    /** 图谱通道向量实体匹配：文本未命中时，向量命中也能召回图谱 chunk 并融合 */
    @Test
    void search_graphChannel_vectorEntityMatch_fusedIntoResults() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("fulltext");
        cfg.setTopK(10);

        // KB 配置了 embedding 模型 → 向量路径可用
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId("kb1");
        kb.setEmbeddingModel("BAAI/bge-m3");
        when(kbMapper.selectById("kb1")).thenReturn(kb);
        ModelRecord mr = new ModelRecord();
        mr.setCode("BAAI/bge-m3");
        mr.setStatus("online");
        mr.setApiUrl("http://embedding");
        mr.setApiKeyRef("key");
        when(modelRecordMapper.selectOne(any())).thenReturn(mr);

        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(chunk("c1", "f1", "普通块内容")));
        // 文本路径无命中，向量路径命中实体
        when(queryEnhanceService.expandGraph(any(), anyString(), anyInt(), anyInt(), any()))
                .thenReturn(Map.of("entities", List.of(), "relations", List.of()));
        when(embeddingService.embed(any(), anyList(), any(), any()))
                .thenReturn(List.of(List.of(0.1f, 0.2f)));
        when(graphStore.searchEntitiesByVector(eq("kb1"), anyList(), anyInt()))
                .thenReturn(List.of(Map.of("name", "向量命中实体", "entity_type", "类型", "score", 0.9d)));
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk("gc1", "gf1", "图谱向量命中内容")));
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));
        when(fileMapper.selectById("gf1")).thenReturn(activeFile("gf1"));

        List<SearchResultItem> results = service.search(req("kb1", "测试向量", cfg));

        assertEquals(2, results.size(), "向量命中的图谱结果应参与融合");
        assertTrue(results.stream().anyMatch(r -> "graph".equals(r.getSource())), "结果应包含图谱向量召回项");
        // RRF 融合只排序不覆盖相似度：向量路原始 cosine 相似度应保留（而非 RRF 排名分 ~0.016）
        assertTrue(results.stream()
                        .filter(r -> "普通块内容".equals(r.getContent()))
                        .allMatch(r -> r.getSimilarity() > 0.9),
                "RRF 融合不应覆盖原始相似度，fulltext 首条 similarity 应保留 1.0");
    }

    /** R1：文本实体名过短（n-gram 泛词如"流程/包含"）不参与 LIKE 召回，避免噪音 */
    @Test
    void search_graphChannel_shortTextEntityName_filteredOut() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("fulltext");
        cfg.setTopK(10);

        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(chunk("c1", "f1", "普通块内容")));
        // 文本实体只有 2 字泛词，且无向量实体 → 图谱通道应跳过 LIKE 召回
        when(queryEnhanceService.expandGraph(any(), anyString(), anyInt(), anyInt(), any()))
                .thenReturn(Map.of("entities", List.of(Map.of("name", "流程")), "relations", List.of()));
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));

        List<SearchResultItem> results = service.search(req("kb1", "测试", cfg));

        assertEquals(1, results.size(), "2 字泛词实体不应触发图谱 LIKE 召回");
        assertTrue(results.stream().noneMatch(r -> "graph".equals(r.getSource())));
    }

    /** C4：window 组装模式下，图谱通道命中的 chunk 触发前后邻居组装（图谱结果进 context） */
    @Test
    void search_contextWindow_graphResultsTriggerNeighborAssembly() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("fulltext");
        cfg.setTopK(10);
        cfg.setContextAssemblyStrategy("window");
        cfg.setContextWindowSize(1);

        // 主检索命中 f1/index1
        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(chunkWithIndex("c1", "f1", 1, "命中内容1")));
        // 图谱通道命中 f1/index2（图谱 chunk 携带真实 fileId/chunkIndex）
        when(queryEnhanceService.expandGraph(any(), anyString(), anyInt(), anyInt(), any()))
                .thenReturn(Map.of("entities", List.of(Map.of("name", "实体X")), "relations", List.of()));
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunkWithIndex("gc1", "f1", 2, "图谱命中内容")));
        // window 组装：f1 共 4 个 chunk（index 0..3）
        when(chunkMapper.selectByFileId("f1")).thenReturn(List.of(
                chunkWithIndex("c0", "f1", 0, "邻居0"),
                chunkWithIndex("c1", "f1", 1, "命中内容1"),
                chunkWithIndex("gc1", "f1", 2, "图谱命中内容"),
                chunkWithIndex("c3", "f1", 3, "邻居3")));
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));

        List<SearchResultItem> results = service.search(req("kb1", "测试", cfg));

        // 融合后命中 index1 + index2 → window 展开出 index0..3，图谱命中内容及其邻居都在 context 中
        assertTrue(results.stream().anyMatch(r -> "图谱命中内容".equals(r.getContent())), "图谱命中应进入 context");
        assertTrue(results.stream().anyMatch(r -> "邻居0".equals(r.getContent())), "图谱命中的前邻居应进入 context");
        assertTrue(results.stream().anyMatch(r -> "邻居3".equals(r.getContent())), "图谱命中的后邻居应进入 context");
    }

    /** 请求未传 mode/topK 时，KB 已保存配置生效（不被 DTO 默认值覆盖） */
    @Test
    void search_requestWithoutMode_keepsKbSavedConfig() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId("kb1");
        kb.setRetrievalConfig("{\"mode\":\"fulltext\",\"topK\":3}");
        when(kbMapper.selectById("kb1")).thenReturn(kb);

        AtomicInteger capturedLimit = new AtomicInteger();
        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt())).thenAnswer(inv -> {
            capturedLimit.set(inv.getArgument(2));
            return List.of(chunk("c1", "f1", "内容1"));
        });
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));

        // 请求不传 config（null）→ 应走 KB 保存的 fulltext + topK=3
        List<SearchResultItem> results = service.search(req("kb1", "测试", null));

        assertEquals(3, capturedLimit.get(), "topK 应取 KB 保存值 3 而非默认 10");
        verify(milvusService, never()).search(any(), any(), anyInt());
        assertEquals(1, results.size());
    }

    /** hybrid 模式走加权融合：vector 与 fulltext 两路结果都进入最终结果 */
    @Test
    void search_hybrid_weightedFusion_keepsBothChannels() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("hybrid");
        cfg.setTopK(10);

        when(kbMapper.selectById("kb1")).thenReturn(null);
        when(embeddingService.embed(any(), anyList(), any(), any()))
                .thenReturn(List.of(List.of(0.1f, 0.2f)));
        when(milvusService.search(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(Map.of("id", "c1", "score", 0.9d)));
        when(chunkMapper.selectByIds(anyList())).thenReturn(List.of(chunk("c1", "f1", "向量路内容")));
        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(chunk("c2", "f2", "全文路内容")));
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));
        when(fileMapper.selectById("f2")).thenReturn(activeFile("f2"));

        List<SearchResultItem> results = service.search(req("kb1", "测试", cfg));

        assertEquals(2, results.size(), "hybrid 加权融合后两路结果都应保留");
        List<String> contents = results.stream().map(SearchResultItem::getContent).toList();
        assertTrue(contents.contains("向量路内容"));
        assertTrue(contents.contains("全文路内容"));
    }

    // ========================================================================
    //  P0 线A：上下文 auto 自动降级链 + 条款/标题线索召回（分册一）
    // ========================================================================

    /** auto 默认策略：命中子分片（parentId 非空）时返回父块全文（L1 parent_chunk） */
    @Test
    void search_contextAuto_parentAssembled() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("fulltext");
        cfg.setTopK(10);
        cfg.setContextAssemblyStrategy("auto");

        // 主检索命中 f1/index1，且该 chunk 是父子分片的子片（parentId 指向父块）
        KbChunk child = chunkWithIndex("c1", "f1", 1, "子分片内容");
        child.setParentId("f1_parent_0");
        child.setChunkType("text");
        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(child));
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));
        // 父块查询返回完整条款上下文
        when(chunkMapper.selectById("f1_parent_0")).thenReturn(
                chunkWithIndex("f1_parent_0", "f1", 0, "【完整父块】本办法所称电力业务许可，包括：\n(一)发电许可\n(二)输电许可\n(三)售电许可"));

        List<SearchResultItem> results = service.search(req("kb1", "电力业务许可包括哪些", cfg));

        assertEquals(1, results.size());
        assertTrue(results.get(0).getContent().contains("完整父块"),
                "auto 策略下命中子分片应返回父块完整上下文");
        assertEquals("parent", results.get(0).getChunkType());
    }

    /** auto 默认策略：无父块（单层分片）时降级 window 取邻居（L2） */
    @Test
    void search_contextAuto_noParent_usesWindowNeighbors() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("fulltext");
        cfg.setTopK(10);
        cfg.setContextAssemblyStrategy("auto");
        cfg.setContextWindowSize(1);

        // 命中块无 parentId（单层分片）→ L2 window
        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(chunkWithIndex("c1", "f1", 1, "命中内容1")));
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));
        when(chunkMapper.selectByFileId("f1")).thenReturn(List.of(
                chunkWithIndex("c0", "f1", 0, "邻居0"),
                chunkWithIndex("c1", "f1", 1, "命中内容1"),
                chunkWithIndex("c2", "f1", 2, "邻居2"),
                chunkWithIndex("c3", "f1", 3, "邻居3")));

        List<SearchResultItem> results = service.search(req("kb1", "测试", cfg));

        assertTrue(results.stream().anyMatch(r -> "邻居0".equals(r.getContent())), "L2 window 应带回前邻居");
        assertTrue(results.stream().anyMatch(r -> "邻居2".equals(r.getContent())), "L2 window 应带回后邻居");
        assertTrue(results.stream().noneMatch(r -> "邻居3".equals(r.getContent())), "windowSize=1 不应带回 2 跳外邻居");
    }

    /** auto 默认策略：无法定位文件（无 fileId）时降级 concat 返回自身（L3） */
    @Test
    void search_contextAuto_noFileId_fallsBackToConcat() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("fulltext");
        cfg.setTopK(10);
        cfg.setContextAssemblyStrategy("auto");
        cfg.setEnableGraphExpand(false); // 关闭图谱，避免额外通道干扰
        cfg.setEnableKeywordMatch(false); // 关闭 QA，避免额外通道干扰

        // 命中块 fileId 为 null（如父文档组装产出的纯内容条目），无法取邻居
        KbChunk noFile = chunkWithIndex("c1", null, 0, "无文件内容");
        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(noFile));

        List<SearchResultItem> results = service.search(req("kb1", "测试", cfg));

        assertEquals(1, results.size());
        assertEquals("无文件内容", results.get(0).getContent(), "无法定位文件时应原样返回自身");
    }

    /** 条款线索召回：query 含"第三条"时，条款锚点 + 相邻条款进入结果（enableClauseRecall 默认开） */
    @Test
    void search_clauseRecall_clauseMention_recallsAnchorsAndNeighbors() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("fulltext");
        cfg.setTopK(10);
        cfg.setEnableClauseRecall(true);
        cfg.setClauseWindowSize(1);
        cfg.setEnableGraphExpand(false);

        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(chunk("c1", "f1", "条款相关内容")));
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));

        // 条款锚点：含"第三条"的 chunk
        KbChunk anchor = chunkWithIndex("ca", "f2", 2, "第三条 电力业务许可的适用范围");
        anchor.setKbId("kb1");
        // 条款锚点 LIKE 查询
        when(chunkMapper.selectList(any())).thenReturn(List.of(anchor));
        when(chunkMapper.selectByFileId("f2")).thenReturn(List.of(
                chunkWithIndex("cn1", "f2", 1, "第二条 定义"),
                chunkWithIndex("ca", "f2", 2, "第三条 电力业务许可的适用范围"),
                chunkWithIndex("cn3", "f2", 3, "第四条 申请材料")));
        when(fileMapper.selectById("f2")).thenReturn(activeFile("f2"));

        List<SearchResultItem> results = service.search(req("kb1", "第三条的适用范围是什么", cfg));

        assertTrue(results.stream().anyMatch(r -> r.getContent().contains("第三条")),
                "条款锚点应被召回");
        assertTrue(results.stream().anyMatch(r -> r.getContent().contains("第二条")),
                "条款锚点前驱（clauseWindowSize=1）应被召回");
        assertTrue(results.stream().anyMatch(r -> r.getContent().contains("第四条")),
                "条款锚点后继（clauseWindowSize=1）应被召回");
    }

    /** 条款线索召回显式关闭时，不触发条款/标题召回（回归） */
    @Test
    void search_clauseRecall_disabled_skipsClauseRecall() {
        RetrievalRequest.RetrievalConfig cfg = cfg();
        cfg.setMode("fulltext");
        cfg.setTopK(10);
        cfg.setEnableClauseRecall(false);
        cfg.setEnableGraphExpand(false);
        cfg.setEnableKeywordMatch(false);

        when(chunkMapper.fulltextSearch(eq("kb1"), anyString(), anyInt()))
                .thenReturn(List.of(chunk("c1", "f1", "普通内容")));
        when(fileMapper.selectById("f1")).thenReturn(activeFile("f1"));

        List<SearchResultItem> results = service.search(req("kb1", "第三条", cfg));

        assertEquals(1, results.size(), "关闭条款召回时不应增加召回项");
    }
}
