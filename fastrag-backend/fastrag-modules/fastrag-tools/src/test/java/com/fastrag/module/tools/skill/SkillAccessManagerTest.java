package com.fastrag.module.tools.skill;

import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.mapper.SkillMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillAccessManagerTest {

    @Mock
    private SkillMapper skillMapper;

    private SkillAccessManager manager;

    @BeforeEach
    void setUp() {
        manager = new SkillAccessManager(skillMapper);
    }

    @Test
    void testAdminAlwaysHasAccess() {
        Skill skill = new Skill();
        skill.setId("s1");
        skill.setIsBuiltin(0);
        skill.setShareConfig(Map.of("accessLevel", "user"));

        boolean result = manager.canAccess("user1", "admin", skill);

        assertTrue(result);
    }

    @Test
    void testSuperAdminAlwaysHasAccess() {
        Skill skill = new Skill();
        skill.setId("s1");

        boolean result = manager.canAccess("user1", "superadmin", skill);

        assertTrue(result);
    }

    @Test
    void testCreatorHasAccess() {
        Skill skill = new Skill();
        skill.setId("s1");
        skill.setIsBuiltin(0);
        when(skillMapper.selectCreatedBy("s1")).thenReturn("creator1");

        boolean result = manager.canAccess("creator1", "user", skill);

        assertTrue(result);
    }

    @Test
    void testBuiltinSkillGlobalAccess() {
        Skill skill = new Skill();
        skill.setId("s1");
        skill.setIsBuiltin(1);

        boolean result = manager.canAccess("anyone", "user", skill);

        assertTrue(result);
    }

    @Test
    void testGlobalShareConfig() {
        Skill skill = new Skill();
        skill.setId("s1");
        skill.setIsBuiltin(0);
        skill.setShareConfig(Map.of("accessLevel", "global"));
        when(skillMapper.selectCreatedBy("s1")).thenReturn("creator1");

        boolean result = manager.canAccess("other_user", "user", skill);

        assertTrue(result);
    }

    @Test
    void testUserLevelConfigWithAccess() {
        Skill skill = new Skill();
        skill.setId("s1");
        skill.setIsBuiltin(0);
        skill.setShareConfig(Map.of(
            "accessLevel", "user",
            "userUids", List.of("user1", "user2")
        ));
        when(skillMapper.selectCreatedBy("s1")).thenReturn("creator1");

        boolean result = manager.canAccess("user2", "user", skill);

        assertTrue(result);
    }

    @Test
    void testUserLevelConfigWithoutAccess() {
        Skill skill = new Skill();
        skill.setId("s1");
        skill.setIsBuiltin(0);
        skill.setShareConfig(Map.of(
            "accessLevel", "user",
            "userUids", List.of("user1", "user2")
        ));
        when(skillMapper.selectCreatedBy("s1")).thenReturn("creator1");

        boolean result = manager.canAccess("user3", "user", skill);

        assertFalse(result);
    }

    @Test
    void testNoShareConfig() {
        Skill skill = new Skill();
        skill.setId("s1");
        skill.setIsBuiltin(0);
        skill.setShareConfig(null);
        when(skillMapper.selectCreatedBy("s1")).thenReturn("creator1");

        // 非创建者不能访问
        boolean result = manager.canAccess("other", "user", skill);

        assertFalse(result);
    }

    @Test
    void testAdminCanManage() {
        Skill skill = new Skill();
        skill.setId("s1");

        boolean result = manager.canManage("user1", "admin", skill);

        assertTrue(result);
    }

    @Test
    void testCreatorCanManage() {
        Skill skill = new Skill();
        skill.setId("s1");
        when(skillMapper.selectCreatedBy("s1")).thenReturn("creator1");

        boolean result = manager.canManage("creator1", "user", skill);

        assertTrue(result);
    }

    @Test
    void testNonCreatorCannotManage() {
        Skill skill = new Skill();
        skill.setId("s1");
        when(skillMapper.selectCreatedBy("s1")).thenReturn("creator1");

        boolean result = manager.canManage("other", "user", skill);

        assertFalse(result);
    }

    @Test
    void testNormalizeShareConfigForUser() {
        Map<String, Object> config = Map.of("accessLevel", "global");
        Map<String, Object> result = manager.normalizeShareConfig(config, "user");

        assertEquals("user", result.get("accessLevel"));
    }

    @Test
    void testNormalizeShareConfigForAdmin() {
        Map<String, Object> config = Map.of("accessLevel", "global");
        Map<String, Object> result = manager.normalizeShareConfig(config, "admin");

        assertEquals("global", result.get("accessLevel"));
    }

    @Test
    void testNullUserIdCannotAccess() {
        Skill skill = new Skill();
        skill.setId("s1");

        boolean result = manager.canAccess(null, "user", skill);

        assertFalse(result);
    }

    // ========== M3-1（D2/V1）：shareVisible —— 分享可见性接线（global/department/user 三级） ==========

    @Test
    void testShareVisible_GlobalLevelVisibleToAll() {
        Skill skill = new Skill();
        skill.setId("s1");
        skill.setShareConfig(Map.of("accessLevel", "global"));

        com.fastrag.security.filter.LoginUser user = com.fastrag.security.filter.LoginUser.builder()
            .userId("stranger").username("u").orgId("org-x").permissions(List.of()).build();

        assertTrue(manager.shareVisible(user, skill), "global 级分享对全员可见");
    }

    @Test
    void testShareVisible_DepartmentLevelMatchesOrgId() {
        Skill skill = new Skill();
        skill.setId("s1");
        skill.setShareConfig(Map.of("accessLevel", "department", "departmentIds", List.of("org-1", "org-2")));

        com.fastrag.security.filter.LoginUser insider = com.fastrag.security.filter.LoginUser.builder()
            .userId("u1").username("u").orgId("org-1").permissions(List.of()).build();
        com.fastrag.security.filter.LoginUser outsider = com.fastrag.security.filter.LoginUser.builder()
            .userId("u2").username("u").orgId("org-9").permissions(List.of()).build();

        assertTrue(manager.shareVisible(insider, skill), "user.orgId ∈ departmentIds 应可见");
        assertFalse(manager.shareVisible(outsider, skill), "user.orgId ∉ departmentIds 不可见");
    }

    @Test
    void testShareVisible_UserLevelMatchesUid() {
        Skill skill = new Skill();
        skill.setId("s1");
        skill.setShareConfig(Map.of("accessLevel", "user", "userUids", List.of("alice", "bob")));

        com.fastrag.security.filter.LoginUser alice = com.fastrag.security.filter.LoginUser.builder()
            .userId("alice").username("u").orgId("org-1").permissions(List.of()).build();
        com.fastrag.security.filter.LoginUser carol = com.fastrag.security.filter.LoginUser.builder()
            .userId("carol").username("u").orgId("org-1").permissions(List.of()).build();

        assertTrue(manager.shareVisible(alice, skill), "user.userId ∈ userUids 应可见");
        assertFalse(manager.shareVisible(carol, skill), "不在 userUids 不可见");
    }

    @Test
    void testShareVisible_MissingOrMalformedConfigNotVisible() {
        // 缺省（无配置）按不可见
        Skill noConfig = new Skill();
        noConfig.setId("s1");
        noConfig.setShareConfig(null);
        // 未知 accessLevel 按不可见
        Skill unknownLevel = new Skill();
        unknownLevel.setId("s2");
        unknownLevel.setShareConfig(Map.of("accessLevel", "weird"));
        // department 级但 departmentIds 解析失败（非列表）按不可见
        Skill malformed = new Skill();
        malformed.setId("s3");
        malformed.setShareConfig(Map.of("accessLevel", "department", "departmentIds", "not-a-list"));

        com.fastrag.security.filter.LoginUser user = com.fastrag.security.filter.LoginUser.builder()
            .userId("u1").username("u").orgId("org-1").permissions(List.of()).build();

        assertFalse(manager.shareVisible(user, noConfig), "无分享配置不可见");
        assertFalse(manager.shareVisible(user, unknownLevel), "未知 accessLevel 不可见");
        assertFalse(manager.shareVisible(user, malformed), "departmentIds 解析失败按不可见");
    }

    @Test
    void testShareVisible_NullUserNotVisible() {
        Skill skill = new Skill();
        skill.setId("s1");
        skill.setShareConfig(Map.of("accessLevel", "global"));

        assertFalse(manager.shareVisible(null, skill));
    }

    // ========== M3-1（V2）：normalizeShareConfig(LoginUser) 降级语义（无 * 权限且非 API Token → 降 user） ==========

    @Test
    void testNormalizeShareConfig_PlainUserDemotedToUserLevel() {
        com.fastrag.security.filter.LoginUser user = com.fastrag.security.filter.LoginUser.builder()
            .userId("u1").username("u").orgId("org-1").permissions(List.of()).build();

        Map<String, Object> result = manager.normalizeShareConfig(
            Map.of("accessLevel", "global", "userUids", List.of("a")), user);

        assertEquals("user", result.get("accessLevel"), "无 * 权限普通用户提交 global 应降级 user");
    }

    @Test
    void testNormalizeShareConfig_WildcardPermissionKeepsGlobal() {
        com.fastrag.security.filter.LoginUser admin = com.fastrag.security.filter.LoginUser.builder()
            .userId("admin").username("a").orgId("org-1").permissions(List.of("*")).build();

        Map<String, Object> result = manager.normalizeShareConfig(
            Map.of("accessLevel", "global"), admin);

        assertEquals("global", result.get("accessLevel"), "* 权限可保持 global");
    }

    @Test
    void testNormalizeShareConfig_ApiTokenKeepsGlobal() {
        com.fastrag.security.filter.LoginUser token = com.fastrag.security.filter.LoginUser.builder()
            .userId("api-token:tok-1").username("t").orgId("org-1").permissions(List.of()).build();

        Map<String, Object> result = manager.normalizeShareConfig(
            Map.of("accessLevel", "department", "departmentIds", List.of("org-1")), token);

        assertEquals("department", result.get("accessLevel"), "API Token 可保持 department");
    }
}
