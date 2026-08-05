package com.fastrag.module.retrieval.service.impl;

import com.fastrag.ai.llm.LlmService;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.platform.service.TermService;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 图谱扩展降级分词的生效性测试：
 * 未配置 NER 模型时，中文 query 应按 n-gram 切分传入图谱模糊匹配，
 * 保证 "如何做故障根因分析" 能切出 "故障/根因/分析" 等可命中 "故障根因分析（RCA）" 的 token。
 */
@ExtendWith(MockitoExtension.class)
class QueryEnhanceServiceImplTest {

    @Mock private GraphStore graphStore;
    @Mock private LlmService llmService;
    @Mock private ModelRecordMapper modelRecordMapper;
    @Mock private TermService termService;

    @InjectMocks private QueryEnhanceServiceImpl service;

    @BeforeEach
    void setupSecurity() {
        // 超级权限用户，跳过 KB ACL 校验
        LoginUser user = LoginUser.builder().userId("tester").username("tester").roles(List.of("admin")).permissions(List.of("*")).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    void expandGraph_noNerModel_ngramTokensReachGraphStore() {
        // 捕获传给图谱的实体 token 列表
        List<String>[] captured = new List[1];
        when(graphStore.expandGraph(eq("kb1"), anyList(), eq(2), eq(20))).thenAnswer(inv -> {
            captured[0] = inv.getArgument(1);
            return Map.of("entities", List.of(), "relations", List.of());
        });

        // nerModel 为 null → 走降级分词
        service.expandGraph("kb1", "如何做故障根因分析 问题 报修", 2, 20, null);

        assertTrue(captured[0].contains("故障"), "应切出 2-gram「故障」");
        assertTrue(captured[0].contains("根因"), "应切出 2-gram「根因」");
        assertTrue(captured[0].contains("分析"), "应切出 2-gram「分析」");
        assertTrue(captured[0].contains("问题"), "短中文块应原样保留");
        assertTrue(captured[0].contains("报修"), "短中文块应原样保留");
        assertTrue(captured[0].contains("如何做故障根因分析"), "原始长块应保留（供 CONTAINS 双向匹配）");
        assertFalse(captured[0].isEmpty());
    }

    @Test
    void expandGraph_mixedQuery_keepsEnglishWords() {
        List<String>[] captured = new List[1];
        when(graphStore.expandGraph(eq("kb1"), anyList(), eq(2), eq(20))).thenAnswer(inv -> {
            captured[0] = inv.getArgument(1);
            return Map.of("entities", List.of(), "relations", List.of());
        });

        service.expandGraph("kb1", "DeepSeek 参数说明", 2, 20, null);

        assertTrue(captured[0].contains("DeepSeek"), "英文词应按整词保留");
        assertTrue(captured[0].contains("参数说明"), "短中文块原样保留");
    }
}
