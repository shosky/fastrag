package com.fastrag.module.tools.controller;

import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillController 审计埋点测试（M1-4）.
 * 校验写端点均标注 @Loggable（category=operation，target 用 SpEL），
 * 文件端点使用新增的 skill_file_* 动作；discardDraft（删草稿非技能）不埋点。
 */
class SkillControllerAuditTest {

    private static Loggable loggable(String method, Class<?>... paramTypes) throws NoSuchMethodException {
        Method m = SkillController.class.getMethod(method, paramTypes);
        return m.getAnnotation(Loggable.class);
    }

    private static void assertOperation(Loggable l, ActionType action, String expectedTarget) {
        assertNotNull(l, "应标注 @Loggable");
        assertEquals(LogCategory.operation, l.category());
        assertEquals(action, l.action());
        if (expectedTarget != null) {
            assertEquals(expectedTarget, l.target(), "target 应用 SpEL 引用路径变量");
        }
    }

    @Test
    void create_auditedAsSkillCreated() throws Exception {
        assertOperation(loggable("create", Map.class), ActionType.skill_created, null);
    }

    @Test
    void update_auditedAsSkillUpdated() throws Exception {
        assertOperation(loggable("update", String.class, Map.class), ActionType.skill_updated, "#id");
    }

    @Test
    void delete_auditedAsSkillDeleted() throws Exception {
        assertOperation(loggable("delete", String.class), ActionType.skill_deleted, "#id");
    }

    @Test
    void toggle_auditedAsSkillUpdated() throws Exception {
        assertOperation(loggable("toggle", String.class), ActionType.skill_updated, "#id");
    }

    @Test
    void updateDependencies_auditedAsSkillUpdated() throws Exception {
        assertOperation(loggable("updateDependencies", String.class, List.class), ActionType.skill_updated, "#id");
    }

    @Test
    void updateShareConfig_auditedAsSkillUpdated() throws Exception {
        assertOperation(loggable("updateShareConfig", String.class, Map.class), ActionType.skill_updated, "#id");
    }

    @Test
    void setEnabled_auditedAsSkillUpdated() throws Exception {
        assertOperation(loggable("setEnabled", String.class, Map.class), ActionType.skill_updated, "#id");
    }

    @Test
    void prepareImport_auditedAsSkillCreated() throws Exception {
        assertOperation(loggable("prepareImport", org.springframework.web.multipart.MultipartFile.class),
            ActionType.skill_created, null);
    }

    @Test
    void confirmDraft_auditedAsSkillCreatedWithDraftTarget() throws Exception {
        assertOperation(loggable("confirmDraft", String.class, Map.class), ActionType.skill_created, "#draftId");
    }

    // ========== F3 导入两步化（2026-10-07 裁决） ==========

    @Test
    void uploadImport_notAudited() throws Exception {
        // upload 仅建草稿（可丢弃、非技能状态变更），不埋审计；skill_created 归 confirm 端点
        assertNull(loggable("uploadImport", org.springframework.web.multipart.MultipartFile.class),
            "uploadImport 不应埋 @Loggable");
    }

    @Test
    void prepareImport_markedDeprecated() throws Exception {
        // 单步 prepare 保留兼容但须标注 @Deprecated（两步化迁移标记）
        assertNotNull(SkillController.class
            .getMethod("prepareImport", org.springframework.web.multipart.MultipartFile.class)
            .getAnnotation(Deprecated.class), "prepareImport 应标 @Deprecated");
    }

    // ========== 文件端点：使用新增 skill_file_* 动作 ==========

    @Test
    void actionType_definesSkillFileActions() {
        assertNotNull(ActionType.valueOf("skill_file_created"));
        assertNotNull(ActionType.valueOf("skill_file_updated"));
        assertNotNull(ActionType.valueOf("skill_file_deleted"));
    }

    @Test
    void createFile_auditedAsSkillFileCreated() throws Exception {
        assertOperation(loggable("createFile", String.class, Map.class), ActionType.skill_file_created, "#slug");
    }

    @Test
    void updateFile_auditedAsSkillFileUpdated() throws Exception {
        assertOperation(loggable("updateFile", String.class, Map.class), ActionType.skill_file_updated, "#slug");
    }

    @Test
    void deleteFile_auditedAsSkillFileDeleted() throws Exception {
        assertOperation(loggable("deleteFile", String.class, String.class), ActionType.skill_file_deleted, "#slug");
    }

    @Test
    void discardDraft_notAudited() throws Exception {
        // 删草稿非删技能，不埋 skill 审计（裁决登记 Deviation）
        assertNull(loggable("discardDraft", String.class), "discardDraft 不应埋 @Loggable");
    }
}
