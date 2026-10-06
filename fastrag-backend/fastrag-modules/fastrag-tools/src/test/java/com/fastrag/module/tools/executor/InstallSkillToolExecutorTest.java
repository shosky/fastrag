package com.fastrag.module.tools.executor;

import com.fastrag.module.tools.registry.ToolDefinition;
import com.fastrag.module.tools.service.SkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstallSkillToolExecutorTest {

    @Mock
    private SkillService skillService;

    private InstallSkillToolExecutor executor;
    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        executor = new InstallSkillToolExecutor(skillService);
        ctx = new ToolContext();
        ctx.setUserId("user1");
    }

    @Test
    void testGetType() {
        assertEquals("builtin", executor.getType());
    }

    @Test
    void testInstallFromRemote() {
        when(skillService.existsBySlug("web-search")).thenReturn(false);

        ToolDefinition def = new ToolDefinition();
        def.setName("install_skill");

        Map<String, Object> args = Map.of(
            "source", "owner/repo@web-search",
            "skill_name", "web-search"
        );

        ToolResult result = executor.execute(def, args, ctx);

        assertTrue(result.isSuccess());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().contains("web-search"));
        assertTrue(result.getOutput().contains("owner/repo"));
    }

    @Test
    void testInstallAlreadyExists() {
        when(skillService.existsBySlug("existing-skill")).thenReturn(true);

        ToolDefinition def = new ToolDefinition();
        def.setName("install_skill");

        Map<String, Object> args = Map.of(
            "source", "owner/repo@existing-skill"
        );

        ToolResult result = executor.execute(def, args, ctx);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("已安装"));
    }

    @Test
    void testMissingParameters() {
        ToolDefinition def = new ToolDefinition();
        def.setName("install_skill");

        ToolResult result = executor.execute(def, Map.of(), ctx);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("需要提供 source"));
    }

    @Test
    void testNullArguments() {
        ToolDefinition def = new ToolDefinition();
        def.setName("install_skill");

        ToolResult result = executor.execute(def, null, ctx);

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    void testLocalInstall() {
        when(skillService.existsBySlug("local-skill")).thenReturn(false);

        ToolDefinition def = new ToolDefinition();
        def.setName("install_skill");

        Map<String, Object> args = Map.of("source", "local-skill");

        ToolResult result = executor.execute(def, args, ctx);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("local-skill"));
    }

    @Test
    void testInstallToolDefinition() {
        ToolDefinition def = InstallSkillToolDefinition.getDefinition();

        assertNotNull(def);
        assertEquals("builtin_install_skill", def.getToolId());
        assertEquals("install_skill", def.getName());
        assertEquals("builtin", def.getType());
        assertNotNull(def.getInputSchema());
        assertTrue(def.getInputSchema().containsKey("properties"));
    }

    // ===== M3-3（D14）：install_skill 指引坐实——输出真实导入流水线 =====

    @Test
    void testLocalInstall_GuidesRealImportPipeline() {
        when(skillService.existsBySlug("local-skill")).thenReturn(false);

        ToolDefinition def = new ToolDefinition();
        def.setName("install_skill");
        Map<String, Object> args = Map.of("source", "local-skill");

        ToolResult result = executor.execute(def, args, ctx);

        assertTrue(result.isSuccess());
        String out = result.getOutput();
        assertTrue(out.contains("/api/skills/import/prepare"), "应指引上传自动安装端点");
        assertTrue(out.contains("install-drafts/{draftId}/confirm"), "应指引两步草稿确认端点");
        assertTrue(out.contains(".zip") && out.contains(".md"), "应说明支持格式 .zip/.md");
        assertTrue(out.contains("^[a-z0-9][a-z0-9._-]{0,63}$"), "应说明 slug 规则");
        assertTrue(out.contains("技能管理"), "应提示管理页路径");
    }

    @Test
    void testRemoteInstall_GuidesRealImportPipeline() {
        when(skillService.existsBySlug("web-search")).thenReturn(false);

        ToolDefinition def = new ToolDefinition();
        def.setName("install_skill");
        Map<String, Object> args = Map.of("source", "owner/repo@web-search", "skill_name", "web-search");

        ToolResult result = executor.execute(def, args, ctx);

        assertTrue(result.isSuccess());
        String out = result.getOutput();
        assertTrue(out.contains("/api/skills/import/prepare"), "远端安装同样指引导入流水线");
        assertTrue(out.contains("owner/repo"), "应保留来源仓库信息");
    }
}
