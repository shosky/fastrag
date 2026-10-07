package com.fastrag.module.agent.sandbox;

import com.fastrag.module.agent.context.User;
import com.fastrag.module.agent.entity.SandboxConfig;
import com.fastrag.module.agent.sandbox.config.EffectiveConfig;
import com.fastrag.module.agent.sandbox.config.SandboxConfigService;
import com.fastrag.module.agent.sandbox.provider.CreateContext;
import com.fastrag.module.agent.sandbox.provider.ExecRequest;
import com.fastrag.module.agent.sandbox.provider.ExecResult;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderClient;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderException;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderRegistry;
import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.service.SkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SandboxExecutionFacade 用例（docs/tdd/agent.md S7 SandboxExecutionFacade 表 9 条，
 * contract agent.SandboxExecutionFacade）。
 *
 * <p>mock Lifecycle + mock ProviderClient（registry 装真实例 + mock client）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SandboxExecutionServiceTest {

    private static final String USER = "u1";
    private static final String SESSION = "s1";
    private static final String CONFIG_ID = "cfg-1";

    @Mock
    private SandboxConfigService configService;
    @Mock
    private SandboxLifecycleService lifecycle;
    @Mock
    private SandboxProviderClient docker;
    @Mock
    private SkillService skillService;

    private SandboxProviderRegistry registry;
    private SandboxExecutionService facade;
    private SandboxHandle handle;
    private SandboxConfig configEntity;
    private EffectiveConfig effective;

    @BeforeEach
    void setUp() {
        when(docker.provider()).thenReturn("docker"); // registry 按 provider() 建映射
        registry = new SandboxProviderRegistry(List.of(docker));
        facade = new SandboxExecutionService(configService, lifecycle, registry, skillService);
        handle = SandboxHandle.builder()
                .sandboxId("inst-1").provider("docker").configId(CONFIG_ID).state("running")
                .build();

        configEntity = new SandboxConfig();
        configEntity.setId(CONFIG_ID);
        configEntity.setUserId(USER);
        configEntity.setSandboxType("docker");
        configEntity.setEnabled(true);

        effective = EffectiveConfig.builder()
                .type("docker").configId(CONFIG_ID).userId(USER).enabled(true)
                .defaultTimeoutSec(60)
                .build();
    }

    /** 分支全通的可用桩：总开关开 + 配置存在且启用 + acquire 成功 */
    private void stubAvailable() {
        when(configService.isGloballyEnabled()).thenReturn(true);
        when(configService.get(eq(CONFIG_ID), any(User.class))).thenReturn(configEntity);
        when(configService.resolveCredentials(configEntity)).thenReturn(effective);
        when(lifecycle.acquire(eq(USER), eq(SESSION), any(EffectiveConfig.class))).thenReturn(handle);
    }

    // ---------- 1. [降级三分支] 三因可区分 Error 文本 ----------

    @Test
    void 降级三分支_总开关关_应用未配置_连接失败时_三种可区分Error文本() {
        // 分支①：总开关关
        when(configService.isGloballyEnabled()).thenReturn(false);
        String branch1 = facade.readFile(USER, SESSION, CONFIG_ID, "/workspace/a.txt", null, null);

        // 分支②：应用未引用配置（configId 空）
        when(configService.isGloballyEnabled()).thenReturn(true);
        String branch2 = facade.readFile(USER, SESSION, null, "/workspace/a.txt", null, null);

        // 分支②'：配置级禁用同样归未配置分支
        when(configService.get(eq(CONFIG_ID), any(User.class))).thenReturn(configEntity);
        when(configService.resolveCredentials(configEntity)).thenReturn(effective);
        configEntity.setEnabled(false);
        String branch2Disabled = facade.readFile(USER, SESSION, CONFIG_ID, "/workspace/a.txt", null, null);
        configEntity.setEnabled(true);

        // 分支③：acquire 连接失败
        when(lifecycle.acquire(eq(USER), eq(SESSION), any(EffectiveConfig.class)))
                .thenThrow(SandboxProviderException.unavailable("daemon unreachable"));
        String branch3 = facade.readFile(USER, SESSION, CONFIG_ID, "/workspace/a.txt", null, null);

        assertThat(branch1).isEqualTo(SandboxExecutionService.TEXT_GLOBAL_DISABLED);
        assertThat(branch2).isEqualTo(SandboxExecutionService.TEXT_NOT_CONFIGURED);
        assertThat(branch2Disabled).isEqualTo(SandboxExecutionService.TEXT_NOT_CONFIGURED);
        assertThat(branch3).startsWith("Error: Sandbox unavailable:").contains("UNAVAILABLE");

        // 三文本互不相同且语义对应（tdd 断言）
        assertThat(branch1).isNotEqualTo(branch2);
        assertThat(branch2).isNotEqualTo(branch3);
        assertThat(branch1).isNotEqualTo(branch3);
    }

    // ---------- 2. [路径钳制] 读 /etc/passwd 时 [拒绝且 provider 零调用] ----------

    @Test
    void 路径钳制_读etc_passwd时_拒绝且provider零调用() {
        String result = facade.readFile(USER, SESSION, CONFIG_ID, "/etc/passwd", null, null);

        assertThat(result).startsWith("Error:");
        verify(docker, never()).readFile(any(), anyString());
    }

    // ---------- 3. [写根限制] 写 input 拒、写 /workspace 根内过 ----------

    @Test
    void 写根限制_写input拒_写workspace根内过() {
        stubAvailable();

        String rejected = facade.writeFile(USER, SESSION, CONFIG_ID,
                SandboxWorkspaceLayout.INPUT_DIR + "/a.txt", "x");
        String accepted = facade.writeFile(USER, SESSION, CONFIG_ID, "/workspace/a.txt", "hello");

        assertThat(rejected).startsWith("Error:").contains("not writable");
        assertThat(accepted).isEqualTo("OK");
        verify(docker, never()).writeFile(any(SandboxHandle.class),
                eq(SandboxWorkspaceLayout.INPUT_DIR + "/a.txt"), anyString());
        verify(docker).writeFile(handle, "/workspace/a.txt", "hello");
    }

    // ---------- 4. [skills 只读] 写 /workspace/skills 拒、读其下 SKILL.md 过 ----------

    @Test
    void skills只读_写skills拒_读其下SKILL_md过() {
        stubAvailable();
        when(docker.readFile(handle, "/workspace/skills/web/SKILL.md")).thenReturn("# Web");

        String writeRejected = facade.writeFile(USER, SESSION, CONFIG_ID,
                "/workspace/skills/web/SKILL.md", "tamper");
        String readAllowed = facade.readFile(USER, SESSION, CONFIG_ID,
                "/workspace/skills/web/SKILL.md", null, null);

        assertThat(writeRejected).startsWith("Error:").contains("not writable");
        assertThat(readAllowed).isEqualTo("# Web");
    }

    // ---------- 4b. [活动标记保留路径] 写 .fastrag_activity 拒（backlog S3） ----------

    @Test
    void 活动标记防御_写fastrag_activity时_拒写且provider零调用() {
        stubAvailable();

        String rejected = facade.writeFile(USER, SESSION, CONFIG_ID,
                SandboxWorkspaceLayout.ACTIVITY_MARKER, "0");

        assertThat(rejected).startsWith("Error:").contains("not writable");
        verify(docker, never()).writeFile(any(SandboxHandle.class), anyString(), anyString());
    }

    // ---------- 5. [穿越拒绝] path 含 ../ 时 [拒绝不触 provider] ----------

    @Test
    void 穿越拒绝_path含穿越片段时_拒绝不触provider() {
        String result = facade.readFile(USER, SESSION, CONFIG_ID, "/workspace/../etc/passwd", null, null);

        assertThat(result).startsWith("Error:").contains("traversal");
        verify(docker, never()).readFile(any(), anyString());
        verify(docker, never()).create(any(CreateContext.class));
    }

    // ---------- 6. [输出截断] exec 输出超 256KB 时 [头尾保留、中间省略返回]（I3） ----------

    @Test
    void 输出截断_exec输出超256KB时_头尾保留中间省略() {
        stubAvailable();
        String big = "x".repeat(300_000) + "\nERROR: exit 1 at tail";
        when(docker.exec(any(SandboxHandle.class), any(ExecRequest.class)))
                .thenReturn(ExecResult.builder().stdout(big).exitCode(0).build());

        String result = facade.execute(USER, SESSION, CONFIG_ID, "cat big.log", null, null);

        assertThat(result).startsWith("xxx");
        assertThat(result).endsWith("ERROR: exit 1 at tail");
        assertThat(result).contains("省略");
        assertThat(result.length()).isLessThanOrEqualTo(262_144 + 64);
    }

    // ---------- 7. [附件暂存] uploads 传入时 [拷入 /workspace/input 并回填沙箱内路径] ----------

    @Test
    void 附件暂存_uploads传入时_拷入input并回填沙箱内路径(@TempDir Path dir) throws Exception {
        stubAvailable();
        Path local = dir.resolve("report.txt");
        Files.writeString(local, "attachment body");
        Map<String, Object> upload = Map.of("file_name", "report.txt",
                "path", local.toAbsolutePath().toString());

        Map<String, String> staged = facade.stageAttachments(USER, SESSION, CONFIG_ID, List.of(upload));

        assertThat(staged).containsEntry("report.txt", "/workspace/input/report.txt");
        verify(docker).writeFile(handle, "/workspace/input/report.txt", "attachment body");
    }

    // ---------- 8. [技能物化] slugs 传入时 [写入 /workspace/skills/{slug}] ----------

    @Test
    void 技能物化_slugs传入时_写入skills目录() {
        stubAvailable();
        Skill web = new Skill();
        web.setSlug("web");
        web.setName("Web");
        web.setDescription("Web skill");
        web.setContent("# Web content");
        Skill code = new Skill();
        code.setSlug("code");
        code.setName("Code");
        code.setDescription("Code skill");
        when(skillService.getBySlug("web")).thenReturn(web);
        when(skillService.getBySlug("code")).thenReturn(code);

        Map<String, String> synced = facade.syncSkills(USER, SESSION, CONFIG_ID, List.of("web", "code"));

        assertThat(synced).containsKeys("web", "code");
        verify(docker).writeFile(eq(handle), eq("/workspace/skills/web/SKILL.md"), anyString());
        verify(docker).writeFile(eq(handle), eq("/workspace/skills/code/SKILL.md"), anyString());
    }

    // ---------- 9. [门面不建实例] 任意动作执行时 [实例获取恒经 acquire] ----------

    @Test
    void 门面不建实例_任意动作执行时_实例获取恒经acquire() {
        stubAvailable();
        when(docker.exec(any(SandboxHandle.class), any(ExecRequest.class)))
                .thenReturn(ExecResult.builder().stdout("ok").exitCode(0).build());

        facade.execute(USER, SESSION, CONFIG_ID, "echo ok", null, null);
        facade.listDirectory(USER, SESSION, CONFIG_ID, "/workspace");
        facade.glob(USER, SESSION, CONFIG_ID, "*.md", "/workspace");

        verify(lifecycle, org.mockito.Mockito.atLeastOnce())
                .acquire(eq(USER), eq(SESSION), any(EffectiveConfig.class));
        verify(docker, never()).create(any(CreateContext.class)); // 门面不直连 provider 创建
    }

    // ---------- 补充：grep 走 provider.exec 包装；listDirectory 空目录文本 ----------

    @Test
    void 补充_grep经provider_exec包装且路径单引号安全() {
        stubAvailable();
        when(docker.exec(any(SandboxHandle.class), any(ExecRequest.class)))
                .thenReturn(ExecResult.builder().stdout("f:1:hit").exitCode(0).build());

        String result = facade.grep(USER, SESSION, CONFIG_ID, "it's", "/workspace", "*.py");

        assertThat(result).isEqualTo("f:1:hit");
        org.mockito.ArgumentCaptor<ExecRequest> captor =
                org.mockito.ArgumentCaptor.forClass(ExecRequest.class);
        verify(docker).exec(eq(handle), captor.capture());
        assertThat(captor.getValue().getCommand())
                .contains("'it'\\''s'")
                .contains("'/workspace'")
                .contains("--include='*.py'");
    }

    @Test
    void 补充_读操作_偏移与行数限制按行切片() {
        stubAvailable();
        when(docker.readFile(handle, "/workspace/a.txt")).thenReturn("l1\nl2\nl3\nl4");

        String sliced = facade.readFile(USER, SESSION, CONFIG_ID, "/workspace/a.txt", 1, 2);

        assertThat(sliced).isEqualTo("l2\nl3");
    }
}
