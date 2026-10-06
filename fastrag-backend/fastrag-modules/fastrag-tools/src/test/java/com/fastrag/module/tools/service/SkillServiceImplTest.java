package com.fastrag.module.tools.service;

import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.entity.SkillDependency;
import com.fastrag.module.tools.entity.SkillScope;
import com.fastrag.module.tools.mapper.SkillDependencyMapper;
import com.fastrag.module.tools.mapper.SkillMapper;
import com.fastrag.module.tools.mapper.SkillScopeMapper;
import com.fastrag.module.tools.service.impl.SkillServiceImpl;
import com.fastrag.security.filter.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SkillServiceImpl 单元测试.
 * 使用 Mockito 模拟 Mapper 层，测试 Service 的业务逻辑和全字段映射.
 */
@ExtendWith(MockitoExtension.class)
class SkillServiceImplTest {

    @Mock
    private SkillMapper mapper;
    @Mock
    private SkillDependencyMapper dependencyMapper;
    @Mock
    private SkillScopeMapper scopeMapper;
    @Mock
    private com.fastrag.module.tools.skill.SkillDependencyValidator dependencyValidator;

    private SkillServiceImpl service;

    @BeforeEach
    void setUp() {
        // M3-1 起注入 SkillAccessManager（分享可见性/降级归一）——用真实实现以验证判定语义
        // M3-2 起注入 SkillDependencyValidator（saveDependencies 先校验）
        service = new SkillServiceImpl(mapper, dependencyMapper, scopeMapper,
                new com.fastrag.module.tools.skill.SkillAccessManager(mapper), dependencyValidator);
        // M3-2：缺省让校验器全量放行（个别用例自行覆盖此打桩）；lenient 防未触发用例报 UnnecessaryStubbing
        org.mockito.Mockito.lenient()
            .when(dependencyValidator.validate(any(), any()))
            .thenAnswer(inv -> new com.fastrag.module.tools.skill.SkillDependencyValidator.ValidationResult(
                    inv.getArgument(1), List.of()));
        // Service 层经 SecurityUtil.getCurrentUser() 取当前用户（requireManage/可见性），
        // 单测无认证过滤器，须显式装配安全上下文（* 权限 = manageable 全通过）
        LoginUser user = new LoginUser();
        user.setUserId("u1");
        user.setUsername("tester");
        user.setPermissions(List.of("*"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testCreate_FullFields() {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("name", "Test Skill");
        form.put("identifier", "test_skill");
        form.put("slug", "test-skill");
        form.put("description", "A test skill");
        form.put("icon", "#ff0000");
        form.put("category", "retrieval");
        form.put("trigger", "When user asks for test");
        form.put("content", "# Test\n\nContent here");
        form.put("codeType", "python");
        form.put("code", "def run(): pass");
        form.put("inputs", "{\"query\": \"string\"}");
        form.put("outputs", "{\"result\": \"string\"}");
        form.put("author", "tester");
        form.put("version", "1.0.0");
        form.put("sourceType", "custom");

        List<Map<String, Object>> deps = List.of(
            Map.of("type", "tool", "name", "test_tool", "required", true)
        );
        form.put("dependencies", deps);

        List<Map<String, Object>> scopes = List.of(
            Map.of("id", "app1", "name", "Test App", "enabled", true)
        );
        form.put("scopes", scopes);

        when(mapper.insert(any())).thenAnswer(invocation -> {
            Skill s = invocation.getArgument(0);
            s.setId("skill-1");
            return 1;
        });

        Skill result = service.create(form);

        assertNotNull(result);
        assertEquals("Test Skill", result.getName());
        assertEquals("test-skill", result.getSlug());
        assertEquals("A test skill", result.getDescription());
        assertEquals("#ff0000", result.getIcon());
        assertEquals("retrieval", result.getCategory());
        assertEquals("custom", result.getSourceType());
        assertEquals("python", result.getCodeType());
        assertEquals("tester", result.getAuthor());
        assertEquals("1.0.0", result.getVersion());
        assertEquals(1, result.getEnabled().intValue());
        assertEquals(0, result.getIsBuiltin().intValue());

        // 验证依赖写入
        verify(dependencyMapper).deleteBySkillId("skill-1");
        verify(dependencyMapper).insert(any(SkillDependency.class));

        // 验证范围写入
        verify(scopeMapper).deleteBySkillId("skill-1");
        verify(scopeMapper).insert(any(SkillScope.class));
    }

    @Test
    void testCreate_MinimalFields() {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("name", "Minimal");
        form.put("slug", "minimal");
        form.put("description", "A minimal skill");

        when(mapper.insert(any())).thenAnswer(invocation -> {
            Skill s = invocation.getArgument(0);
            s.setId("skill-2");
            return 1;
        });

        Skill result = service.create(form);

        assertEquals("Minimal", result.getName());
        assertEquals("custom", result.getSourceType()); // 默认值
        assertEquals(1, result.getEnabled().intValue());
    }

    @Test
    void testUpdate_FullFields() {
        Skill existing = new Skill();
        existing.setId("skill-1");
        existing.setName("Old Name");
        when(mapper.selectById("skill-1")).thenReturn(existing);

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("name", "New Name");
        form.put("description", "New Description");
        form.put("category", "generation");
        form.put("icon", "#00ff00");
        form.put("content", "# New Content");
        form.put("enabled", 1);
        form.put("version", "2.0.0");

        List<Map<String, Object>> deps = List.of(
            Map.of("type", "model", "name", "gpt-4", "required", true)
        );
        form.put("dependencies", deps);

        Skill result = service.update("skill-1", form);

        assertNotNull(result);
        assertEquals("New Name", result.getName());
        assertEquals("New Description", result.getDescription());
        assertEquals("generation", result.getCategory());
        assertEquals("#00ff00", result.getIcon());
        assertEquals("2.0.0", result.getVersion());

        verify(dependencyMapper).deleteBySkillId("skill-1");
        verify(dependencyMapper).insert(any(SkillDependency.class));
    }

    @Test
    void testDelete_BuiltinSkillThrows() {
        Skill builtin = new Skill();
        builtin.setId("builtin-1");
        builtin.setIsBuiltin(1);
        when(mapper.selectById("builtin-1")).thenReturn(builtin);

        assertThrows(RuntimeException.class, () -> service.delete("builtin-1"));
        verify(mapper, never()).deleteById(anyString());
    }

    @Test
    void testDelete_CustomSkill() {
        Skill custom = new Skill();
        custom.setId("custom-1");
        custom.setIsBuiltin(0);
        when(mapper.selectById("custom-1")).thenReturn(custom);

        service.delete("custom-1");

        verify(dependencyMapper).deleteBySkillId("custom-1");
        verify(scopeMapper).deleteBySkillId("custom-1");
        verify(mapper).deleteById("custom-1");
    }

    @Test
    void testSetEnabled() {
        Skill s = new Skill();
        s.setId("skill-1");
        s.setEnabled(0);
        when(mapper.selectById("skill-1")).thenReturn(s);

        service.setEnabled("skill-1", true);

        assertEquals(1, s.getEnabled().intValue());
        verify(mapper).updateById(s);
    }

    @Test
    void testToggleEnabled() {
        Skill s = new Skill();
        s.setId("skill-1");
        s.setEnabled(0);
        when(mapper.selectById("skill-1")).thenReturn(s);

        service.toggleEnabled("skill-1");

        assertEquals(1, s.getEnabled().intValue());
        verify(mapper).updateById(s);
    }

    @Test
    void testUpdateDependencies() {
        Skill s = new Skill();
        s.setId("skill-1");
        when(mapper.selectById("skill-1")).thenReturn(s);

        List<Map<String, Object>> deps = List.of(
            Map.of("type", "tool", "name", "tool1", "required", false)
        );

        service.updateDependencies("skill-1", deps);

        verify(dependencyMapper).deleteBySkillId("skill-1");
        verify(dependencyMapper).insert(any(SkillDependency.class));
        verify(mapper).updateById(s);
        // 验证 skill 表的 dependencies JSON 字段也被更新
        assertNotNull(s.getDependencies());
        assertEquals(1, s.getDependencies().size());
        assertTrue(s.getDependencies().get(0).contains("tool1"));
    }

    @Test
    void testUpdateShareConfig() {
        Skill s = new Skill();
        s.setId("skill-1");
        when(mapper.selectById("skill-1")).thenReturn(s);

        Map<String, Object> config = Map.of("accessLevel", "global");

        service.updateShareConfig("skill-1", config);

        assertNotNull(s.getShareConfig());
        assertEquals("global", s.getShareConfig().get("accessLevel"));
        verify(mapper).updateById(s);
    }

    // ========== M1-2 slug 白名单接线 ==========

    @Test
    void testCreate_InvalidSlugRejected() {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("name", "Evil");
        form.put("slug", "../evil");
        form.put("description", "d");

        com.fastrag.common.exception.BusinessException e =
            assertThrows(com.fastrag.common.exception.BusinessException.class, () -> service.create(form));
        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("slug"), "消息应指明 slug 字段");
    }

    @Test
    void testCreate_SlugDerivedFromNameWhenAbsent() {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("name", "My Cool Skill");
        form.put("description", "d");

        when(mapper.insert(any())).thenAnswer(invocation -> {
            Skill s = invocation.getArgument(0);
            s.setId("skill-3");
            return 1;
        });

        Skill result = service.create(form);

        assertEquals("my-cool-skill", result.getSlug());
    }

    @Test
    void testCreate_SlugDerivationFailurePromptsExplicitSlug() {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("name", "中文名字");
        form.put("description", "d");

        com.fastrag.common.exception.BusinessException e =
            assertThrows(com.fastrag.common.exception.BusinessException.class, () -> service.create(form));
        assertTrue(e.getMessage().contains("slug"), "应提示显式提供 slug");
    }

    // ===== M2-2 前置：listByIds（按主键批量取技能实体，供绑定解析） =====

    @Test
    void testListByIds_BatchSelect() {
        Skill s1 = new Skill();
        s1.setId("id-1");
        s1.setSlug("alpha");
        when(mapper.selectBatchIds(List.of("id-1", "id-2"))).thenReturn(List.of(s1));

        List<Skill> out = service.listByIds(List.of("id-1", "id-2"));

        assertEquals(1, out.size());
        assertEquals("alpha", out.get(0).getSlug());
        verify(mapper).selectBatchIds(List.of("id-1", "id-2"));
    }

    @Test
    void testListByIds_EmptyIdsSkipsQuery() {
        assertTrue(service.listByIds(List.of()).isEmpty());
        verify(mapper, never()).selectBatchIds(any());
    }

    // ========== M3-1（V1）：分享可见性接线（get/getBySlug/list/listAccessible 经 SkillAccessManager） ==========

    /** 装配普通用户（无 * 权限）安全上下文 */
    private void loginAsPlainUser(String userId, String orgId) {
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUsername(userId);
        user.setOrgId(orgId);
        user.setPermissions(List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
    }

    @Test
    void testGet_SharedGlobalVisibleToNonOwner() {
        loginAsPlainUser("stranger", "org-other");
        Skill s = new Skill();
        s.setId("s1");
        s.setCreator("someone-else");
        s.setOrgId("org-other-2");
        s.setIsBuiltin(0);
        s.setShareConfig(Map.of("accessLevel", "global"));
        when(mapper.selectById("s1")).thenReturn(s);

        Skill result = service.get("s1");

        assertNotNull(result, "shareConfig=global 的技能应对非属主可见（DataScope 之外经分享判定）");
    }

    @Test
    void testGetBySlug_SharedUserLevelVisibleToAllowedUid() {
        loginAsPlainUser("alice", "org-other");
        Skill s = new Skill();
        s.setId("s2");
        s.setSlug("shared-skill");
        s.setCreator("someone-else");
        s.setOrgId("org-other-2");
        s.setIsBuiltin(0);
        s.setShareConfig(Map.of("accessLevel", "user", "userUids", List.of("alice")));
        when(mapper.selectBySlug("shared-skill")).thenReturn(s);

        Skill result = service.getBySlug("shared-skill");

        assertNotNull(result, "userUids 命中当前用户应可见");
    }

    @Test
    void testGet_PrivateSkillStillForbiddenForStranger() {
        loginAsPlainUser("stranger", "org-other");
        Skill s = new Skill();
        s.setId("s3");
        s.setCreator("someone-else");
        s.setOrgId("org-other-2");
        s.setIsBuiltin(0);
        s.setShareConfig(null);
        when(mapper.selectById("s3")).thenReturn(s);

        assertThrows(com.fastrag.common.exception.BusinessException.class, () -> service.get("s3"),
            "无分享配置时维持 DataScope 不可见语义");
    }

    @Test
    void testList_SharedGlobalSkillIncludedForStranger() {
        loginAsPlainUser("stranger", "org-mine");
        Skill mine = new Skill();
        mine.setId("own");
        mine.setCreator("stranger");
        Skill shared = new Skill();
        shared.setId("shared");
        shared.setCreator("someone-else");
        shared.setOrgId("org-far");
        shared.setIsBuiltin(0);
        shared.setShareConfig(Map.of("accessLevel", "global"));
        Skill foreignPrivate = new Skill();
        foreignPrivate.setId("foreign");
        foreignPrivate.setCreator("someone-else");
        foreignPrivate.setOrgId("org-far");
        foreignPrivate.setIsBuiltin(0);
        when(mapper.selectList(any())).thenReturn(new ArrayList<>(List.of(mine, shared, foreignPrivate)));

        List<Skill> out = service.list(null, null);

        assertTrue(out.stream().anyMatch(s -> "shared".equals(s.getId())),
            "global 分享技能应出现在 list 结果中");
        assertFalse(out.stream().anyMatch(s -> "foreign".equals(s.getId())),
            "他人私有技能不应出现在 list 结果中");
    }

    @Test
    void testListAccessible_SharedGlobalSkillIncluded() {
        loginAsPlainUser("stranger", "org-mine");
        Skill shared = new Skill();
        shared.setId("shared");
        shared.setCreator("someone-else");
        shared.setOrgId("org-far");
        shared.setIsBuiltin(0);
        shared.setEnabled(1);
        shared.setShareConfig(Map.of("accessLevel", "global"));
        when(mapper.selectList(any())).thenReturn(new ArrayList<>(List.of(shared)));

        List<Skill> out = service.listAccessible(null, null, null);

        assertEquals(1, out.size());
        assertEquals("shared", out.get(0).getId());
    }

    // ========== M3-1（V2）：updateShareConfig 降级语义 + shareConfig 落库 ==========

    @Test
    void testUpdateShareConfig_PlainUserGlobalDemotedToUser() {
        loginAsPlainUser("u1", "org-1");
        Skill s = new Skill();
        s.setId("skill-1");
        when(mapper.selectById("skill-1")).thenReturn(s);

        service.updateShareConfig("skill-1", Map.of("accessLevel", "global"));

        assertEquals("user", s.getShareConfig().get("accessLevel"),
            "无 * 权限且非 API Token 提交 global 应强制降级 user");
        verify(mapper).updateById(s);
    }

    @Test
    void testUpdateShareConfig_AdminKeepsGlobal() {
        Skill s = new Skill();
        s.setId("skill-1");
        when(mapper.selectById("skill-1")).thenReturn(s);

        service.updateShareConfig("skill-1", Map.of("accessLevel", "global"));

        assertEquals("global", s.getShareConfig().get("accessLevel"),
            "* 权限用户可保持 global（setUp 已装配 * 权限上下文）");
    }

    // ========== M3-2（D9）：saveDependencies 先过 SkillDependencyValidator 校验 ==========

    @Test
    void testCreate_InvalidDependencyRejected() {
        when(dependencyValidator.validate(any(), any())).thenReturn(new com.fastrag.module.tools.skill.SkillDependencyValidator.ValidationResult(
                List.of(), List.of("tool:ghost - 工具不存在或未启用: ghost")));

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("name", "BadDeps");
        form.put("slug", "bad-deps");
        form.put("description", "d");
        form.put("dependencies", List.of(Map.of("type", "tool", "name", "ghost", "required", true)));

        com.fastrag.common.exception.BusinessException e =
            assertThrows(com.fastrag.common.exception.BusinessException.class, () -> service.create(form));
        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("依赖"), "错误消息应说明依赖非法");
        verify(dependencyMapper, never()).insert(any());
    }

    @Test
    void testUpdate_ValidDependenciesPassThroughValidator() {
        when(dependencyValidator.validate(any(), any())).thenAnswer(
                inv -> new com.fastrag.module.tools.skill.SkillDependencyValidator.ValidationResult(
                        inv.getArgument(1), List.of()));
        Skill existing = new Skill();
        existing.setId("skill-1");
        when(mapper.selectById("skill-1")).thenReturn(existing);

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("dependencies", List.of(Map.of("type", "tool", "name", "ok_tool", "required", true)));

        service.update("skill-1", form);

        verify(dependencyValidator).validate(any(), any());
        verify(dependencyMapper).insert(any(com.fastrag.module.tools.entity.SkillDependency.class));
    }
}
