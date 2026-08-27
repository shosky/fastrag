package com.fastrag.module.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.enums.KBRole;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.model.OnlyOfficeCallbackPayload;
import com.fastrag.module.knowledge.service.OnlyOfficeService;
import com.fastrag.security.annotation.KbAuth;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.OnlyOfficeJwtUtil;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * OnlyOffice Document Server 集成控制器。
 *
 * <p>提供三组端点（基础路径 {@code /api/kb/{kbId}/files}）：
 * <ul>
 *   <li>{@code GET /{id}/onlyoffice/config} — KB-auth viewer，浏览器调用，
 *       返回 OnlyOffice config（含 JWT 签名 token）。前端把 config 直接传给
 *       {@code DocsAPI.DocEditor} 即可。</li>
 *   <li>{@code GET /{id}/onlyoffice/raw} — permitAll + OnlyOffice JWT 校验，
 *       OnlyOffice Document Server 服务端调用，从 MinIO 拉取原始文件。</li>
 *   <li>{@code POST /{id}/onlyoffice/callback} — permitAll + OnlyOffice JWT 校验，
 *       OnlyOffice Document Server 在保存时回调，下载新文件并触发重分片。</li>
 * </ul>
 *
 * <p>权限模型：
 * <ul>
 *   <li>/config 走标准 {@link KbAuth}（viewer 角色可读，editor 角色可编辑）</li>
 *   <li>/raw 与 /callback 走 OnlyOffice 自签 JWT（不允许前端或外部访问）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/kb/{kbId}/files")
@RequiredArgsConstructor
public class OnlyOfficeController {

    private final OnlyOfficeService onlyOfficeService;
    private final OnlyOfficeJwtUtil ooJwtUtil;
    private final KbFileMapper fileMapper;
    private final MinioService minioService;

    @Value("${onlyoffice.enabled:false}")
    private boolean ooEnabled;

    /**
     * 浏览器拉取编辑器配置。
     * viewer 角色只能查看（mode=view），editor 角色可编辑（mode=edit，强制保存触发重分片）。
     */
    @KbAuth(KBRole.viewer)
    @GetMapping("/{id}/onlyoffice/config")
    public ApiResponse<?> getConfig(@PathVariable String kbId, @PathVariable String id) {
        if (!ooEnabled) {
            return ApiResponse.error(503, "OnlyOffice 未启用（onlyoffice.enabled=false）");
        }
        KbFile f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getId, id).eq(KbFile::getKbId, kbId));
        if (f == null) {
            return ApiResponse.error(404, "文件不存在");
        }
        if (!onlyOfficeService.isSupportedFile(f.getName())) {
            return ApiResponse.badRequest("OnlyOffice 不支持该文件类型: " + f.getExtension());
        }

        LoginUser currentUser = SecurityUtil.getCurrentUser();
        String userId = currentUser != null ? currentUser.getUserId() : "anonymous";
        String userName = currentUser != null ? currentUser.getUsername() : "Anonymous";

        // 角色判断：viewer → mode=view；editor/owner → mode=edit
        KBRole role = resolveCurrentUserRole(kbId);
        boolean editable = role == KBRole.editor || role == KBRole.owner;

        Map<String, Object> config = onlyOfficeService.buildEditorConfig(kbId, id, userId, userName, editable);
        return ApiResponse.success(config);
    }

    /**
     * OnlyOffice Document Server 服务端拉取原始文件。
     * permitAll（在 SecurityConfig 已配置），仅接受 OnlyOffice 自签 JWT 作为身份证明。
     */
    @GetMapping("/{id}/onlyoffice/raw")
    public ResponseEntity<InputStreamResource> rawFile(
            @PathVariable String kbId,
            @PathVariable String id,
            @RequestParam("token") String token) {
        // 1. 校验 token
        Map<String, Object> verified;
        try {
            verified = ooJwtUtil.verify(token);
        } catch (Exception e) {
            log.warn("[OnlyOffice] rawFile token verify failed: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }
        String tokenKbId = String.valueOf(verified.get("kbId"));
        String tokenFileId = String.valueOf(verified.get("fileId"));
        String scope = String.valueOf(verified.get("scope"));
        if (!"onlyoffice-raw".equals(scope) || !kbId.equals(tokenKbId) || !id.equals(tokenFileId)) {
            log.warn("[OnlyOffice] rawFile token mismatch: path kbId={} fileId={}, token claims={}", kbId, id, verified);
            return ResponseEntity.status(403).build();
        }

        // 2. 取文件
        KbFile f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getId, id).eq(KbFile::getKbId, kbId));
        if (f == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            InputStream stream = minioService.download(f.getObjectKey());
            MediaType mediaType = mediaTypeFor(f.getExtension());
            String encodedName = URLEncoder.encode(f.getName(), StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(mediaType)
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            log.error("[OnlyOffice] rawFile stream error: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * OnlyOffice Document Server 保存回调。
     * 仅返回 OO 约定的 {@code {error, actions}}，不包装 ApiResponse。
     */
    @PostMapping("/{id}/onlyoffice/callback")
    public Map<String, Object> callback(
            @PathVariable String kbId,
            @PathVariable String id,
            @RequestBody OnlyOfficeCallbackPayload payload) {
        log.info("[OnlyOffice] callback received: kbId={}, fileId={}, status={}",
                kbId, id, payload != null ? payload.getStatus() : null);
        Map<String, Object> result = onlyOfficeService.handleCallback(kbId, id, payload);
        log.info("[OnlyOffice] callback response: {}", result);
        return result;
    }

    /**
     * 解析当前用户在该 KB 的角色（直接读 Spring Security 上下文中的权限）。
     * 注：{@link com.fastrag.security.aspect.KbAuthAspect} 已经把角色信息存入 LoginUser，
     * 此处简化复用 — 若 LoginUser.permissions 含 {@code kb:manage} 视为 editor/owner。
     */
    private KBRole resolveCurrentUserRole(String kbId) {
        LoginUser u = SecurityUtil.getCurrentUser();
        if (u == null) return KBRole.viewer;
        if (u.hasPermission("*")) return KBRole.editor;
        // 简化：具备 kb:write/edit 权限视为 editor，否则 viewer
        if (u.hasAnyPermission("kb:manage", "kb:write", "kb:edit")) {
            return KBRole.editor;
        }
        return KBRole.viewer;
    }

    private MediaType mediaTypeFor(String ext) {
        if (ext == null) return MediaType.APPLICATION_OCTET_STREAM;
        return switch (ext.toLowerCase()) {
            case "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "doc"  -> MediaType.parseMediaType("application/msword");
            case "xlsx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "xls"  -> MediaType.parseMediaType("application/vnd.ms-excel");
            case "pptx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation");
            case "ppt"  -> MediaType.parseMediaType("application/vnd.ms-powerpoint");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /** Map → JSON 序列化辅助（仅 callback 响应使用） */
    @SuppressWarnings("unused")
    private static Map<String, Object> emptyOk() {
        Map<String, Object> m = new HashMap<>();
        m.put("error", 0);
        return m;
    }
}
