package com.fastrag.module.tools.controller;

/**
 * 技能（Skill）管理控制器。
 *
 * <p>提供技能的完整生命周期管理，包括 CRUD、文件管理、安装导入/导出、
 * 依赖管理和分享配置等功能。技能是 Agent 可调用的能力单元，
 * 支持 builtin（内置）和 custom（自定义）两种来源类型。</p>
 *
 * <h3>REST API 端点：</h3>
 * <ul>
 *   <li>{@code GET    /api/skills} - 列出所有技能（支持 keyword/category 筛选）</li>
 *   <li>{@code GET    /api/skills/accessible} - 列出用户可访问的已启用技能</li>
 *   <li>{@code GET    /api/skills/builtin} - 列出内置技能</li>
 *   <li>{@code GET    /api/skills/{id}} - 获取单个技能详情</li>
 *   <li>{@code GET    /api/skills/slug/{slug}} - 按 slug 获取技能</li>
 *   <li>{@code POST   /api/skills} - 创建技能</li>
 *   <li>{@code PUT    /api/skills/{id}} - 更新技能</li>
 *   <li>{@code DELETE /api/skills/{id}} - 删除技能</li>
 *   <li>{@code POST   /api/skills/{id}/toggle} - 切换技能启用状态</li>
 *   <li>{@code PUT    /api/skills/{id}/dependencies} - 更新技能依赖</li>
 *   <li>{@code PUT    /api/skills/{id}/share-config} - 更新技能分享配置</li>
 *   <li>{@code PUT    /api/skills/{id}/enabled} - 设置技能启用状态</li>
 *   <li>{@code GET    /api/skills/{id}/dependencies} - 获取技能依赖列表</li>
 *   <li>{@code GET    /api/skills/{id}/scopes} - 获取技能作用域列表</li>
 *   <li>{@code GET    /api/skills/dependency-options} - 获取依赖选项（前端下拉）</li>
 * </ul>
 *
 * <h3>技能文件管理：</h3>
 * <ul>
 *   <li>{@code GET    /api/skills/{slug}/tree} - 获取技能文件树</li>
 *   <li>{@code GET    /api/skills/{slug}/file} - 读取技能文件内容</li>
 *   <li>{@code POST   /api/skills/{slug}/file} - 创建文件/目录</li>
 *   <li>{@code PUT    /api/skills/{slug}/file} - 更新文件内容</li>
 *   <li>{@code DELETE /api/skills/{slug}/file} - 删除文件/目录</li>
 *   <li>{@code GET    /api/skills/{slug}/export} - 导出技能为 ZIP</li>
 * </ul>
 *
 * <h3>安装草稿流水线：</h3>
 * <ul>
 *   <li>{@code POST   /api/skills/import/prepare} - 上传 ZIP/SKILL.md 并自动安装</li>
 *   <li>{@code POST   /api/skills/install-drafts/{draftId}/confirm} - 确认安装草稿</li>
 *   <li>{@code DELETE /api/skills/install-drafts/{draftId}} - 丢弃安装草稿</li>
 * </ul>
 *
 * @see SkillService
 * @see SkillFileService
 * @see SkillDraftService
 */
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.tools.entity.SkillInstallDraft;
import com.fastrag.module.tools.service.SkillDraftService;
import com.fastrag.module.tools.service.SkillFileService;
import com.fastrag.module.tools.service.SkillService;
import com.fastrag.module.tools.skill.SkillDependencyValidator;
import com.fastrag.security.util.SecurityUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {
    private final SkillService svc;
    private final SkillDependencyValidator dependencyValidator;
    private final SkillFileService skillFileService;
    private final SkillDraftService skillDraftService;

    /** 列出所有技能 */
    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return ApiResponse.success(svc.list(keyword, category));
    }

    /** 列出用户可访问的已启用技能 (用于 Agent 配置中可选技能列表) */
    @GetMapping("/accessible")
    public ApiResponse<?> listAccessible(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sourceType) {
        return ApiResponse.success(svc.listAccessible(keyword, category, sourceType));
    }

    /** 列出内置技能 */
    @GetMapping("/builtin")
    public ApiResponse<?> listBuiltin() {
        return ApiResponse.success(svc.listBuiltin());
    }

    /** 获取单个技能 */
    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        return ApiResponse.success(svc.get(id));
    }

    /** 根据 slug 获取技能 */
    @GetMapping("/slug/{slug}")
    public ApiResponse<?> getBySlug(@PathVariable String slug) {
        return ApiResponse.success(svc.getBySlug(slug));
    }

    /** 创建技能 */
    @PostMapping
    public ApiResponse<?> create(@RequestBody Map<String, Object> form) {
        return ApiResponse.success(svc.create(form));
    }

    /** 更新技能 */
    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody Map<String, Object> form) {
        return ApiResponse.success(svc.update(id, form));
    }

    /** 删除技能 */
    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        svc.delete(id);
        return ApiResponse.success();
    }

    /** 切换技能启用状态 */
    @PostMapping("/{id}/toggle")
    public ApiResponse<?> toggle(@PathVariable String id) {
        svc.toggleEnabled(id);
        return ApiResponse.success();
    }

    /** 更新技能依赖 */
    @PutMapping("/{id}/dependencies")
    public ApiResponse<?> updateDependencies(
            @PathVariable String id,
            @RequestBody List<Map<String, Object>> dependencies) {
        return ApiResponse.success(svc.updateDependencies(id, dependencies));
    }

    /** 更新技能分享配置 */
    @PutMapping("/{id}/share-config")
    public ApiResponse<?> updateShareConfig(
            @PathVariable String id,
            @RequestBody Map<String, Object> config) {
        return ApiResponse.success(svc.updateShareConfig(id, config));
    }

    /** 设置技能启用状态 */
    @PutMapping("/{id}/enabled")
    public ApiResponse<?> setEnabled(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        svc.setEnabled(id, enabled);
        return ApiResponse.success();
    }

    /** 获取技能依赖列表 */
    @GetMapping("/{id}/dependencies")
    public ApiResponse<?> getDependencies(@PathVariable String id) {
        return ApiResponse.success(svc.getDependencies(id));
    }

    /** 获取技能作用域列表 */
    @GetMapping("/{id}/scopes")
    public ApiResponse<?> getScopes(@PathVariable String id) {
        return ApiResponse.success(svc.getScopes(id));
    }

    /** 获取依赖选项（用于前端下拉框） */
    @GetMapping("/dependency-options")
    public ApiResponse<?> getDependencyOptions(
            @RequestParam(required = false) String excludeSkillId) {
        return ApiResponse.success(dependencyValidator.getDependencyOptions(excludeSkillId));
    }

    // ========== 技能文件管理 ==========

    /** 获取技能文件树 */
    @GetMapping("/{slug}/tree")
    public ApiResponse<?> getFileTree(@PathVariable String slug) {
        return ApiResponse.success(skillFileService.getTree(slug));
    }

    /** 读取技能文件 */
    @GetMapping("/{slug}/file")
    public ApiResponse<?> readFile(
            @PathVariable String slug,
            @RequestParam String path) {
        return ApiResponse.success(skillFileService.readFile(slug, path));
    }

    /** 创建技能文件或目录 */
    @PostMapping("/{slug}/file")
    public ApiResponse<?> createFile(
            @PathVariable String slug,
            @RequestBody Map<String, Object> body) {
        skillFileService.createNode(slug,
            (String) body.get("path"),
            Boolean.TRUE.equals(body.get("isDir")),
            (String) body.get("content"),
            getCurrentUserId());
        return ApiResponse.success();
    }

    /** 更新技能文件 */
    @PutMapping("/{slug}/file")
    public ApiResponse<?> updateFile(
            @PathVariable String slug,
            @RequestBody Map<String, Object> body) {
        skillFileService.updateFile(slug,
            (String) body.get("path"),
            (String) body.get("content"),
            getCurrentUserId());
        return ApiResponse.success();
    }

    /** 删除技能文件或目录 */
    @DeleteMapping("/{slug}/file")
    public ApiResponse<?> deleteFile(
            @PathVariable String slug,
            @RequestParam String path) {
        skillFileService.deleteNode(slug, path, getCurrentUserId());
        return ApiResponse.success();
    }

    /** 导出技能为 ZIP */
    @GetMapping("/{slug}/export")
    public void exportZip(
            @PathVariable String slug,
            HttpServletResponse response) {
        java.io.File zip = skillFileService.exportZip(slug);
        try {
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition",
                "attachment; filename=\"" + slug + ".zip\"");
            try (var os = response.getOutputStream();
                 var is = new java.io.FileInputStream(zip)) {
                is.transferTo(os);
            }
        } catch (IOException e) {
            throw new RuntimeException("导出 ZIP 失败", e);
        } finally {
            zip.delete();
        }
    }

    // ========== 安装草稿流水线 ==========

    /** 上传 ZIP/SKILL.md 并自动完成安装（创建草稿 → 确认安装 → 清理草稿） */
    @PostMapping("/import/prepare")
    public ApiResponse<?> prepareImport(@RequestParam("file") MultipartFile file) {
        try {
            String operator = SecurityUtil.getCurrentUserId();
            SkillInstallDraft draft = skillDraftService.prepareUpload(
                file.getOriginalFilename(), file.getBytes(), operator);
            // 自动确认安装（单步完成，无需前端二次调用）
            List<SkillInstallDraft.DraftItem> results = skillDraftService.confirmDraft(
                draft.getDraftId(), Map.of("accessLevel", "user"), operator);
            return ApiResponse.success(results);
        } catch (IOException e) {
            return ApiResponse.error(400, "文件读取失败: " + e.getMessage());
        }
    }

    /** 确认安装草稿 */
    @PostMapping("/install-drafts/{draftId}/confirm")
    public ApiResponse<?> confirmDraft(
            @PathVariable String draftId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> shareConfig = (Map<String, Object>) body.getOrDefault("shareConfig", Map.of());
        return ApiResponse.success(
            skillDraftService.confirmDraft(draftId, shareConfig, SecurityUtil.getCurrentUserId()));
    }

    /** 丢弃安装草稿 */
    @DeleteMapping("/install-drafts/{draftId}")
    public ApiResponse<?> discardDraft(@PathVariable String draftId) {
        skillDraftService.discardDraft(draftId, SecurityUtil.getCurrentUserId());
        return ApiResponse.success();
    }

    /** 获取当前用户 ID（占位，后续从 SecurityContext 获取） */
    private String getCurrentUserId() {
        return "system";
    }
}
