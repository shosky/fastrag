package com.fastrag.module.retrieval.service.impl;

import com.fastrag.ai.llm.LlmService;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.platform.service.TermService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * P0 回归（2026-10-09 检索事故）：LLM 网关失败时 doChatInternal 不抛异常而是返回
 * 错误文案，该文案曾被 expandQueries/suggest 当作"改写变体/建议查询"采纳，
 * 整条替换真实检索 query，导致 11 条无关 image 分片顶掉目标文本分片。
 *
 * <p>防线：expandQueries 源头识别失败文案降级为原查询；suggest 再拦一次。
 * 本测试锁定该事故模式：mock LLM 返回错误文案，断言 query 不被替换。</p>
 */
@ExtendWith(MockitoExtension.class)
class QueryEnhanceServiceImplLlmFailureGuardTest {

    /** 事故现场的真实错误文案（ai.gateway.url 指向未启动的本地 Ollama） */
    private static final String GATEWAY_FAILURE_TEXT =
            "模型调用失败: Connection refused: no further information: /127.0.0.1:11434";
    private static final String QUERY = "小微ICT营销6步法";

    @Mock private GraphStore graphStore;
    @Mock private LlmService llmService;
    @Mock private ModelRecordMapper modelRecordMapper;
    @Mock private TermService termService;
    @Mock private StringRedisTemplate redisTemplate;

    @InjectMocks private QueryEnhanceServiceImpl service;

    @Test
    void expandQueries_llmReturnsGatewayFailureText_fallsBackToOriginalQueryOnly() {
        when(llmService.chat(eq("default"), anyString(), any(), any()))
                .thenReturn(GATEWAY_FAILURE_TEXT);

        List<String> variants = service.expandQueries(QUERY, 3, null, null);

        assertEquals(List.of(QUERY), variants,
                "网关失败文案不得成为改写变体，必须降级为仅原查询");
    }

    @Test
    void expandQueries_llmReturnsEmptyResponsePlaceholder_fallsBackToOriginalQueryOnly() {
        when(llmService.chat(eq("default"), anyString(), any(), any()))
                .thenReturn(LlmService.EMPTY_RESPONSE_TEXT);

        List<String> variants = service.expandQueries(QUERY, 3, null, null);

        assertEquals(List.of(QUERY), variants,
                "空响应占位文案与失败文案同属'非模型正文'，同样不得成为变体");
    }

    @Test
    void suggest_llmReturnsGatewayFailureText_suggestsOriginalQuery() {
        when(llmService.chat(eq("default"), anyString(), any(), any()))
                .thenReturn(GATEWAY_FAILURE_TEXT);

        Map<String, Object> r = service.suggest(QUERY);

        assertEquals(QUERY, r.get("suggestedQuery"),
                "错误文案顶替建议查询即本次事故根因，suggest 必须返回原查询");
        assertEquals("", r.get("reason"), "降级时 reason 为空（无建议）");
    }

    @Test
    void suggest_withExplicitModel_passesModelThroughToLlmCall() {
        when(llmService.chat(eq("deepseek-v3"), anyString(), any(), any()))
                .thenReturn(GATEWAY_FAILURE_TEXT);

        Map<String, Object> r = service.suggest(QUERY, "deepseek-v3");

        assertEquals(QUERY, r.get("suggestedQuery"),
                "指定模型同样走失败防线；且模型 code 应透传到 LLM 调用（等价类：防线不因模型不同而失效）");
    }

    @Test
    void llmService_failureTextDetector_coversIncidentShapes() {
        assertTrue(LlmService.isFailureText(GATEWAY_FAILURE_TEXT), "网关失败文案");
        assertTrue(LlmService.isFailureText(LlmService.EMPTY_RESPONSE_TEXT), "空响应占位");
        assertTrue(LlmService.isFailureText(null), "null 视为失败");
        assertTrue(LlmService.isFailureText("  "), "空白视为失败");
        assertTrue(!LlmService.isFailureText("小微ICT业务营销六步法"), "正常模型正文不得误判");
    }
}
