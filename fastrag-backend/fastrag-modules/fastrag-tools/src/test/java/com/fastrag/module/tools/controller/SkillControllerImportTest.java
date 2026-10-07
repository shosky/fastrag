package com.fastrag.module.tools.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.tools.entity.SkillInstallDraft;
import com.fastrag.module.tools.service.SkillDraftService;
import com.fastrag.module.tools.service.SkillFileService;
import com.fastrag.module.tools.service.SkillService;
import com.fastrag.module.tools.skill.SkillDependencyValidator;
import com.fastrag.security.filter.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SkillController 导入两步化（F3，docs/plan/skill-feature-batch.md）用例：
 * upload 仅建草稿返回预览（不安装）；prepare 保留兼容。
 */
@ExtendWith(MockitoExtension.class)
class SkillControllerImportTest {

    @Mock
    private SkillService svc;
    @Mock
    private SkillDependencyValidator dependencyValidator;
    @Mock
    private SkillFileService skillFileService;
    @Mock
    private SkillDraftService skillDraftService;

    @InjectMocks
    private SkillController controller;

    @BeforeEach
    void setUp() {
        // SecurityUtil.getCurrentUserId() 取当前用户，单测无认证过滤器须显式装配
        LoginUser user = LoginUser.builder()
            .userId("uploader")
            .username("uploader")
            .orgId("org-1")
            .permissions(List.of())
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------- 1. [两步化-upload] multipart 上传时 [建草稿返回预览且不安装] ----------

    @Test
    void 两步化upload_multipart上传时_建草稿返回预览且不安装() throws Exception {
        byte[] zipBytes = "PK zip-bytes".getBytes(StandardCharsets.UTF_8);
        SkillInstallDraft draft = new SkillInstallDraft();
        draft.setDraftId("d-1");
        draft.setSourceType("upload");
        draft.setSource("pack.zip");
        draft.setExpiresAt(LocalDateTime.now().plusHours(1));
        SkillInstallDraft.DraftItem item = new SkillInstallDraft.DraftItem();
        item.setSlug("web");
        item.setName("Web");
        item.setDescription("Web skill");
        item.setSuccess(true);
        draft.setItems(List.of(item));
        when(skillDraftService.prepareUpload("pack.zip", zipBytes, "uploader")).thenReturn(draft);

        MockMultipartFile file = new MockMultipartFile(
            "file", "pack.zip", "application/zip", zipBytes);
        ApiResponse<?> resp = controller.uploadImport(file);

        assertEquals(200, resp.getCode());
        assertNotNull(resp.getData());
        assertSame(draft, resp.getData());
        assertEquals("d-1", draft.getDraftId());
        assertEquals(1, draft.getItems().size());
        assertNotNull(draft.getExpiresAt());
        verify(skillDraftService).prepareUpload("pack.zip", zipBytes, "uploader");
        // 关键断言：upload 仅建草稿，绝不触发安装
        verify(skillDraftService, never()).confirmDraft(anyString(), anyMap(), anyString());
    }

    // ---------- 2. [两步化-upload] 文件读取失败时 [返回 400 业务错误] ----------

    @Test
    void 两步化upload_文件读取失败时_返回400业务错误() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("bad.zip");
        when(file.getBytes()).thenThrow(new java.io.IOException("disk error"));

        ApiResponse<?> resp = controller.uploadImport(file);

        assertEquals(400, resp.getCode());
        verify(skillDraftService, never()).prepareUpload(anyString(), any(), anyString());
        verify(skillDraftService, never()).confirmDraft(anyString(), anyMap(), anyString());
    }
}
