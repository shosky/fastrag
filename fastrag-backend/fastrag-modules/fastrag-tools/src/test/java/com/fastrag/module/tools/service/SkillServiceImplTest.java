package com.fastrag.module.tools.service;

import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.entity.SkillDependency;
import com.fastrag.module.tools.entity.SkillScope;
import com.fastrag.module.tools.mapper.SkillDependencyMapper;
import com.fastrag.module.tools.mapper.SkillMapper;
import com.fastrag.module.tools.mapper.SkillScopeMapper;
import com.fastrag.module.tools.service.impl.SkillServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private SkillServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SkillServiceImpl(mapper, dependencyMapper, scopeMapper);
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
}
