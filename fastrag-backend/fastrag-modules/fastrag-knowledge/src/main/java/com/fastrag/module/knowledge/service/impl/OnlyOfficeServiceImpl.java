package com.fastrag.module.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.model.OnlyOfficeCallbackPayload;
import com.fastrag.module.knowledge.parser.DocumentParser;
import com.fastrag.module.knowledge.parser.ParseOptions;
import com.fastrag.module.knowledge.parser.ParseResult;
import com.fastrag.module.knowledge.service.FileService;
import com.fastrag.module.knowledge.service.MarkdownService;
import com.fastrag.module.knowledge.service.OnlyOfficeService;
import com.fastrag.security.util.OnlyOfficeJwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * OnlyOfficeService 实现类。
 *
 * <p>职责拆解：
 * <ul>
 *   <li>{@link #buildEditorConfig} — 拼装 OnlyOffice 官方文档规定的 config 对象，
 *       用 {@link OnlyOfficeJwtUtil} 签名整个 config 后塞入 {@code token} 字段；
 *       URL 拼接时使用 {@code storage-base-url}（原始文件下载）和
 *       {@code callback-base-url}（保存回调）。</li>
 *   <li>{@link #handleCallback} — 校验 status=2/6 + JWT + key 后下载 OO 服务端临时存放
 *       的新文件，调 {@link FileService#replaceOriginalFile} 覆盖存储，再触发
 *       {@code reChunkFile} 走 MQ 异步重分片。</li>
 *   <li>{@link #signRawFileToken} — 给 {@code document.url} 拼 {@code ?token=…}
 *       查询参数，OO 服务端拉取时使用。</li>
 * </ul>
 *
 * <p>document.key 设计：用 {@link MessageDigest} 把 {@code fileId} 和
 * {@link KbFile#getUpdatedAt()} 一起哈希，这样：
 * <ol>
 *   <li>初次打开 → 固定 key → OO 加载原文件</li>
 *   <li>编辑保存 → FastRAG 调 {@code replaceOriginalFile} → updatedAt 变化 → 下次打开
 *       浏览器拿到新 key → OO Document Server 强制 reload 新文件</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlyOfficeServiceImpl implements OnlyOfficeService {

    private final OnlyOfficeJwtUtil ooJwtUtil;
    private final KbFileMapper fileMapper;
    private final MinioService minioService;
    private final FileService fileService;
    private final DocumentParser documentParser;
    private final MarkdownService markdownService;

    /** 支持 OnlyOffice 在线编辑的文件扩展名（小写） */
    private static final Set<String> SUPPORTED_EXTS = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx");

    /** OO docType 映射：用于配置 editorConfig 类型 */
    private static final Map<String, String> EXT_TO_DOC_TYPE = Map.of(
            "doc", "word",
            "docx", "word",
            "xls", "cell",
            "xlsx", "cell",
            "ppt", "slide",
            "pptx", "slide");

    /** OO fileType 字段（必须严格按 OO 约定） */
    private static final Map<String, String> EXT_TO_FILE_TYPE = Map.of(
            "doc", "doc",
            "docx", "docx",
            "xls", "xls",
            "xlsx", "xlsx",
            "ppt", "ppt",
            "pptx", "pptx");

    /** document.url 中 raw 接口地址前缀 */
    @Value("${onlyoffice.storage-base-url:http://host.docker.internal:8081}")
    private String storageBaseUrl;

    /** callback URL 前缀 */
    @Value("${onlyoffice.callback-base-url:http://host.docker.internal:8081}")
    private String callbackBaseUrl;

    /** 启用开关（false 时前端走原 mammoth/xlsx/pptx-preview 渲染） */
    @Value("${onlyoffice.enabled:false}")
    private boolean enabled;

    /**
     * JWT 开关，与 OO 容器 token.enable 同步。
     * false 时：config 不签名、callback 跳过 token 校验（OO 禁用 JWT 后回调不携带 token 字段）。
     * document.key 校验始终保留。
     */
    @Value("${onlyoffice.jwt-enabled:true}")
    private boolean jwtEnabled;

    @Override
    public boolean isSupportedFile(String fileName) {
        if (!enabled || StrUtil.isBlank(fileName)) {
            return false;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return false;
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTS.contains(ext);
    }

    @Override
    public String signRawFileToken(String kbId, String fileId) {
        return ooJwtUtil.signRawFileToken(kbId, fileId, 1800L);
    }

    @Override
    public String buildDocumentKey(String fileId, LocalDateTime updatedAt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(fileId.getBytes(StandardCharsets.UTF_8));
            if (updatedAt != null) {
                md.update(updatedAt.toString().getBytes(StandardCharsets.UTF_8));
            }
            byte[] digest = md.digest();
            // OO 文档 key 限制长度 ≤ 128 字符且最好是 base64-like；取前 32 字符
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16 && i < digest.length; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return fileId + "_" + System.currentTimeMillis();
        }
    }

    @Override
    public Map<String, Object> buildEditorConfig(String kbId, String fileId, String userId, String userName, boolean editable) {
        KbFile f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getId, fileId)
                .eq(KbFile::getKbId, kbId));
        if (f == null) {
            throw new IllegalArgumentException("File not found: " + fileId);
        }
        String ext = f.getExtension() != null ? f.getExtension().toLowerCase(Locale.ROOT) : "";
        if (!SUPPORTED_EXTS.contains(ext)) {
            throw new IllegalArgumentException("OnlyOffice not supported for extension: " + ext);
        }

        String documentKey = buildDocumentKey(fileId, f.getUpdatedAt());
        // OnlyOffice 7.1+ 要求 document.url 同时带 token 和 key（防 URL 滥用 / 文档身份校验）
        String documentUrl = storageBaseUrl + "/api/kb/" + kbId + "/files/" + fileId + "/onlyoffice/raw"
                + "?token=" + signRawFileToken(kbId, fileId)
                + "&key=" + documentKey;
        String callbackUrl = callbackBaseUrl + "/api/kb/" + kbId + "/files/" + fileId + "/onlyoffice/callback";

        Map<String, Object> document = new HashMap<>();
        document.put("fileType", EXT_TO_FILE_TYPE.getOrDefault(ext, ext));
        document.put("key", documentKey);
        document.put("title", f.getName());
        document.put("url", documentUrl);
        document.put("permissions", Map.of(
                "edit", editable,
                "download", true,
                "print", true,
                "copy", true));

        Map<String, Object> editorConfig = new HashMap<>();
        editorConfig.put("callbackUrl", callbackUrl);
        editorConfig.put("mode", editable ? "edit" : "view");
        editorConfig.put("user", Map.of(
                "id", userId == null ? "anonymous" : userId,
                "name", userName == null ? "Anonymous" : userName));
        editorConfig.put("lang", "zh-CN");
        editorConfig.put("customization", Map.of(
                "autosave", true,
                "forcesave", true,        // 显式开启强制保存，避免用户忘记 Ctrl+S
                "compactHeader", true,
                "toolbar", true));

        Map<String, Object> root = new HashMap<>();
        root.put("document", document);
        root.put("editorConfig", editorConfig);
        root.put("documentType", EXT_TO_DOC_TYPE.getOrDefault(ext, "word"));
        root.put("type", EXT_TO_DOC_TYPE.getOrDefault(ext, "word"));
        root.put("width", "100%");
        root.put("height", "100%");

        // 整个 config 用 OO 密钥签名（OO Document Server 会校验）；jwtEnabled=false 时跳过
        if (jwtEnabled) {
            String token = ooJwtUtil.signPayload(root, 1800L * 1000L);
            root.put("token", token);
        }

        log.info("[OnlyOffice] Built editor config: kbId={}, fileId={}, ext={}, editable={}, key={}",
                kbId, fileId, ext, editable, documentKey);
        return root;
    }

    @Override
    public Map<String, Object> handleCallback(String kbId, String fileId, OnlyOfficeCallbackPayload payload) {
        Map<String, Object> response = new HashMap<>();
        if (payload == null || payload.getStatus() == null) {
            log.warn("[OnlyOffice] Callback missing status: kbId={}, fileId={}", kbId, fileId);
            response.put("error", 1);
            return response;
        }

        int status = payload.getStatus();
        log.info("[OnlyOffice] Callback received: kbId={}, fileId={}, status={}, key={}",
                kbId, fileId, status, payload.getKey());

        // status 1/4: 正在编辑或关闭无修改 → 直接 ack
        if (status == 1 || status == 4) {
            response.put("error", 0);
            return response;
        }

        // status 3/7: 保存错误 → 拒绝
        if (status == 3 || status == 7) {
            log.warn("[OnlyOffice] Save error reported: kbId={}, fileId={}, status={}", kbId, fileId, status);
            response.put("error", 1);
            return response;
        }

        // status 2/6: 需要保存 → 下载 + 替换 + 触发重分片
        if (status == 2 || status == 6) {
            String token = payload.getToken();
            String downloadUrl = payload.getUrl();
            String key = payload.getKey();

            // 1. 校验 token（OO 禁用 JWT 时回调不携带 token，跳过校验；document.key 校验仍保留）
            if (jwtEnabled) {
                try {
                    Map<String, Object> verified = ooJwtUtil.verify(token);
                    if (!kbId.equals(String.valueOf(verified.get("kbId")))
                            || !fileId.equals(String.valueOf(verified.get("fileId")))) {
                        log.warn("[OnlyOffice] Callback token mismatch: claimed kbId={} fileId={}, verified={}",
                                kbId, fileId, verified);
                        response.put("error", 1);
                        return response;
                    }
                } catch (Exception e) {
                    log.warn("[OnlyOffice] Callback token verification failed: {}", e.getMessage());
                    response.put("error", 1);
                    return response;
                }
            } else if (token != null && !token.isBlank()) {
                log.info("[OnlyOffice] JWT disabled, skipping callback token check (token present but ignored)");
            }

            // 2. 校验 document.key
            KbFile f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                    .eq(KbFile::getId, fileId)
                    .eq(KbFile::getKbId, kbId));
            if (f == null) {
                response.put("error", 1);
                return response;
            }
            String expectedKey = buildDocumentKey(fileId, f.getUpdatedAt());
            if (key != null && !key.equals(expectedKey)) {
                log.warn("[OnlyOffice] Callback document.key mismatch: expected={}, got={}",
                        expectedKey, key);
                // key 不一致说明文件已在我们不知情的情况下被改动过，保守拒绝
                response.put("error", 1);
                return response;
            }

            // 3. 下载 OO 临时文件
            if (StrUtil.isBlank(downloadUrl)) {
                log.warn("[OnlyOffice] Callback status=2 missing download url: fileId={}", fileId);
                response.put("error", 1);
                return response;
            }
            byte[] newBytes;
            try {
                newBytes = downloadFromUrl(downloadUrl);
            } catch (Exception e) {
                log.error("[OnlyOffice] Failed to download saved file from {}: {}", downloadUrl, e.getMessage());
                response.put("error", 1);
                return response;
            }

            // 4. 替换原始文件 + 更新 markdown
            try {
                String contentType = guessContentType(f.getExtension());
                f = fileService.replaceOriginalFile(kbId, fileId, newBytes, contentType);
                log.info("[OnlyOffice] File replaced: kbId={}, fileId={}, size={}", kbId, fileId, newBytes.length);

                // 5. 重新解析并落盘 markdown（失败仅告警，不阻塞主流程）
                try (InputStream in = new ByteArrayInputStream(newBytes)) {
                    ParseResult parsed = documentParser.parse(in, f.getExtension(), f.getParseStrategyId(),
                            (ParseOptions) null);
                    markdownService.persistParsedMarkdown(kbId, fileId, f.getExtension(), parsed);
                } catch (Exception parseEx) {
                    log.warn("[OnlyOffice] Re-parse failed for {}: {}", fileId, parseEx.getMessage());
                }

                // 6. 触发重分片（走 MQ 异步，**不等执行完成**立即返回 OO）
                // 注：用 try/catch 包住异常，确保即使 MQ 不可用也能 ack 给 OO
                try {
                    fileService.reChunkFile(kbId, fileId, null);
                    log.info("[OnlyOffice] Re-chunk triggered: fileId={}", fileId);
                } catch (Exception chunkEx) {
                    log.warn("[OnlyOffice] Re-chunk trigger failed for {}: {}", fileId, chunkEx.getMessage());
                }
            } catch (Exception e) {
                log.error("[OnlyOffice] Failed to handle saved file: {}", e.getMessage(), e);
                response.put("error", 1);
                return response;
            }

            response.put("error", 0);
            return response;
        }

        // 未知 status
        log.warn("[OnlyOffice] Unknown callback status: {}", status);
        response.put("error", 0);
        return response;
    }

    /**
     * 从 OO 提供的 URL 下载文件字节。OO 把用户编辑后的文件放在它自己的临时存储里，
     * 这个 URL 短时有效（通常 5-10 分钟）。使用 {@code internal-url} 配置的 OO 主机名，
     * 避免内网域名解析问题。
     */
    private byte[] downloadFromUrl(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);
        int code = conn.getResponseCode();
        if (code >= 400) {
            throw new RuntimeException("HTTP " + code);
        }
        try (InputStream is = conn.getInputStream()) {
            return is.readAllBytes();
        } finally {
            conn.disconnect();
        }
    }

    private String guessContentType(String ext) {
        if (ext == null) return "application/octet-stream";
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc"  -> "application/msword";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls"  -> "application/vnd.ms-excel";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "ppt"  -> "application/vnd.ms-powerpoint";
            default -> "application/octet-stream";
        };
    }
}
