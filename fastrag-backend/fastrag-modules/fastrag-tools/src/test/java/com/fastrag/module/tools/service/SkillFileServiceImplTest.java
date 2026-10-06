package com.fastrag.module.tools.service;

import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.entity.SkillFileContent;
import com.fastrag.module.tools.entity.SkillFileTree;
import com.fastrag.module.tools.mapper.SkillMapper;
import com.fastrag.module.tools.service.impl.SkillFileServiceImpl;
import com.fastrag.security.filter.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * SkillFileServiceImpl 单元测试.
 * 覆盖文件树/读取/创建/更新/删除/导出，以及属主鉴权（读=visible，写删=manageable）与 slug 防御。
 * 鉴权依赖 SecurityUtil.getCurrentUser()，单测无认证过滤器，须显式装配安全上下文。
 */
@ExtendWith(MockitoExtension.class)
class SkillFileServiceImplTest {

    @Mock
    private SkillMapper skillMapper;

    private SkillFileServiceImpl service;
    private Path tempDataDir;
    private Path skillDir;

    @BeforeEach
    void setUp() throws Exception {
        // 创建临时目录模拟数据目录
        tempDataDir = Files.createTempDirectory("skill-test-");
        skillDir = tempDataDir.resolve("skills").resolve("test-skill");
        Files.createDirectories(skillDir);

        service = new SkillFileServiceImpl(skillMapper, new com.fastrag.module.tools.skill.SkillMarkdownParser());
        ReflectionTestUtils.setField(service, "dataDir", tempDataDir.toString());

        loginAs("tester", List.of());
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        // 清理临时目录
        if (tempDataDir != null) {
            Files.walk(tempDataDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
        }
    }

    /** 装配当前登录用户（SecurityContext） */
    private void loginAs(String userId, List<String> permissions) {
        LoginUser user = LoginUser.builder()
            .userId(userId)
            .username(userId)
            .orgId("org-1")
            .permissions(permissions)
            .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
    }

    /** mock 技能记录：selectBySlug(slug) 返回指定属主/组织/内置标记的技能 */
    private void givenSkill(String slug, String creator, String orgId, Integer isBuiltin) {
        Skill s = new Skill();
        s.setSlug(slug);
        s.setCreator(creator);
        s.setOrgId(orgId);
        s.setIsBuiltin(isBuiltin);
        when(skillMapper.selectBySlug(slug)).thenReturn(s);
    }

    @Test
    void testGetTree() throws Exception {
        givenSkill("test-skill", "tester", "org-1", 0);
        // 创建测试文件结构
        Files.writeString(skillDir.resolve("SKILL.md"), "# Test\nContent");
        Files.createDirectories(skillDir.resolve("tools"));
        Files.writeString(skillDir.resolve("tools").resolve("helper.py"), "def help(): pass");

        SkillFileTree tree = service.getTree("test-skill");

        assertNotNull(tree);
        assertEquals("test-skill", tree.getName());
        assertTrue(tree.isDir());
        assertNotNull(tree.getChildren());
    }

    @Test
    void testGetTreeNonExistent() {
        // 技能记录存在但磁盘目录缺失 → 返回空树
        givenSkill("empty-skill", "tester", "org-1", 0);

        SkillFileTree tree = service.getTree("empty-skill");
        assertNotNull(tree);
        assertEquals("empty-skill", tree.getName());
        assertTrue(tree.isDir());
    }

    @Test
    void testReadFile() throws Exception {
        givenSkill("test-skill", "tester", "org-1", 0);
        Files.writeString(skillDir.resolve("SKILL.md"), "# Test Skill\n\nDescription");

        SkillFileContent content = service.readFile("test-skill", "SKILL.md");

        assertTrue(content.isExists());
        assertEquals("SKILL.md", content.getPath());
        assertEquals("# Test Skill\n\nDescription", content.getContent());
        assertTrue(content.getSize() > 0);
    }

    @Test
    void testReadNonExistentFile() {
        givenSkill("test-skill", "tester", "org-1", 0);
        SkillFileContent content = service.readFile("test-skill", "nonexistent.txt");

        assertFalse(content.isExists());
        assertNotNull(content.getError());
    }

    @Test
    void testReadPathTraversal() {
        givenSkill("test-skill", "tester", "org-1", 0);
        SkillFileContent content = service.readFile("test-skill", "../secret.txt");

        assertFalse(content.isExists());
        assertTrue(content.getError().contains("路径遍历"));
    }

    @Test
    void testCreateAndReadFile() throws Exception {
        givenSkill("test-skill", "tester", "org-1", 0);
        service.createNode("test-skill", "newfile.md", false, "# New File", "tester");

        SkillFileContent content = service.readFile("test-skill", "newfile.md");
        assertTrue(content.isExists());
        assertEquals("# New File", content.getContent());
    }

    @Test
    void testCreateDirectory() throws Exception {
        givenSkill("test-skill", "tester", "org-1", 0);
        service.createNode("test-skill", "subdir", true, null, "tester");

        assertTrue(Files.exists(skillDir.resolve("subdir")));
        assertTrue(Files.isDirectory(skillDir.resolve("subdir")));
    }

    @Test
    void testUpdateFile() throws Exception {
        givenSkill("test-skill", "tester", "org-1", 0);
        Files.writeString(skillDir.resolve("updatable.md"), "Original");

        service.updateFile("test-skill", "updatable.md", "Updated", "tester");

        String content = Files.readString(skillDir.resolve("updatable.md"));
        assertEquals("Updated", content);
    }

    @Test
    void testDeleteFile() throws Exception {
        givenSkill("test-skill", "tester", "org-1", 0);
        Files.writeString(skillDir.resolve("deletable.md"), "To be deleted");

        service.deleteNode("test-skill", "deletable.md", "tester");

        assertFalse(Files.exists(skillDir.resolve("deletable.md")));
    }

    @Test
    void testDeleteDirectory() throws Exception {
        givenSkill("test-skill", "tester", "org-1", 0);
        Files.createDirectories(skillDir.resolve("subdir/subsub"));
        Files.writeString(skillDir.resolve("subdir").resolve("file.txt"), "content");

        service.deleteNode("test-skill", "subdir", "tester");

        assertFalse(Files.exists(skillDir.resolve("subdir")));
    }

    @Test
    void testExportZip() throws Exception {
        givenSkill("test-skill", "tester", "org-1", 0);
        Files.writeString(skillDir.resolve("SKILL.md"), "# Test");
        Files.writeString(skillDir.resolve("code.py"), "print('hello')");

        File zip = service.exportZip("test-skill");

        assertNotNull(zip);
        assertTrue(zip.exists());
        assertTrue(zip.length() > 0);
        zip.delete();
    }

    @Test
    void testPathTraversalPrevention() {
        givenSkill("test-skill", "tester", "org-1", 0);
        assertThrows(SecurityException.class, () ->
            service.createNode("test-skill", "../../evil.md", false, "evil", "tester"));
    }

    // ========== M1-1 鉴权：读 = visible，写/删 = manageable ==========

    @Test
    void testGetTree_NonOwnerForbidden() {
        givenSkill("test-skill", "someone-else", "org-9", 0);
        loginAs("other", List.of());

        BusinessException e = assertThrows(BusinessException.class, () -> service.getTree("test-skill"));
        assertEquals(403, e.getCode());
        assertEquals("无权访问该技能", e.getMessage());
    }

    @Test
    void testReadFile_NonOwnerForbidden() {
        givenSkill("test-skill", "someone-else", "org-9", 0);
        loginAs("other", List.of());

        BusinessException e = assertThrows(BusinessException.class,
            () -> service.readFile("test-skill", "SKILL.md"));
        assertEquals(403, e.getCode());
        assertEquals("无权访问该技能", e.getMessage());
    }

    @Test
    void testExportZip_NonOwnerForbidden() {
        givenSkill("test-skill", "someone-else", "org-9", 0);
        loginAs("other", List.of());

        BusinessException e = assertThrows(BusinessException.class, () -> service.exportZip("test-skill"));
        assertEquals(403, e.getCode());
        assertEquals("无权访问该技能", e.getMessage());
    }

    @Test
    void testCreateNode_NonOwnerForbidden() throws Exception {
        givenSkill("test-skill", "someone-else", "org-9", 0);
        loginAs("other", List.of());

        BusinessException e = assertThrows(BusinessException.class,
            () -> service.createNode("test-skill", "hack.md", false, "evil", "other"));
        assertEquals(403, e.getCode());
        assertEquals("无权管理该技能", e.getMessage());
        assertFalse(Files.exists(skillDir.resolve("hack.md")), "非属主写入不应产生文件");
    }

    @Test
    void testUpdateFile_NonOwnerForbidden() throws Exception {
        givenSkill("test-skill", "someone-else", "org-9", 0);
        loginAs("other", List.of());
        Files.writeString(skillDir.resolve("victim.md"), "Original");

        BusinessException e = assertThrows(BusinessException.class,
            () -> service.updateFile("test-skill", "victim.md", "hacked", "other"));
        assertEquals(403, e.getCode());
        assertEquals("无权管理该技能", e.getMessage());
        assertEquals("Original", Files.readString(skillDir.resolve("victim.md")), "非属主更新不应改动文件");
    }

    @Test
    void testDeleteNode_NonOwnerForbidden() throws Exception {
        givenSkill("test-skill", "someone-else", "org-9", 0);
        loginAs("other", List.of());
        Files.writeString(skillDir.resolve("victim.md"), "Keep me");

        BusinessException e = assertThrows(BusinessException.class,
            () -> service.deleteNode("test-skill", "victim.md", "other"));
        assertEquals(403, e.getCode());
        assertEquals("无权管理该技能", e.getMessage());
        assertTrue(Files.exists(skillDir.resolve("victim.md")), "非属主删除不应删掉文件");
    }

    @Test
    void testGetTree_BuiltinReadableByAnyUser() throws Exception {
        // 内置技能 creator=system / isBuiltin=1 → 全员可读
        givenSkill("test-skill", "system", null, 1);
        loginAs("random-user", List.of());

        SkillFileTree tree = service.getTree("test-skill");
        assertNotNull(tree);
        assertEquals("test-skill", tree.getName());
    }

    @Test
    void testCreateNode_BuiltinForbiddenForPlainUser() {
        // 内置技能普通用户不可写
        givenSkill("test-skill", "system", null, 1);
        loginAs("plain-user", List.of());

        BusinessException e = assertThrows(BusinessException.class,
            () -> service.createNode("test-skill", "x.md", false, "c", "plain-user"));
        assertEquals(403, e.getCode());
        assertEquals("无权管理该技能", e.getMessage());
    }

    @Test
    void testCreateNode_BuiltinAllowedForWildcardPermission() throws Exception {
        // 内置技能 * 权限可写
        givenSkill("test-skill", "system", null, 1);
        loginAs("admin", List.of("*"));

        service.createNode("test-skill", "admin-file.md", false, "content", "admin");

        assertTrue(Files.exists(skillDir.resolve("admin-file.md")));
    }

    @Test
    void testCreateNode_BuiltinAllowedForApiToken() throws Exception {
        // 内置技能 API Token（程序化访问）可写
        givenSkill("test-skill", "system", null, 1);
        loginAs("api-token:tok-1", List.of());

        service.createNode("test-skill", "token-file.md", false, "content", "api-token:tok-1");

        assertTrue(Files.exists(skillDir.resolve("token-file.md")));
    }

    @Test
    void testGetTree_UnknownSlugReportsNotFound() {
        when(skillMapper.selectBySlug("ghost")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.getTree("ghost"));
        assertEquals(404, e.getCode());
        assertTrue(e.getMessage().contains("技能不存在"));
    }

    @Test
    void testCreateNode_SlugTraversalRejected() {
        // slug 形如 ../x 必须被 Slug 白名单拒绝（不允许触达查库与文件系统）
        BusinessException e = assertThrows(BusinessException.class,
            () -> service.createNode("../x", "evil.md", false, "evil", "tester"));
        assertTrue(e.getMessage().contains("slug"));
    }

    // ========== M3-4（D10，裁决 3A）：根 SKILL.md 为正文事实源——写盘后回写 DB ==========

    private static final String NEW_SKILL_MD = "---\nname: 改名技能\nslug: test-skill\n"
        + "description: 新描述\nversion: 2.0.0\ncategory: generation\n"
        + "icon: \"#00ff00\"\n---\n\n新正文";

    @Test
    void testUpdateFile_RootSkillMdSyncedToDb() throws Exception {
        givenSkill("test-skill", "tester", "org-1", 0);
        Skill record = skillMapper.selectBySlug("test-skill");
        record.setName("旧名");
        Files.writeString(skillDir.resolve("SKILL.md"), "old");

        service.updateFile("test-skill", "SKILL.md", NEW_SKILL_MD, "tester");

        assertEquals(NEW_SKILL_MD, Files.readString(skillDir.resolve("SKILL.md")));
        org.mockito.Mockito.verify(skillMapper).updateById(record);
        assertEquals("改名技能", record.getName(), "name 应随 frontmatter 回写");
        assertEquals("新描述", record.getDescription());
        assertEquals("2.0.0", record.getVersion());
        assertEquals("generation", record.getCategory());
        assertEquals("#00ff00", record.getIcon());
        assertEquals(NEW_SKILL_MD, record.getContent(), "content 应为 SKILL.md 原文");
        assertNotNull(record.getContentHash(), "contentHash 应为回写后 MD5");
    }

    @Test
    void testUpdateFile_SubPathSkippedDbSync() throws Exception {
        givenSkill("test-skill", "tester", "org-1", 0);
        Files.writeString(skillDir.resolve("notes.md"), "old");

        service.updateFile("test-skill", "notes.md", "new", "tester");

        assertEquals("new", Files.readString(skillDir.resolve("notes.md")));
        org.mockito.Mockito.verify(skillMapper, never()).updateById(org.mockito.ArgumentMatchers.any(Skill.class));
    }

    @Test
    void testUpdateFile_InvalidSkillMdRejectedAndFileUntouched() throws Exception {
        givenSkill("test-skill", "tester", "org-1", 0);
        Files.writeString(skillDir.resolve("SKILL.md"), "old-valid");

        assertThrows(RuntimeException.class,
            () -> service.updateFile("test-skill", "SKILL.md", "no frontmatter here", "tester"),
            "解析失败应抛错");

        assertEquals("old-valid", Files.readString(skillDir.resolve("SKILL.md")),
            "先解析后写盘：解析失败文件不得被改写");
        org.mockito.Mockito.verify(skillMapper, never()).updateById(org.mockito.ArgumentMatchers.any(Skill.class));
    }

    @Test
    void testCreateNode_RootSkillMdSyncedToDb() throws Exception {
        givenSkill("test-skill", "tester", "org-1", 0);

        service.createNode("test-skill", "SKILL.md", false, NEW_SKILL_MD, "tester");

        assertTrue(Files.exists(skillDir.resolve("SKILL.md")));
        org.mockito.Mockito.verify(skillMapper).updateById(org.mockito.ArgumentMatchers.any(Skill.class));
    }
}
