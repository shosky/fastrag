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
}
