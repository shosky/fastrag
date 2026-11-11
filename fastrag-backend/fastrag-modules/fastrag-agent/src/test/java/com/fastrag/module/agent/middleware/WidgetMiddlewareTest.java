package com.fastrag.module.agent.middleware;

import com.fastrag.ai.model.ChatMessage;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.tools.registry.ToolDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S5 对话协议装配（tdd/widget.md S5；REQ-WG-D01/C01/D05）：
 * 中间件真实现直测——清单通道 List<Map>（编排层同源载荷），零 mock。
 */
class WidgetMiddlewareTest {

    private final WidgetMiddleware middleware = new WidgetMiddleware();

    private BaseContext contextWithCatalog(List<Map<String, Object>> catalog) {
        BaseContext context = new BaseContext();
        if (catalog != null) {
            context.getRuntimeState().put(WidgetMiddleware.WIDGET_CATALOG_KEY, catalog);
        }
        return context;
    }

    private List<ChatMessage> systemMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage system = new ChatMessage();
        system.setRole("system");
        system.setContent("基础系统提示");
        messages.add(system);
        ChatMessage user = new ChatMessage();
        user.setRole("user");
        user.setContent("帮我看下订单");
        messages.add(user);
        return messages;
    }

    private Map<String, Object> catalogEntry(String id) {
        return Map.of("id", id, "name", "订单确认卡", "description", "点单确认",
                "schemaJson", "{\"type\":\"object\",\"properties\":{\"total\":{\"type\":\"number\"}}}",
                "defaultJson", "{\"total\": 42}");
    }

    @Test
    @DisplayName("[装配] 绑定非空时 [清单注入+工具注册]")
    void injectsCatalogAndRegistersTool() {
        BaseContext context = contextWithCatalog(List.of(catalogEntry("w1"), catalogEntry("w2")));
        List<ChatMessage> messages = systemMessages();
        List<ToolDefinition> tools = new ArrayList<>();

        middleware.beforeModelCall(context, messages, tools);

        assertThat(messages.get(0).getContent()).contains("Widget 卡片已接入")
                .contains("w1").contains("订单确认卡").contains("w2")
                .contains("基础系统提示");
        assertThat(tools).anyMatch(t -> "render_widget".equals(t.getName()) && "widget".equals(t.getType()));
    }

    @Test
    @DisplayName("[装配] 无清单（纯直聊）时 [零注入不注册]")
    void skipsWhenNoCatalog() {
        BaseContext context = contextWithCatalog(null);
        List<ChatMessage> messages = systemMessages();
        List<ToolDefinition> tools = new ArrayList<>();

        middleware.beforeModelCall(context, messages, tools);

        assertThat(messages.get(0).getContent()).isEqualTo("基础系统提示");
        assertThat(tools).isEmpty();
    }

    @Test
    @DisplayName("[装配] 绑定全禁用时 [等同未绑定（空清单不注入）]")
    void skipsWhenCatalogEmpty() {
        BaseContext context = contextWithCatalog(List.of());
        List<ChatMessage> messages = systemMessages();
        List<ToolDefinition> tools = new ArrayList<>();

        middleware.beforeModelCall(context, messages, tools);

        assertThat(messages.get(0).getContent()).isEqualTo("基础系统提示");
        assertThat(tools).isEmpty();
    }

    @Test
    @DisplayName("[清单] 超 5000 字符时 [按序截断]")
    void truncatesOversizeCatalogBlock() {
        Map<String, Object> big = Map.of("id", "w1", "name", "大清单卡", "description", "描".repeat(6000),
                "schemaJson", "{}", "defaultJson", "{}");
        BaseContext context = contextWithCatalog(List.of(big));
        List<ChatMessage> messages = systemMessages();
        List<ToolDefinition> tools = new ArrayList<>();

        middleware.beforeModelCall(context, messages, tools);

        assertThat(messages.get(0).getContent().length()).isLessThanOrEqualTo("基础系统提示".length() + 5000);
        assertThat(tools).anyMatch(t -> "render_widget".equals(t.getName()));
    }

    @Test
    @DisplayName("[装配] render_widget 入参 schema [三必填]")
    void toolSchemaRequiresThreeArgs() {
        BaseContext context = contextWithCatalog(List.of(catalogEntry("w1")));
        List<ToolDefinition> tools = new ArrayList<>();
        middleware.beforeModelCall(context, systemMessages(), tools);
        ToolDefinition def = tools.stream().filter(t -> "render_widget".equals(t.getName())).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) def.getInputSchema().get("required");
        assertThat(required).containsExactly("widgetId", "widgetKey", "data");
    }
}
