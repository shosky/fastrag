package com.fastrag.module.agent.fs;

import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.service.SkillService;
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
class SkillReadonlyBackendTest {

    @Mock
    private SkillService skillService;

    private SkillReadonlyBackend backend;

    @BeforeEach
    void setUp() {
        backend = new SkillReadonlyBackend(skillService);
    }

    @Test
    void testGetMountPoint() {
        assertEquals("/home/gem/skills/", backend.getMountPoint());
    }

    @Test
    void testIsAllowedWrite() {
        assertFalse(backend.isAllowedWrite("/home/gem/skills/anything"));
    }

    @Test
    void testLsRoot() {
        Skill s1 = new Skill();
        s1.setSlug("web-search");
        s1.setEnabled(1);
        Skill s2 = new Skill();
        s2.setSlug("code-gen");
        s2.setEnabled(1);
        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(List.of(s1, s2));

        List<FileEntry> entries = backend.ls("/home/gem/skills");

        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(e -> "web-search".equals(e.getName())));
        assertTrue(entries.stream().anyMatch(e -> "code-gen".equals(e.getName())));
        assertTrue(entries.stream().allMatch(FileEntry::isDir));
    }

    @Test
    void testLsRootWithTrailingSlash() {
        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(List.of());

        List<FileEntry> entries = backend.ls("/home/gem/skills/");

        assertNotNull(entries);
    }

    @Test
    void testLsSkillDir() {
        Skill skill = new Skill();
        skill.setSlug("web-search");
        skill.setEnabled(1);
        when(skillService.getBySlug("web-search")).thenReturn(skill);

        List<FileEntry> entries = backend.ls("/home/gem/skills/web-search");

        assertEquals(1, entries.size());
        assertEquals("SKILL.md", entries.get(0).getName());
        assertFalse(entries.get(0).isDir());
    }

    @Test
    void testLsNonExistentSkill() {
        when(skillService.getBySlug("nonexistent")).thenReturn(null);

        List<FileEntry> entries = backend.ls("/home/gem/skills/nonexistent");

        assertTrue(entries.isEmpty());
    }

    @Test
    void testReadSkillMd() {
        Skill skill = new Skill();
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        skill.setContent("# Web Search\n\nSearch content");
        skill.setVersion("1.0.0");
        when(skillService.getBySlug("web-search")).thenReturn(skill);

        FileContent fc = backend.read("/home/gem/skills/web-search/SKILL.md");

        assertTrue(fc.isExists());
        assertNotNull(fc.getContent());
        assertTrue(fc.getContent().contains("联网搜索"));
        assertTrue(fc.getContent().contains("web-search"));
        assertTrue(fc.getContent().contains("Search the web"));
        assertTrue(fc.getContent().contains("Web Search"));
    }

    @Test
    void testReadSkillDir() {
        Skill skill = new Skill();
        skill.setSlug("test");
        skill.setName("Test");
        skill.setDescription("Test desc");
        when(skillService.getBySlug("test")).thenReturn(skill);

        // 读取技能根目录应该返回 SKILL.md 内容
        FileContent fc = backend.read("/home/gem/skills/test");

        assertTrue(fc.isExists());
        assertTrue(fc.getContent().contains("Test"));
    }

    @Test
    void testReadNonExistentSkill() {
        when(skillService.getBySlug("nonexistent")).thenReturn(null);

        FileContent fc = backend.read("/home/gem/skills/nonexistent/SKILL.md");

        assertFalse(fc.isExists());
        assertNotNull(fc.getError());
    }

    @Test
    void testReadNonSkillMdFile() {
        Skill skill = new Skill();
        skill.setSlug("test");
        skill.setName("Test");
        skill.setDescription("Test");
        when(skillService.getBySlug("test")).thenReturn(skill);

        FileContent fc = backend.read("/home/gem/skills/test/other.py");

        assertFalse(fc.isExists());
        assertTrue(fc.getError().contains("仅支持读取 SKILL.md"));
    }

    @Test
    void testReadInvalidPath() {
        FileContent fc = backend.read("/home/gem/other/file.txt");

        assertFalse(fc.isExists());
        assertTrue(fc.getError().contains("无效的技能路径"));
    }

    @Test
    void testGrep() {
        Skill s1 = new Skill();
        s1.setSlug("web-search");
        s1.setName("联网搜索");
        s1.setContent("Search API results");
        Skill s2 = new Skill();
        s2.setSlug("code-gen");
        s2.setName("代码生成");
        s2.setContent("Generate Python code");
        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(List.of(s1, s2));

        List<FileEntry> matches = backend.grep("Search", "/home/gem/skills");

        assertFalse(matches.isEmpty());
        assertTrue(matches.stream().anyMatch(m -> m.getName().contains("web-search")));
    }

    @Test
    void testNormalizePath() {
        // 通过 read 方法间接测试路径规范化
        Skill skill = new Skill();
        skill.setSlug("test");
        skill.setName("Test");
        skill.setDescription("Test");
        when(skillService.getBySlug("test")).thenReturn(skill);

        // Windows 反斜杠路径应正常工作
        FileContent fc = backend.read("home/gem/skills/test/SKILL.md");

        assertTrue(fc.isExists());
    }
}
