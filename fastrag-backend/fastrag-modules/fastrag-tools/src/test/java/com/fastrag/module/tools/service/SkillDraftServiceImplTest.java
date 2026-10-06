package com.fastrag.module.tools.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.entity.SkillInstallDraft;
import com.fastrag.module.tools.mapper.SkillMapper;
import com.fastrag.module.tools.service.impl.SkillDraftServiceImpl;
import com.fastrag.module.tools.skill.SkillMarkdownParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillDraftServiceImplTest {

    @Mock
    private SkillMapper skillMapper;
    @Mock
    private com.fastrag.module.tools.mapper.SkillDependencyMapper dependencyMapper;
    @Mock
    private com.fastrag.module.tools.skill.SkillDependencyValidator dependencyValidator;

    private SkillDraftServiceImpl service;
    private Path tempDataDir;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        // M3-1 起注入 SkillAccessManager（shareConfig 降级归一），用真实实现以验证判定语义
        // M3-2 起注入 SkillDependencyMapper + SkillDependencyValidator（依赖落库）
        service = new SkillDraftServiceImpl(skillMapper, new SkillMarkdownParser(), om,
                new com.fastrag.module.tools.skill.SkillAccessManager(skillMapper),
                dependencyMapper, dependencyValidator);
        tempDataDir = createTempDir("draft-test-");
        ReflectionTestUtils.setField(service, "dataDir", tempDataDir.toString());
        // confirmDraft 经 SecurityUtil.getCurrentUser() 取 orgId，单测无认证过滤器须显式装配
        com.fastrag.security.filter.LoginUser user = com.fastrag.security.filter.LoginUser.builder()
            .userId("tester")
            .username("tester")
            .orgId("org-42")
            .permissions(List.of())
            .build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null));
    }

    @AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        if (tempDataDir != null) {
            deleteDir(tempDataDir);
        }
    }

    @Test
    void testPrepareUpload_SingleSkillMd() {
        byte[] content = "---\nname: Test Skill\nslug: test-skill\ndescription: A test\n---\n\nBody".getBytes();

        SkillInstallDraft draft = service.prepareUpload("my-skill.md", content, "tester");

        assertNotNull(draft);
        assertNotNull(draft.getDraftId());
        assertEquals("upload", draft.getSourceType());
        assertFalse(draft.getItems().isEmpty());
        assertTrue(draft.getItems().get(0).isSuccess());
        assertEquals("test-skill", draft.getItems().get(0).getSlug());
    }

    @Test
    void testPrepareUpload_InvalidFileFormat() {
        byte[] content = "not a valid file".getBytes();

        assertThrows(RuntimeException.class, () ->
            service.prepareUpload("data.bin", content, "tester"));
    }

    @Test
    void testConfirmDraft() throws Exception {
        // 先准备一个草稿
        byte[] mdContent = "---\nname: Test Skill\nslug: test-skill\ndescription: A test\n---\n\nBody".getBytes();
        SkillInstallDraft draft = service.prepareUpload("skill.md", mdContent, "tester");

        // mock slug 不存在（新安装）
        when(skillMapper.selectBySlug("test-skill")).thenReturn(null);

        // 确认安装
        List<SkillInstallDraft.DraftItem> results = service.confirmDraft(draft.getDraftId(),
            Map.of("accessLevel", "user"), "tester");

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).isSuccess());
        verify(skillMapper).insert(any(Skill.class));
    }

    @Test
    void testConfirmDraft_ReplaceExisting() throws Exception {
        byte[] mdContent = "---\nname: Existing\nslug: existing\ndescription: Already exists\n---\n\nBody".getBytes();
        SkillInstallDraft draft = service.prepareUpload("skill.md", mdContent, "tester");

        // mock slug 已存在 → 覆盖重装（M3-5：update-in-place，主键保留）
        Skill old = new Skill();
        old.setId("old-id");
        old.setSlug("existing");
        when(skillMapper.selectBySlug("existing")).thenReturn(old);

        List<SkillInstallDraft.DraftItem> results = service.confirmDraft(draft.getDraftId(), null, "tester");

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).isSuccess(), "覆盖重装应成功");
        verify(skillMapper, never()).deleteById(anyString());
        verify(skillMapper).updateById(any(Skill.class));
    }

    @Test
    void testDiscardDraft() throws Exception {
        byte[] content = "---\nname: Temp\nslug: temp\ndescription: Temporary\n---\n\nBody".getBytes();
        SkillInstallDraft draft = service.prepareUpload("temp.md", content, "tester");

        Path draftDir = tempDataDir.resolve("skill_import_drafts").resolve(draft.getDraftId());
        assertTrue(Files.exists(draftDir));

        service.discardDraft(draft.getDraftId(), "tester");

        assertFalse(Files.exists(draftDir));
    }

    @Test
    void testConfirmExpiredDraft() throws Exception {
        byte[] content = "---\nname: Expired\nslug: expired\ndescription: Will expire\n---\n\nBody".getBytes();
        SkillInstallDraft draft = service.prepareUpload("expired.md", content, "tester");

        // 手动设置过期时间为过去
        draft.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));

        // 重写 metadata.json
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        Path metaFile = tempDataDir.resolve("skill_import_drafts").resolve(draft.getDraftId()).resolve("metadata.json");
        Files.writeString(metaFile, om.writeValueAsString(draft));

        assertThrows(RuntimeException.class, () ->
            service.confirmDraft(draft.getDraftId(), null, "tester"));
    }

    @Test
    void testConfirmNonExistentDraft() {
        assertThrows(RuntimeException.class, () ->
            service.confirmDraft("non-existent-draft", null, "tester"));
    }

    // ========== M1-2 draftId UUID 严格校验 + slug 白名单接线 ==========

    @Test
    void testConfirmDraft_IllegalDraftIdRejected() {
        RuntimeException e = assertThrows(RuntimeException.class,
            () -> service.confirmDraft("../x", null, "tester"));
        assertTrue(e.getMessage().contains("非法的草稿 ID"));
    }

    @Test
    void testDiscardDraft_IllegalDraftIdRejected() {
        RuntimeException e = assertThrows(RuntimeException.class,
            () -> service.discardDraft("../../etc", "tester"));
        assertTrue(e.getMessage().contains("非法的草稿 ID"));
    }

    @Test
    void testPrepareUpload_PathTraversalFilenameRejected() {
        byte[] content = "---\nname: Evil\ndescription: e\n---\n\nBody".getBytes();

        RuntimeException e = assertThrows(RuntimeException.class,
            () -> service.prepareUpload("../../evil.md", content, "tester"));
        assertTrue(e.getMessage().contains("slug"), "消息应指明 slug 非法，而非落到目录创建");
    }

    // ========== M3-4（M1 遗留）：prepareUpload 异常通路清理空草稿目录 ==========

    @Test
    void testPrepareUpload_FailureLeavesNoEmptyDraftDir() throws Exception {
        byte[] content = "not a valid file".getBytes();

        assertThrows(RuntimeException.class, () -> service.prepareUpload("data.bin", content, "tester"));

        Path draftsRoot = tempDataDir.resolve("skill_import_drafts");
        if (Files.exists(draftsRoot)) {
            try (var dirs = Files.list(draftsRoot)) {
                assertEquals(0, dirs.count(), "异常通路不应残留空草稿目录");
            }
        }
    }

    @Test
    void testPrepareUpload_InvalidDerivedSlugRejected() {
        // 单文件分支：文件名派生 slug（大写+空格）非法 → validate 抛 BusinessException（统一业务错误）
        byte[] content = "whatever".getBytes();

        assertThrows(com.fastrag.common.exception.BusinessException.class,
            () -> service.prepareUpload("My Skill.md", content, "tester"));
    }

    // ========== M1-3 导入补属主 ==========

    @Test
    void testConfirmDraft_SetsCreatorFromOperatorAndOrgIdFromContext() {
        byte[] mdContent = "---\nname: Owned\nslug: owned-skill\ndescription: Owned skill\n---\n\nBody".getBytes();
        SkillInstallDraft draft = service.prepareUpload("owned.md", mdContent, "tester");

        when(skillMapper.selectBySlug("owned-skill")).thenReturn(null);

        service.confirmDraft(draft.getDraftId(), Map.of("accessLevel", "user"), "tester");

        org.mockito.ArgumentCaptor<Skill> captor = org.mockito.ArgumentCaptor.forClass(Skill.class);
        verify(skillMapper).insert(captor.capture());
        Skill inserted = captor.getValue();
        assertEquals("tester", inserted.getCreator(), "creator 应取 operator 入参");
        assertNotNull(inserted.getOrgId(), "orgId 应取当前登录用户所属组织");
        assertEquals("org-42", inserted.getOrgId());
    }

    // ========== M3-2（D9）：confirmDraft 落依赖（skill.dependencies JSON + skill_dependency 行，经校验器） ==========

    @Test
    void testConfirmDraft_WritesDependenciesJsonAndRows() {
        byte[] mdContent = ("---\nname: Deps\nslug: deps-skill\ndescription: d\n"
            + "tool_dependencies:\n  - tavily_search\n"
            + "mcp_dependencies:\n  - github\n"
            + "---\n\nBody").getBytes();
        SkillInstallDraft draft = service.prepareUpload("deps.md", mdContent, "tester");
        when(skillMapper.selectBySlug("deps-skill")).thenReturn(null);
        when(dependencyValidator.validate(any(), any())).thenAnswer(inv ->
            new com.fastrag.module.tools.skill.SkillDependencyValidator.ValidationResult(inv.getArgument(1), List.of()));
        when(skillMapper.insert(any(Skill.class))).thenAnswer(inv -> {
            inv.getArgument(0, Skill.class).setId("new-id"); // 模拟 ASSIGN_ID 回填
            return 1;
        });

        service.confirmDraft(draft.getDraftId(), Map.of("accessLevel", "user"), "tester");

        org.mockito.ArgumentCaptor<Skill> captor = org.mockito.ArgumentCaptor.forClass(Skill.class);
        verify(skillMapper).insert(captor.capture());
        Skill inserted = captor.getValue();
        assertNotNull(inserted.getDependencies(), "解析出的依赖应写入 skill.dependencies JSON");
        assertEquals(List.of("tool:tavily_search", "mcp:github"), inserted.getDependencies(),
            "依赖串规范为 type:name 形态");
        // 依赖行先删后插（主键来自 insert 回填）
        verify(dependencyMapper).deleteBySkillId("new-id");
        verify(dependencyMapper, times(2)).insert(any(com.fastrag.module.tools.entity.SkillDependency.class));
    }

    @Test
    void testConfirmDraft_InvalidDependencyStillInstallsWithoutRows() {
        byte[] mdContent = ("---\nname: BadDep\nslug: bad-dep\ndescription: d\n"
            + "tool_dependencies:\n  - ghost_tool\n"
            + "---\n\nBody").getBytes();
        SkillInstallDraft draft = service.prepareUpload("baddep.md", mdContent, "tester");
        when(skillMapper.selectBySlug("bad-dep")).thenReturn(null);
        when(dependencyValidator.validate(any(), any())).thenReturn(
            new com.fastrag.module.tools.skill.SkillDependencyValidator.ValidationResult(
                List.of(), List.of("tool:ghost_tool - 工具不存在或未启用: ghost_tool")));

        List<SkillInstallDraft.DraftItem> results = service.confirmDraft(
            draft.getDraftId(), Map.of("accessLevel", "user"), "tester");

        assertTrue(results.get(0).isSuccess(), "依赖非法不阻断安装（内置技能可先于工具存在）");
        org.mockito.ArgumentCaptor<Skill> captor = org.mockito.ArgumentCaptor.forClass(Skill.class);
        verify(skillMapper).insert(captor.capture());
        assertTrue(captor.getValue().getDependencies() == null || captor.getValue().getDependencies().isEmpty(),
            "非法依赖不应写入 dependencies JSON");
        verify(dependencyMapper, never()).insert(any());
    }

    // ========== M3-5（D11）：slug 已存在 → update-in-place（主键不变，绑定天然存活） ==========

    @Test
    void testConfirmDraft_SlugExistsUpdatesInPlaceKeepingId() {
        byte[] mdContent = "---\nname: Existing\nslug: existing\ndescription: Updated desc\n---\n\nNew body".getBytes();
        SkillInstallDraft draft = service.prepareUpload("existing.md", mdContent, "tester");

        Skill old = new Skill();
        old.setId("old-id");
        old.setSlug("existing");
        old.setName("Old Name");
        old.setEnabled(0); // update-in-place 不应被覆盖回 enabled=1？——语义：保留既有 enabled/scope
        when(skillMapper.selectBySlug("existing")).thenReturn(old);

        List<SkillInstallDraft.DraftItem> results = service.confirmDraft(draft.getDraftId(), null, "tester");

        assertTrue(results.get(0).isSuccess(), "覆盖重装应成功");
        // 主键保留：updateById 而非 deleteById+insert
        verify(skillMapper, never()).deleteById(anyString());
        verify(skillMapper, never()).insert(any(Skill.class));
        org.mockito.ArgumentCaptor<Skill> captor = org.mockito.ArgumentCaptor.forClass(Skill.class);
        verify(skillMapper).updateById(captor.capture());
        Skill updated = captor.getValue();
        assertEquals("old-id", updated.getId(), "主键必须保留（app_skill_binding 因 id 不变天然存活）");
        assertEquals("Existing", updated.getName(), "name 应随 SKILL.md 更新");
        assertEquals("Updated desc", updated.getDescription(), "description 应随 SKILL.md 更新");
        assertEquals(Integer.valueOf(0), updated.getEnabled(), "既有 enabled 不被覆盖");
        // 依赖行先删后插
        verify(dependencyMapper).deleteBySkillId("old-id");
    }

    @Test
    void testConfirmDraft_OverwriteKeepsExistingShareConfig() {
        byte[] mdContent = "---\nname: Existing\nslug: existing\ndescription: d\n---\n\nBody".getBytes();
        SkillInstallDraft draft = service.prepareUpload("existing.md", mdContent, "tester");

        Skill old = new Skill();
        old.setId("old-id");
        old.setSlug("existing");
        old.setShareConfig(Map.of("accessLevel", "department", "departmentIds", List.of("org-1")));
        when(skillMapper.selectBySlug("existing")).thenReturn(old);

        service.confirmDraft(draft.getDraftId(), Map.of("accessLevel", "user"), "tester");

        org.mockito.ArgumentCaptor<Skill> captor = org.mockito.ArgumentCaptor.forClass(Skill.class);
        verify(skillMapper).updateById(captor.capture());
        assertEquals("department", captor.getValue().getShareConfig().get("accessLevel"),
            "update-in-place 不应覆盖既有分享配置（spec 更新字段清单不含 shareConfig）");
    }

    // 辅助方法

    private Path createTempDir(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteDir(Path dir) {
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
            }
        } catch (Exception ignored) {}
    }
}
