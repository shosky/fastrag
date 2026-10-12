package com.fastrag.module.tools.config;

import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.mapper.SkillMapper;
import com.fastrag.module.tools.skill.SkillMarkdownParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SkillDataInitializer 单元测试（M3-4 裁决 3A：DB content 唯一事实源，启动播种仅在 DB 无记录时执行）。
 * classpath 上有真实 builtin-skills（code-generate / kb-qa / web-search）供扫描。
 */
@ExtendWith(MockitoExtension.class)
class SkillDataInitializerTest {

    @Mock
    private SkillMapper skillMapper;

    private SkillDataInitializer initializer;
    private Path tempDataDir;

    @BeforeEach
    void setUp() throws Exception {
        initializer = new SkillDataInitializer(skillMapper, new SkillMarkdownParser());
        tempDataDir = Files.createTempDirectory("skill-init-test-");
        ReflectionTestUtils.setField(initializer, "dataDir", tempDataDir.toString());
        // 缺省全部无记录（个别用例覆盖）；lenient 防部分用例未触发全部 slug 查询
        lenient().when(skillMapper.selectBySlug(any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        if (tempDataDir != null) {
            try {
                Files.walk(tempDataDir).sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
            } catch (Exception ignored) {}
        }
    }

    @Test
    void run_SeedsNewSkillsWithDependenciesAndDirectory() {
        initializer.run();

        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(skillMapper, times(3)).insert(captor.capture()); // code-generate / kb-qa / web-search
        verify(skillMapper, never()).updateById(any());

        Skill webSearch = captor.getAllValues().stream()
            .filter(s -> "web-search".equals(s.getSlug())).findFirst().orElse(null);
        assertNotNull(webSearch, "web-search 内置技能应被播种");
        assertEquals(List.of("tool:tavily_search"), webSearch.getDependencies(),
            "frontmatter 依赖应解析进 dependencies JSON（M3-2）");
        assertEquals(List.of("FMP_API_KEY"), webSearch.getEnvs(),
            "frontmatter env 声明应解析进 envs JSON（F4；web-search v1.3.0 起声明 FMP_API_KEY）");
        assertEquals("builtin", webSearch.getSourceType());
        assertEquals(Integer.valueOf(1), webSearch.getIsBuiltin());
        // 播种须复制技能目录到运行时数据目录
        assertTrue(Files.exists(tempDataDir.resolve("skills").resolve("web-search").resolve("SKILL.md")),
            "播种应复制技能目录");
    }

    @Test
    void run_ExistingSkillSkippedEntirely() {
        Skill existing = new Skill();
        existing.setId("x1");
        existing.setSlug("web-search");
        existing.setName("旧名字");
        existing.setContentHash("old-hash");
        when(skillMapper.selectBySlug("web-search")).thenReturn(existing);

        initializer.run();

        // DB 已有 → 不更新 DB、不覆盖磁盘目录（裁决 3A：播种不覆盖）
        verify(skillMapper, never()).updateById(any());
        assertEquals("旧名字", existing.getName(), "已有记录不得被启动播种覆盖");
        assertFalse(Files.exists(tempDataDir.resolve("skills").resolve("web-search")),
            "已存在技能不应覆盖磁盘目录");
        // 其余两个新技能仍播种
        verify(skillMapper, times(2)).insert(any(Skill.class));
    }
}
