package com.fastrag.module.tools.executor;

import com.fastrag.module.tools.registry.ToolDefinition;
import com.fastrag.module.tools.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * install_skill 工具执行器（M3-3/D14：指引桩坐实为真实导入流水线指引）。
 *
 * <p>本执行器不直接落库安装（安装动作属管理面，经鉴权），而是向模型输出可执行的
 * 导入流水线指引：支持格式（.zip/.md）、上传即装端点 {@code POST /api/skills/import/prepare}、
 * 两步草稿端点 {@code POST /api/skills/install-drafts/{draftId}/confirm}、
 * slug 白名单规则与管理页路径。保持 ToolResult success 语义。</p>
 *
 * <p>type = "builtin"，由 ToolRegistry 统一注入、AgentEngine 按条件过滤（M3-3 REQ-E03 修订）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstallSkillToolExecutor implements ToolExecutor {

    /** 技能 slug 白名单规则（与 SlugValidator 一致） */
    static final String SLUG_RULE = "^[a-z0-9][a-z0-9._-]{0,63}$";

    private final SkillService skillService;

    @Override
    public String getType() {
        return "builtin";
    }

    @Override
    public ToolResult execute(ToolDefinition tool, Map<String, Object> arguments, ToolContext ctx) {
        long t0 = System.currentTimeMillis();

        try {
            String source = arguments != null ? (String) arguments.get("source") : null;
            String skillName = arguments != null ? (String) arguments.get("skill_name") : null;

            if (source == null && skillName == null) {
                return ToolResult.error(
                    "需要提供 source 参数（GitHub 仓库格式：owner/repo 或 owner/repo@skill-name，或本地技能 slug）",
                    (int) (System.currentTimeMillis() - t0));
            }

            String result;
            if (source != null && source.contains("/")) {
                result = installFromRemote(source, skillName);
            } else {
                result = installFromLocal(source != null ? source : skillName);
            }

            return ToolResult.success(result, (int) (System.currentTimeMillis() - t0));

        } catch (Exception e) {
            log.error("[InstallSkillTool] 安装技能失败", e);
            return ToolResult.error("安装失败: " + e.getMessage(),
                (int) (System.currentTimeMillis() - t0));
        }
    }

    /** 导入流水线指引正文（M3-3：坐实真实端点与规则） */
    private String pipelineGuidance(String heading) {
        return heading + "\n\n"
            + "支持的安装方式：\n"
            + "1. 上传即装（推荐）：将技能包（.zip）或单个 SKILL.md（.md）文件通过\n"
            + "   POST /api/skills/import/prepare 上传，服务端解析 frontmatter 后自动完成安装。\n"
            + "2. 两步草稿：先 POST /api/skills/import/prepare 得到 draftId，再调用\n"
            + "   POST /api/skills/install-drafts/{draftId}/confirm 确认安装（可携带 shareConfig）。\n"
            + "3. 管理页操作：进入「技能管理」页面，使用「导入技能 / 远程安装」功能。\n\n"
            + "约束：\n"
            + "- 文件格式仅支持 .zip（可含 references/、scripts/ 等子目录）或 .md（SKILL.md）\n"
            + "- slug 规则：" + SLUG_RULE + "（小写字母或数字开头，仅含 a-z、0-9、.、_、-，长度 1-64）\n"
            + "- SKILL.md 必须以 --- frontmatter 开头，且包含 name 与 description 字段\n";
    }

    private String installFromRemote(String source, String skillName) {
        // M3-3：skill_name 缺省时从 owner/repo@skill-name 形态解析（已安装检查需真实 slug）
        if (skillName == null && source.contains("@")) {
            String derived = source.substring(source.indexOf('@') + 1).strip();
            if (!derived.isEmpty()) skillName = derived;
        }
        log.info("[InstallSkillTool] 从远程安装: source={}, skill={}", source, skillName);

        StringBuilder sb = new StringBuilder();
        sb.append("要安装的技能: ").append(source);
        if (skillName != null) {
            sb.append(" (").append(skillName).append(")");
        }
        sb.append("\n\n仓库: ").append(source.contains("@") ? source.substring(0, source.indexOf('@')) : source);

        // 检查技能是否已安装
        if (skillName != null && skillService.existsBySlug(skillName)) {
            sb.append("\n\n技能 '").append(skillName).append("' 已安装。如需重新安装（覆盖重装），")
              .append("直接重复导入即可——同 slug 安装将原地更新，不影响已有绑定。");
            return sb.toString();
        }

        sb.append("\n").append(pipelineGuidance("请按以下导入流水线安装该技能："));
        return sb.toString();
    }

    private String installFromLocal(String skillName) {
        log.info("[InstallSkillTool] 从本地安装: skill={}", skillName);

        if (skillName == null) {
            return "请提供要安装的技能名称。";
        }

        // 检查技能是否已安装
        if (skillService.existsBySlug(skillName)) {
            return "技能 '" + skillName + "' 已安装。如需重新安装（覆盖重装），直接重复导入即可——同 slug 安装将原地更新，不影响已有绑定。";
        }

        return pipelineGuidance("本地安装技能: " + skillName);
    }
}
