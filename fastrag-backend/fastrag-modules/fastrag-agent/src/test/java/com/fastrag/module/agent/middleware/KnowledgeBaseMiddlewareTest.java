package com.fastrag.module.agent.middleware;

import com.fastrag.ai.model.ChatMessage;
import com.fastrag.common.service.KbOperationService;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.tools.executor.KnowledgeToolExecutor;
import com.fastrag.module.tools.executor.ToolContext;
import com.fastrag.module.tools.executor.ToolResult;
import com.fastrag.module.tools.registry.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 知识库中间件回归（2026-10-19 诊断：绑定知识库的应用对话不检索）。
 *
 * <p>钉住三条契约：</p>
 * <ul>
 *   <li>注册 schema 与执行器参数名一致（历史缺陷：schema 给 knowledgeId/topK，
 *       执行器读 kb_id/top_k——schema 规范调用必然「缺少参数」失败，模型即使想检索也永远失败）</li>
 *   <li>kb_id 缺省回落绑定知识库（config.kbIds），模型无需预知库 ID</li>
 *   <li>能力提示为强门控（必查 + [cN] 引用口径）；无实现的 find_kb_document 不再注册</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseMiddlewareTest {

    @Mock
    private KbOperationService kbOperationService;

    private List<ToolDefinition> run(List<String> kbIds, List<ChatMessage> messages) {
        BaseContext context = new BaseContext();
        context.setKnowledges(new ArrayList<>(kbIds));
        List<ToolDefinition> tools = new ArrayList<>();
        new KnowledgeBaseMiddleware().beforeModelCall(context, messages, tools);
        return tools;
    }

    private ToolDefinition findQueryKb(List<ToolDefinition> tools) {
        return tools.stream().filter(t -> "query_kb".equals(t.getName())).findFirst().orElse(null);
    }

    @Test
    void registersQueryKb_schemaAlignedWithExecutorContract() {
        List<ChatMessage> messages = new ArrayList<>(List.of(new ChatMessage("system", "base")));
        List<ToolDefinition> tools = run(List.of("kb-1"), messages);

        ToolDefinition def = findQueryKb(tools);
        assertNotNull(def, "绑定知识库时必须注册 query_kb");

        Map<String, Object> properties = castMap(def.getInputSchema().get("properties"));
        assertTrue(properties.containsKey("query"));
        assertTrue(properties.containsKey("top_k"), "top_k 参数名必须与执行器读取的 top_k 一致");
        assertFalse(properties.containsKey("knowledgeId"), "历史失配参数名不得再出现");
        assertFalse(properties.containsKey("topK"));
        // 2026-10-20：单库绑定不暴露 kb_id——8B 级模型面对可选参数会「填空」传空串，不如不给参数位
        assertFalse(properties.containsKey("kb_id"), "单库绑定下 schema 不得暴露 kb_id（杜绝空串填空）");
        assertFalse(properties.containsKey("knowledgeId"));

        assertEquals(List.of("kb-1"), def.getConfig().get("kbIds"), "绑定库应烙进 config 供执行器缺省回落");
    }

    @Test
    void registersQueryKb_multiKb_exposesKbIdForDisambiguation() {
        List<ChatMessage> messages = new ArrayList<>(List.of(new ChatMessage("system", "base")));
        List<ToolDefinition> tools = run(List.of("kb-1", "kb-2"), messages);

        ToolDefinition def = findQueryKb(tools);
        assertNotNull(def);

        Map<String, Object> properties = castMap(def.getInputSchema().get("properties"));
        assertTrue(properties.containsKey("kb_id"), "多库绑定应暴露 kb_id 供模型指定检索范围");
        assertEquals(List.of("kb-1", "kb-2"), def.getConfig().get("kbIds"));
    }

    @Test
    void queryKb_schemaFaithfulCall_executesEndToEnd() {
        when(kbOperationService.queryKnowledgeBase(eq("kb-1"), eq("什么是超限车辆"), anyInt()))
            .thenReturn(List.of(Map.of("content", "超限车辆是指超过限载限宽限高标准的车辆", "score", 0.9,
                    "source", "治超条例.pdf")));

        List<ChatMessage> messages = new ArrayList<>(List.of(new ChatMessage("system", "base")));
        ToolDefinition def = findQueryKb(run(List.of("kb-1"), messages));
        assertNotNull(def);

        // 严格按注册 schema 的最小调用形态：只有必填 query（schema 声明 kb_id 可选）
        KnowledgeToolExecutor executor = new KnowledgeToolExecutor(kbOperationService);
        ToolResult result = executor.execute(def, Map.of("query", "什么是超限车辆"), new ToolContext());

        assertTrue(result.isSuccess(),
                "schema 规范调用必须能成功执行（回归：历史 schema 失配导致必然「缺少参数」失败）");
        assertTrue(result.getOutput().contains("超过限载限宽限高标准"));
    }

    @Test
    void capabilityHint_isForcedGate_withCnCitation() {
        List<ChatMessage> messages = new ArrayList<>(List.of(new ChatMessage("system", "base prompt")));
        run(List.of("kb-1"), messages);

        String system = messages.get(0).getContent();
        assertTrue(system.startsWith("base prompt"), "原有 system 提示词保留");
        assertTrue(system.contains("必须先调用 query_kb"), "能力提示必须是强门控（弱模型不得自判豁免）");
        assertTrue(system.contains("[cN]"), "引用口径统一到执行器的 [cN] 编号");
        assertFalse(system.contains("[文件名]"), "不得再教模型输出 [文件名] 占位符（幻觉引用源头）");
    }

    @Test
    void findKbDocument_notRegistered() {
        List<ToolDefinition> tools = run(List.of("kb-1"),
                new ArrayList<>(List.of(new ChatMessage("system", "b"))));

        assertTrue(tools.stream().noneMatch(t -> "find_kb_document".equals(t.getName())),
                "find_kb_document 无执行器实现，注册即必失败，不得暴露给模型");
        assertEquals(2, tools.size());
    }

    @Test
    void noKnowledges_noToolsNoHint() {
        List<ChatMessage> messages = new ArrayList<>(List.of(new ChatMessage("system", "untouched")));
        List<ToolDefinition> tools = run(List.of(), messages);

        assertTrue(tools.isEmpty());
        assertEquals("untouched", messages.get(0).getContent());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }
}
