package com.fastrag.module.tools.service;

import com.fastrag.module.tools.entity.SkillFileContent;
import com.fastrag.module.tools.entity.SkillFileTree;
import com.fastrag.module.tools.mapper.SkillMapper;
import com.fastrag.module.tools.service.impl.SkillFileServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class SkillFileServiceImplTest {

    private SkillFileServiceImpl service;
    private Path tempDataDir;
    private Path skillDir;

    @BeforeEach
    void setUp() throws Exception {
        // 创建临时目录模拟数据目录
        tempDataDir = Files.createTempDirectory("skill-test-");
        skillDir = tempDataDir.resolve("skills").resolve("test-skill");
        Files.createDirectories(skillDir);

        service = new SkillFileServiceImpl(null);
        ReflectionTestUtils.setField(service, "dataDir", tempDataDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        // 清理临时目录
        if (tempDataDir != null) {
            Files.walk(tempDataDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
        }
    }

    @Test
    void testGetTree() throws Exception {
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
        SkillFileTree tree = service.getTree("non-existent");
        assertNotNull(tree);
        assertEquals("non-existent", tree.getName());
        assertTrue(tree.isDir());
    }

    @Test
    void testReadFile() throws Exception {
        Files.writeString(skillDir.resolve("SKILL.md"), "# Test Skill\n\nDescription");

        SkillFileContent content = service.readFile("test-skill", "SKILL.md");

        assertTrue(content.isExists());
        assertEquals("SKILL.md", content.getPath());
        assertEquals("# Test Skill\n\nDescription", content.getContent());
        assertTrue(content.getSize() > 0);
    }

    @Test
    void testReadNonExistentFile() {
        SkillFileContent content = service.readFile("test-skill", "nonexistent.txt");

        assertFalse(content.isExists());
        assertNotNull(content.getError());
    }

    @Test
    void testReadPathTraversal() {
        SkillFileContent content = service.readFile("test-skill", "../secret.txt");

        assertFalse(content.isExists());
        assertTrue(content.getError().contains("路径遍历"));
    }

    @Test
    void testCreateAndReadFile() throws Exception {
        service.createNode("test-skill", "newfile.md", false, "# New File", "tester");

        SkillFileContent content = service.readFile("test-skill", "newfile.md");
        assertTrue(content.isExists());
        assertEquals("# New File", content.getContent());
    }

    @Test
    void testCreateDirectory() throws Exception {
        service.createNode("test-skill", "subdir", true, null, "tester");

        assertTrue(Files.exists(skillDir.resolve("subdir")));
        assertTrue(Files.isDirectory(skillDir.resolve("subdir")));
    }

    @Test
    void testUpdateFile() throws Exception {
        Files.writeString(skillDir.resolve("updatable.md"), "Original");

        service.updateFile("test-skill", "updatable.md", "Updated", "tester");

        String content = Files.readString(skillDir.resolve("updatable.md"));
        assertEquals("Updated", content);
    }

    @Test
    void testDeleteFile() throws Exception {
        Files.writeString(skillDir.resolve("deletable.md"), "To be deleted");

        service.deleteNode("test-skill", "deletable.md", "tester");

        assertFalse(Files.exists(skillDir.resolve("deletable.md")));
    }

    @Test
    void testDeleteDirectory() throws Exception {
        Files.createDirectories(skillDir.resolve("subdir/subsub"));
        Files.writeString(skillDir.resolve("subdir").resolve("file.txt"), "content");

        service.deleteNode("test-skill", "subdir", "tester");

        assertFalse(Files.exists(skillDir.resolve("subdir")));
    }

    @Test
    void testExportZip() throws Exception {
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
        assertThrows(SecurityException.class, () ->
            service.createNode("test-skill", "../../evil.md", false, "evil", "tester"));
    }
}
