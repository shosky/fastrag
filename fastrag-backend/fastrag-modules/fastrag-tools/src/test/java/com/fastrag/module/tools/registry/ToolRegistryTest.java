package com.fastrag.module.tools.registry;

import com.fastrag.ai.model.ChatRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QF-6（idd-review 快修）：toOpenAITools 按 function name 稳定排序。
 * 消费路径（LLM function calling）对工具顺序敏感，乱序输入也应产出升序稳定输出。
 */
@ExtendWith(MockitoExtension.class)
class ToolRegistryTest {

    @InjectMocks
    private ToolRegistry registry;

    private static ToolDefinition def(String name) {
        ToolDefinition def = new ToolDefinition();
        def.setToolId(name);
        def.setName(name);
        def.setDescription(name + " desc");
        def.setType("builtin");
        return def;
    }

    @Test
    void toOpenAITools_sortsByFunctionNameAscending() {
        List<ChatRequest.ToolDefinition> tools =
                registry.toOpenAITools(new ArrayList<>(List.of(
                        def("web_search"), def("install_skill"), def("query_kb"))));

        assertThat(tools).extracting(t -> t.getFunction().getName())
                .containsExactly("install_skill", "query_kb", "web_search");
    }

    @Test
    void toOpenAITools_shuffledInput_yieldsSameStableOrder() {
        List<ChatRequest.ToolDefinition> first =
                registry.toOpenAITools(new ArrayList<>(List.of(
                        def("c_tool"), def("a_tool"), def("b_tool"))));
        List<ChatRequest.ToolDefinition> second =
                registry.toOpenAITools(new ArrayList<>(List.of(
                        def("b_tool"), def("c_tool"), def("a_tool"))));

        assertThat(first).extracting(t -> t.getFunction().getName())
                .containsExactly("a_tool", "b_tool", "c_tool");
        assertThat(second).extracting(t -> t.getFunction().getName())
                .containsExactly("a_tool", "b_tool", "c_tool");
    }

    @Test
    void toOpenAITools_preservesDefinitionContent() {
        List<ChatRequest.ToolDefinition> tools =
                registry.toOpenAITools(new ArrayList<>(List.of(def("query_kb"))));

        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).getType()).isEqualTo("function");
        assertThat(tools.get(0).getFunction().getDescription()).isEqualTo("query_kb desc");
        assertThat(tools.get(0).getFunction().getParameters()).containsKey("type");
    }

    // ===== I4（P1-4，finding gap-4）：同名工具 first-wins 归并（统一出口，覆盖中间件追加路径） =====

    @Test
    void toOpenAITools_duplicateNames_firstWins_dropsLaterDuplicates() {
        ToolDefinition first = def("dup_tool");
        first.setDescription("first version");
        ToolDefinition second = def("dup_tool");
        second.setDescription("second version");

        List<ChatRequest.ToolDefinition> tools = registry.toOpenAITools(
                new ArrayList<>(List.of(first, def("z_tool"), second)));

        assertThat(tools).extracting(t -> t.getFunction().getName())
                .containsExactly("dup_tool", "z_tool");
        assertThat(tools.get(0).getFunction().getDescription()).isEqualTo("first version");
    }

    @Test
    void toOpenAITools_middlewareAppendedDuplicates_alsoMergedAtSingleExit() {
        // 模拟中间件向 tools 列表追加同名工具（resolveTools 后追加路径不改，统一出口归并）
        List<ToolDefinition> tools = new ArrayList<>(List.of(def("read_file")));
        tools.add(def("read_file")); // 中间件重复追加

        List<ChatRequest.ToolDefinition> openai = registry.toOpenAITools(tools);

        assertThat(openai).extracting(t -> t.getFunction().getName())
                .containsExactly("read_file");
    }
}
