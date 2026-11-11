package com.fastrag.module.agent.middleware;

import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Widget 清单注入中间件（order=14，0-13 全满尾插）：首轮模型调用前消费 runtimeState
 * 清单通道（编排层经 WidgetAssetService.buildCatalog 组装、PreRetrievalMiddleware 同型）——
 * 非空则注入「Widget 卡片清单」system 块并注册 render_widget 工具；空则静默跳过（纯直聊/未绑定）。
 * 不反向依赖 application 的服务层——只读 runtimeState Map。
 */
@Slf4j
@Component
public class WidgetMiddleware implements AgentMiddleware {

    /** 清单通道键（编排层 ↔ 中间件 ↔ 执行器三方共享）；载荷 = List<Map<String,Object>>，
     *  键集 id/name/description/schemaJson/defaultJson（松类型通道，与 HistoryMiddleware 同型，
     *  避免 agent→application 反向依赖） */
    public static final String WIDGET_CATALOG_KEY = "widgetCatalog";

    /** 清单注入截断预算（5000 字符口径，IF-003 同款） */
    private static final int MAX_CATALOG_CHARS = 5000;

    private static final String BLOCK_HEAD =
            "\n\n【Widget 卡片已接入】以下是可用的展示卡片。需要向用户展示结构化结果、"
                    + "收集选择/确认、或反馈任务进度时，调用 render_widget 工具，"
                    + "传入目标卡片的 widgetId、自拟的 widgetKey（同 key 再次调用会更新同一张卡片，"
                    + "适合进度类场景）以及符合该卡片数据结构的 data：\n";

    @Override
    public int getOrder() {
        return 14;
    }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                       List<ChatMessage> messages,
                                       List<ToolDefinition> tools) {
        Object raw = context.getRuntimeState().get(WIDGET_CATALOG_KEY);
        if (!(raw instanceof List<?> catalog) || catalog.isEmpty()) {
            return context;
        }
        try {
            String block = BLOCK_HEAD + renderCatalog(catalog);
            for (ChatMessage msg : messages) {
                if ("system".equals(msg.getRole())) {
                    String base = msg.getContent() == null ? "" : msg.getContent();
                    msg.setContent(base + truncate(block));
                    break;
                }
            }
            tools.add(ToolDefinition.builder()
                    .toolId("builtin_render_widget")
                    .name("render_widget")
                    .description("渲染一张交互式卡片给用户（展示结果/收集确认/反馈进度）。"
                            + "widgetId 取清单中的卡片标识，widgetKey 为你自拟的卡片键，"
                            + "data 必须符合该卡片数据结构声明的变量")
                    .type("widget")
                    .inputSchema(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "widgetId", Map.of("type", "string", "description", "卡片标识（取自清单）"),
                                    "widgetKey", Map.of("type", "string", "description",
                                            "卡片键（自拟；同键再次调用会替换同卡，用于进度更新）"),
                                    "data", Map.of("type", "object", "description",
                                            "渲染数据，须符合卡片数据结构声明")),
                            "required", List.of("widgetId", "widgetKey", "data")))
                    .build());
            log.info("[WidgetMiddleware] 注入 Widget 清单 {} 项并注册 render_widget", catalog.size());
        } catch (Exception e) {
            log.error("[WidgetMiddleware] 清单注入失败（对话照常）：{}", e.getMessage());
        }
        return context;
    }

    private String renderCatalog(List<?> catalog) {
        StringBuilder sb = new StringBuilder();
        for (Object o : catalog) {
            if (!(o instanceof Map<?, ?> e)) {
                continue;
            }
            sb.append("- widgetId=").append(e.get("id"))
                    .append("｜名称：").append(e.get("name"));
            Object desc = e.get("description");
            if (desc != null && !String.valueOf(desc).isBlank()) {
                sb.append("｜用途：").append(desc);
            }
            sb.append("｜数据结构：").append(e.get("schemaJson"));
            Object def = e.get("defaultJson");
            if (def != null && !String.valueOf(def).isBlank()) {
                sb.append("｜默认值示例：").append(def);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 清单注入截断（5000 字符口径，IF-003 同款，超限按序截断） */
    private String truncate(String block) {
        return block.length() > MAX_CATALOG_CHARS ? block.substring(0, MAX_CATALOG_CHARS) : block;
    }
}
