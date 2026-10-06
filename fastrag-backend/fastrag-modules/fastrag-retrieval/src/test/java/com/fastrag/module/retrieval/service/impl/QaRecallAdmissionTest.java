package com.fastrag.module.retrieval.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.ai.rerank.RerankService;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbQaPair;
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
import com.fastrag.module.publish.mapper.KbLogMapper;
import com.fastrag.security.filter.LoginUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 召回准入测试（tdd/qa-generation.md S4 切片，ADR-0005 的可观察面）。
 *
 * <p>准入规则 `source='manual' OR status='confirmed'` 由 SQL 条件承载（contract/interfaces.md
 * `## qa-generation` 变更点登记）——mock mapper 无法执行 SQL，故直接断言传入 selectList 的
 * wrapper SQL 段与参数（TableInfo 手工初始化使 lambda 可解析）；三分支的行级行为依赖真库，
 * 列 implement 验收清单。既有 qa 通道语义（前置返回/分词命中/降级）由 RetrievalServiceImplTest 回归。
 */
@ExtendWith(MockitoExtension.class)
class QaRecallAdmissionTest {

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

    @BeforeAll
    static void initMpMeta() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), KbQaPair.class);
    }

    @BeforeEach
    void setupSecurity() {
        LoginUser user = LoginUser.builder().userId("tester").username("tester")
                .roles(List.of("admin")).permissions(List.of("*")).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private RetrievalRequest keywordRequest() {
        RetrievalRequest req = new RetrievalRequest();
        req.setKnowledgeId("kb1");
        req.setQuery("续费政策");
        RetrievalRequest.RetrievalConfig cfg = new RetrievalRequest.RetrievalConfig();
        cfg.setEnableKeywordMatch(true);
        cfg.setQaRecallCount(5);
        req.setConfig(cfg);
        return req;
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<KbQaPair> capturedWrapper() {
        when(qaPairMapper.selectList(any())).thenReturn(List.of());
        service.search(keywordRequest());
        ArgumentCaptor<LambdaQueryWrapper<KbQaPair>> cap =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(qaPairMapper).selectList(cap.capture());
        return cap.getValue();
    }

    @Test
    void 准入_wrapper条件含manual或confirmed() {
        LambdaQueryWrapper<KbQaPair> wrapper = capturedWrapper();
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("source"), "准入规则须过滤 source 列");
        assertTrue(sql.contains("status"), "准入规则须过滤 status 列");
    }

    @Test
    void 准入_参数含manual与confirmed两个合法值() {
        LambdaQueryWrapper<KbQaPair> wrapper = capturedWrapper();
        wrapper.getSqlSegment(); // 强制合并嵌套条件的参数
        Collection<Object> params = wrapper.getParamNameValuePairs().values();
        assertTrue(params.contains("manual"), "manual 来源恒可召回");
        assertTrue(params.contains("confirmed"), "ai 来源须确认后才召回");
    }

    @Test
    void 召回_确认后的ai对经通道返回时_source为qa() {
        // 行为级冒烟：DB 侧按准入条件返回 confirmed 对后，通道投影（source=qa、Q/A 拼接）不变
        KbQaPair confirmed = new KbQaPair();
        confirmed.setId("q1");
        confirmed.setKbId("kb1");
        confirmed.setFileId("f-qa");
        confirmed.setQuestion("续费政策是什么");
        confirmed.setAnswer("续费政策答案");
        confirmed.setSource("ai");
        confirmed.setStatus("confirmed");
        when(qaPairMapper.selectList(any())).thenReturn(List.of(confirmed));

        List<SearchResultItem> results = service.search(keywordRequest());

        assertTrue(results.size() >= 1);
        assertEquals("qa", results.get(0).getSource());
        assertTrue(results.get(0).getContent().contains("续费政策答案"));
    }
}
