package com.fastrag.module.application.widget;

import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.application.widget.dto.WidgetSnapshot;
import com.fastrag.module.agent.middleware.WidgetMiddleware;
import com.fastrag.module.tools.executor.ToolContext;
import com.fastrag.module.tools.executor.ToolExecutor;
import com.fastrag.module.tools.executor.ToolResult;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * render_widget 工具执行器（独立 type="widget"，第 8 个执行器——builtin 型已被
 * InstallSkillToolExecutor 占用且工厂按 type findFirst，撞型路由不确定）。
 * 校验转快照的一层壳，深逻辑在 WidgetAssetService.resolveSnapshot（IF-004）。
 * 允许集合 = runtimeState 清单通道中的本次绑定资产（WidgetMiddleware 消费同源）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RenderWidgetToolExecutor implements ToolExecutor {

    public static final String TYPE = "widget";

    private final WidgetAssetService widgetAssetService;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public ToolResult execute(ToolDefinition tool, Map<String, Object> arguments, ToolContext ctx) {
        long start = System.currentTimeMillis();
        try {
            Set<String> allowed = allowedWidgetIds(ctx);
            if (allowed.isEmpty()) {
                return ToolResult.error("Error: 本次对话未绑定任何 Widget，不能渲染卡片", elapsed(start));
            }
            if (arguments == null) {
                return ToolResult.error("Error: 缺少工具参数（widgetId/widgetKey/data）", elapsed(start));
            }
            String widgetId = str(arguments.get("widgetId"));
            String widgetKey = str(arguments.get("widgetKey"));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) arguments.get("data");
            if (widgetId.isBlank() || widgetKey.isBlank()) {
                return ToolResult.error("Error: widgetId 与 widgetKey 均为必填", elapsed(start));
            }
            if (data == null) {
                return ToolResult.error("Error: 缺少 data（卡片渲染数据对象）", elapsed(start));
            }
            WidgetSnapshot snapshot = widgetAssetService.resolveSnapshot(widgetId, widgetKey, data, allowed);
            ToolResult result = ToolResult.success("已生成卡片：" + snapshot.name(), elapsed(start));
            result.setStructured(snapshot.toMap());
            return result;
        } catch (BusinessException be) {
            // 工具路径错误契约：Error 文本回灌 LLM 自纠，不中断对话（REQ-WG-D04）
            return ToolResult.error(be.getMessage(), elapsed(start));
        } catch (Exception e) {
            log.warn("[RenderWidget] 执行失败：{}", e.getMessage());
            return ToolResult.error("Error: 卡片生成失败：" + e.getMessage(), elapsed(start));
        }
    }

    /** 允许集合取自清单通道（编排层组装、中间件消费的同一份）——配置面与运行时同源 */
    private Set<String> allowedWidgetIds(ToolContext ctx) {
        Set<String> allowed = new HashSet<>();
        if (ctx != null && ctx.getExtension(WidgetMiddleware.WIDGET_CATALOG_KEY) instanceof List<?> catalog) {
            for (Object o : catalog) {
                if (o instanceof Map<?, ?> entry && entry.get("id") != null) {
                    allowed.add(String.valueOf(entry.get("id")));
                }
            }
        }
        return allowed;
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private int elapsed(long start) {
        return (int) (System.currentTimeMillis() - start);
    }
}
