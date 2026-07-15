package com.fastrag.module.tools.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.tools.entity.McpService;
import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.entity.SkillDependencyInfo;
import com.fastrag.module.tools.entity.Tool;
import com.fastrag.module.tools.mapper.McpServiceMapper;
import com.fastrag.module.tools.mapper.SkillMapper;
import com.fastrag.module.tools.mapper.ToolMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillDependencyValidatorTest {

    @Mock
    private ToolMapper toolMapper;
    @Mock
    private McpServiceMapper mcpServiceMapper;
    @Mock
    private SkillMapper skillMapper;

    private SkillDependencyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SkillDependencyValidator(toolMapper, mcpServiceMapper, skillMapper);
    }

    @Test
    void testValidateValidToolDependency() {
        when(toolMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        List<SkillDependencyInfo> deps = List.of(
            createDep("tool", "tavily_search", false)
        );

        SkillDependencyValidator.ValidationResult result = validator.validate("skill-1", deps);

        assertTrue(result.getErrors().isEmpty());
        assertEquals(1, result.getValidDeps().size());
    }

    @Test
    void testValidateInvalidToolDependency() {
        when(toolMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        List<SkillDependencyInfo> deps = List.of(
            createDep("tool", "nonexistent_tool", false)
        );

        SkillDependencyValidator.ValidationResult result = validator.validate("skill-1", deps);

        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().get(0).contains("不存在"));
    }

    @Test
    void testValidateValidMcpDependency() {
        McpService svc = new McpService();
        svc.setName("filesystem");
        svc.setEnabled(1);
        when(mcpServiceMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(svc));

        List<SkillDependencyInfo> deps = List.of(
            createDep("mcp", "filesystem", true)
        );

        SkillDependencyValidator.ValidationResult result = validator.validate("skill-1", deps);

        assertTrue(result.getErrors().isEmpty());
        assertEquals(1, result.getValidDeps().size());
    }

    @Test
    void testValidateSelfSkillDependency() {
        Skill self = new Skill();
        self.setId("skill-1");
        self.setName("self-skill");
        when(skillMapper.selectById("skill-1")).thenReturn(self);

        List<SkillDependencyInfo> deps = List.of(
            createDep("skill", "self-skill", false)
        );

        SkillDependencyValidator.ValidationResult result = validator.validate("skill-1", deps);

        // 自引用会被检测为 "不能依赖自身"
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("self-skill")));
    }

    @Test
    void testValidateDuplicateDependency() {
        List<SkillDependencyInfo> deps = List.of(
            createDep("tool", "duplicate_tool", false),
            createDep("tool", "duplicate_tool", false)
        );

        SkillDependencyValidator.ValidationResult result = validator.validate("skill-1", deps);

        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("重复")));
    }

    @Test
    void testValidateNullDeps() {
        SkillDependencyValidator.ValidationResult result = validator.validate("skill-1", null);
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getValidDeps().isEmpty());
    }

    @Test
    void testValidateEmptyDeps() {
        SkillDependencyValidator.ValidationResult result = validator.validate("skill-1", List.of());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getValidDeps().isEmpty());
    }

    @Test
    void testValidateUnknownType() {
        List<SkillDependencyInfo> deps = List.of(
            createDep("unknown_type", "something", false)
        );

        SkillDependencyValidator.ValidationResult result = validator.validate("skill-1", deps);

        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().get(0).contains("未知依赖类型"));
    }

    @Test
    void testGetDependencyOptions() {
        Tool tool = new Tool();
        tool.setName("test_tool");
        when(toolMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(tool));

        McpService svc = new McpService();
        svc.setName("test_mcp");
        when(mcpServiceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(svc));

        Skill sk = new Skill();
        sk.setName("test_skill");
        when(skillMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sk));

        SkillDependencyValidator.DependencyOptions opts = validator.getDependencyOptions(null);

        assertFalse(opts.getTools().isEmpty());
        assertTrue(opts.getTools().contains("test_tool"));
        assertTrue(opts.getMcpServices().contains("test_mcp"));
        assertTrue(opts.getSkills().contains("test_skill"));
    }

    @Test
    void testModelDependencyIsSkipped() {
        List<SkillDependencyInfo> deps = List.of(
            createDep("model", "gpt-4", false)
        );

        SkillDependencyValidator.ValidationResult result = validator.validate("skill-1", deps);

        assertTrue(result.getErrors().isEmpty());
        assertEquals(1, result.getValidDeps().size());
    }

    @Test
    void testEmptyNameReturnsError() {
        List<SkillDependencyInfo> deps = List.of(
            createDep("tool", "", false)
        );

        SkillDependencyValidator.ValidationResult result = validator.validate("skill-1", deps);

        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().get(0).contains("不能为空"));
    }

    @Test
    void testValidSkillDependency() {
        Skill target = new Skill();
        target.setName("other-skill");
        target.setEnabled(1);
        when(skillMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(target));

        List<SkillDependencyInfo> deps = List.of(
            createDep("skill", "other-skill", true)
        );

        SkillDependencyValidator.ValidationResult result = validator.validate("skill-1", deps);

        assertTrue(result.getErrors().isEmpty());
        assertEquals(1, result.getValidDeps().size());
    }

    private SkillDependencyInfo createDep(String type, String name, boolean required) {
        SkillDependencyInfo dep = new SkillDependencyInfo();
        dep.setType(type);
        dep.setName(name);
        dep.setRequired(required);
        return dep;
    }
}
