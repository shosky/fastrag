package com.fastrag.module.tools.executor;

import com.fastrag.module.tools.registry.ToolDefinition;
import com.fastrag.module.tools.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * install_skill 工具执行器。
 * 允许 Agent 在对话中从远程仓库或本地路径安装技能。
 * type = "builtin"，由 ToolRegistry 统一注入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstallSkillToolExecutor implements ToolExecutor {

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
                    "需要提供 source 参数（GitHub 仓库格式：owner/repo 或 owner/repo@skill-name）",
                    (int) (System.currentTimeMillis() - t0));
            }

            String result;
            if (source != null && source.contains("/")) {
                result = installFromRemote(source, skillName, ctx);
            } else {
                result = installFromLocal(source != null ? source : skillName, ctx);
            }

            return ToolResult.success(result, (int) (System.currentTimeMillis() - t0));

        } catch (Exception e) {
            log.error("[InstallSkillTool] 安装技能失败", e);
            return ToolResult.error("安装失败: " + e.getMessage(),
                (int) (System.currentTimeMillis() - t0));
        }
    }

    private String installFromRemote(String source, String skillName, ToolContext ctx) {
        log.info("[InstallSkillTool] 从远程安装: source={}, skill={}", source, skillName);

        StringBuilder sb = new StringBuilder();
        sb.append("要安装的技能: ").append(source);
        if (skillName != null) {
            sb.append(" (").append(skillName).append(")");
        }
        sb.append("\n\n");

        // 解析 source
        // 格式: owner/repo 或 owner/repo@skill-name
        String repo;
        String specificSkill = null;
        if (source.contains("@")) {
            String[] parts = source.split("@", 2);
            repo = parts[0];
            specificSkill = parts[1];
        } else {
            repo = source;
        }

        String skillToInstall = specificSkill != null ? specificSkill : skillName;

        sb.append("仓库: ").append(repo).append("\n");
        if (skillToInstall != null) {
            sb.append("技能: ").append(skillToInstall).append("\n");
        }

        // 检查技能是否已安装
        if (skillToInstall != null && skillService.existsBySlug(skillToInstall)) {
            sb.append("\n⚠️ 技能 '").append(skillToInstall).append("' 已安装。");
            if (ctx != null) {
                sb.append("\n\n如需重新安装，请先删除现有技能。");
            }
            return sb.toString();
        }

        sb.append("\n安装方式: ");
        sb.append("\n1. 通过命令行: `npx -y skills add github:").append(repo).append(" --skill ").append(skillToInstall != null ? skillToInstall : "<name>").append(" -g -y --copy`");
        sb.append("\n2. 通过管理界面: 在「技能管理」页面点击「远程安装」");
        sb.append("\n\n安装后技能将被自动启用。");

        return sb.toString();
    }

    private String installFromLocal(String skillName, ToolContext ctx) {
        log.info("[InstallSkillTool] 从本地安装: skill={}", skillName);

        if (skillName == null) {
            return "请提供要安装的技能名称。";
        }

        // 检查技能是否已安装
        if (skillService.existsBySlug(skillName)) {
            return "技能 '" + skillName + "' 已安装。如需重新安装，请先删除现有技能。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("本地安装技能: ").append(skillName).append("\n\n");
        sb.append("请通过以下方式之一安装此技能：\n");
        sb.append("1. 将 SKILL.md 文件放在沙箱目录 `/home/gem/user-data/skills/").append(skillName).append("/` 下\n");
        sb.append("2. 通过技能管理界面上传 ZIP 文件\n");
        sb.append("3. 使用远程安装命令从仓库安装\n");

        return sb.toString();
    }
}
