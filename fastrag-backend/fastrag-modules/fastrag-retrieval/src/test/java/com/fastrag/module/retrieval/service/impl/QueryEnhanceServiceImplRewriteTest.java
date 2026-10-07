package com.fastrag.module.retrieval.service.impl;

import com.fastrag.ai.llm.LlmService;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.module.platform.entity.TermLibrary;
import com.fastrag.module.platform.entity.TermRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.platform.service.TermService;
import com.fastrag.security.filter.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * R6（P2-6）查询改写双 stub 最小接线：
 * <ul>
 *   <li>R6-2 suggest 并入 expandQueries 统一实现——复用同一 LLM 变体改写机制，
 *       取首个与原查询不同的变体作为建议查询；LLM 失败降级为原查询（reason 空）</li>
 *   <li>R6-3 query_rule 最小接线（applyQueryRules 正则级改写）——
 *       全角→半角归一、空白归一、术语别名→规范名（术语库）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class QueryEnhanceServiceImplRewriteTest {

    @Mock private GraphStore graphStore;
    @Mock private LlmService llmService;
    @Mock private ModelRecordMapper modelRecordMapper;
    @Mock private TermService termService;
    @Mock private StringRedisTemplate redisTemplate;

    @InjectMocks private QueryEnhanceServiceImpl service;

    @BeforeEach
    void setupSecurity() {
        LoginUser user = LoginUser.builder().userId("tester").username("tester")
                .roles(List.of("admin")).permissions(List.of("*")).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    // ==================== R6-2：suggest 并入 expandQueries 统一实现 ====================

    @Test
    void suggest_llmReturnsVariant_returnsVariantAsSuggestedQuery() {
        when(llmService.chat(eq("default"), anyString(), any(), any()))
                .thenReturn("企业微信怎么配置单点登录");

        Map<String, Object> r = service.suggest("企微怎么配置sso");

        assertEquals("企业微信怎么配置单点登录", r.get("suggestedQuery"),
                "suggest 应复用 expandQueries 的 LLM 变体改写，返回首个不同变体");
        assertFalse(String.valueOf(r.get("reason")).isEmpty(), "有建议时应给出 reason");
    }

    @Test
    void suggest_llmUnavailable_returnsOriginalQueryWithEmptyReason() {
        when(llmService.chat(eq("default"), anyString(), any(), any()))
                .thenThrow(new RuntimeException("gateway down"));

        Map<String, Object> r = service.suggest("原始查询");

        assertEquals("原始查询", r.get("suggestedQuery"), "LLM 不可用时降级为原查询");
        assertEquals("", r.get("reason"), "降级时 reason 为空（无建议）");
    }

    @Test
    void suggest_blankQuery_returnsQueryAsIs() {
        Map<String, Object> r = service.suggest("");
        assertEquals("", r.get("suggestedQuery"));
        assertEquals("", r.get("reason"));
    }

    // ==================== R6-3：query_rule 最小接线（正则级改写） ====================

    @Test
    void applyQueryRules_fullWidthChars_normalizedToHalfWidth() {
        Map<String, Object> r = service.applyQueryRules("ＲＡＧ 检索测试");

        assertEquals("RAG 检索测试", r.get("rewritten"), "全角 ASCII 应归一为半角");
        assertTrue(((List<?>) r.get("appliedRules")).contains("halfwidth"));
    }

    @Test
    void applyQueryRules_termAlias_replacedWithCanonicalTerm() {
        TermLibrary lib = new TermLibrary();
        lib.setId("lib1");
        when(termService.listLibraries()).thenReturn(List.of(lib));
        TermRecord tr = new TermRecord();
        tr.setTerm("RAG");
        tr.setAlias("检索增强生成，检索增强");
        tr.setStatus(1);
        when(termService.listTerms("lib1")).thenReturn(List.of(tr));

        Map<String, Object> r = service.applyQueryRules("什么是检索增强生成");

        assertEquals("什么是RAG", r.get("rewritten"), "别名应被正则级替换为规范术语名");
        assertTrue(((List<?>) r.get("appliedRules")).contains("term_alias"));
    }

    @Test
    void applyQueryRules_longerAliasFirst_noNestedPartialReplace() {
        // 别名 "检索增强生成" 含子串别名 "检索增强"：应先替换更长者，避免嵌套部分替换
        TermLibrary lib = new TermLibrary();
        lib.setId("lib1");
        when(termService.listLibraries()).thenReturn(List.of(lib));
        TermRecord tr = new TermRecord();
        tr.setTerm("RAG");
        tr.setAlias("检索增强,检索增强生成");
        tr.setStatus(1);
        when(termService.listTerms("lib1")).thenReturn(List.of(tr));

        Map<String, Object> r = service.applyQueryRules("检索增强生成技术");

        assertEquals("RAG技术", r.get("rewritten"), "长别名优先替换，短别名不再命中已替换文本");
    }

    @Test
    void applyQueryRules_plainQuery_unchangedWithNoRules() {
        Map<String, Object> r = service.applyQueryRules("正常查询");

        assertEquals("正常查询", r.get("rewritten"));
        assertTrue(((List<?>) r.get("appliedRules")).isEmpty(), "无规则命中时 appliedRules 为空");
    }

    @Test
    void applyQueryRules_blankQuery_returnedAsIs() {
        Map<String, Object> r = service.applyQueryRules("  ");
        assertEquals("  ", r.get("rewritten"), "空白查询原样返回（不产生改写）");
        assertTrue(((List<?>) r.get("appliedRules")).isEmpty());
    }
}
