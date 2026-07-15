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

    private SkillDraftServiceImpl service;
    private Path tempDataDir;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        service = new SkillDraftServiceImpl(skillMapper, new SkillMarkdownParser(), om);
        tempDataDir = createTempDir("draft-test-");
        ReflectionTestUtils.setField(service, "dataDir", tempDataDir.toString());
    }

    @AfterEach
    void tearDown() {
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

        // mock slug 已存在 → 覆盖重装
        Skill old = new Skill();
        old.setId("old-id");
        old.setSlug("existing");
        when(skillMapper.selectBySlug("existing")).thenReturn(old);

        List<SkillInstallDraft.DraftItem> results = service.confirmDraft(draft.getDraftId(), null, "tester");

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).isSuccess(), "覆盖重装应成功");
        verify(skillMapper).deleteById("old-id");
        verify(skillMapper).insert(any(Skill.class));
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
