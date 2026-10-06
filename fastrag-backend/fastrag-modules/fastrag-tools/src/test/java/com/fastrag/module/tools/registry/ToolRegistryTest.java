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
}
