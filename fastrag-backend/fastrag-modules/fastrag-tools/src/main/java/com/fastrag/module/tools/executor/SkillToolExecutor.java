package com.fastrag.module.tools.executor;

import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 技能工具执行器。
 *
 * <p>Agent可调用的技能操作工具，提供技能的读取和激活功能。
 * 实现 {@link ToolExecutor} 接口，type为"skill"。</p>
 *
 * <p>支持的工具操作：</p>
 * <ul>
 *   <li>read_skill - 读取指定技能的Markdown内容，包含技能元数据(name、slug、description)和详细说明</li>
 *   <li>activate - 激活指定技能，返回激活成功信息及依赖项列表</li>
 * </ul>
 */
@Slf4j
@Component
public class SkillToolExecutor implements ToolExecutor {

    @Override
    public String getType() {
        return "skill";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(ToolDefinition tool, Map<String, Object> arguments, ToolContext ctx) {
        long t0 = System.currentTimeMillis();
        try {
            String action = arguments != null ? (String) arguments.get("action") : null;
            if (action == null) {
                return fail("Missing required parameter: action", t0);
            }

            Map<String, Object> config = tool.getConfig();
            String slug = config != null ? (String) config.get("slug") : null;
            String name = config != null ? (String) config.get("name") : "Unknown";
            String content = config != null ? (String) config.get("content") : "";

            return switch (action) {
                case "read_skill" -> {
                    log.info("[SkillTool] Reading skill: {} ({})", name, slug);
                    String md = buildSkillMdContent(name, slug, content, config);
                    yield ToolResult.success(md, duration(t0));
                }
                case "activate" -> {
                    log.info("[SkillTool] Activating skill: {} ({})", name, slug);
                    List<String> deps = config != null
                        ? (List<String>) config.getOrDefault("dependencies", List.of())
                        : List.of();
                    String depInfo = deps.isEmpty() ? "无" : String.join(", ", deps);
                    yield ToolResult.success(
                        "技能 '" + name + "' (slug: " + slug + ") 已激活。依赖项: " + depInfo,
                        duration(t0));
                }
                default -> fail("未知操作: " + action, t0);
            };
        } catch (Exception e) {
            log.error("[SkillTool] Execution failed", e);
            return fail(e.getMessage(), t0);
        }
    }

    private String buildSkillMdContent(String name, String slug, String content,
                                        Map<String, Object> config) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(name).append("\n");
        sb.append("slug: ").append(slug).append("\n");
        if (config != null && config.get("description") != null) {
            sb.append("description: ").append(config.get("description")).append("\n");
        }
        sb.append("---\n\n");
        if (content != null && !content.isEmpty()) {
            sb.append(content).append("\n");
        } else {
            sb.append("（该技能暂无详细描述内容）\n");
        }
        return sb.toString();
    }

    private ToolResult fail(String error, long t0) {
        ToolResult r = new ToolResult();
        r.setSuccess(false);
        r.setError(error);
        r.setDurationMs((int)(System.currentTimeMillis() - t0));
        return r;
    }

    private int duration(long t0) {
        return (int)(System.currentTimeMillis() - t0);
    }
}
