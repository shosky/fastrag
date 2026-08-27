package com.fastrag.module.knowledge.service;

import com.fastrag.infra.minio.MinioService;
import com.fastrag.module.knowledge.parser.ParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 文档 Markdown 全文落盘服务。
 *
 * <p>将 {@link ParseResult#getText()} 中的 markdown 全文按约定路径
 * {@code {kbId}/{fileId}/parsed.md} 上传到 MinIO，供前端展示与回填分片。
 *
 * <p>被以下场景复用：
 * <ul>
 *   <li>{@link com.fastrag.module.knowledge.consumer.IngestionConsumer} —
 *       摄入流水线在分片完成后落盘</li>
 *   <li>{@link com.fastrag.module.knowledge.service.OnlyOfficeService} —
 *       OnlyOffice 编辑后保存时重新解析并落盘，触发后续重分片</li>
 *   <li>{@code FileController.saveMarkdown} — 手动编辑 markdown 后可选触发重分片</li>
 * </ul>
 *
 * <p>仅对 {@link #MARKDOWN_ELIGIBLE_EXTS} 中的文档类扩展名落盘；音视频/图片等无 markdown
 * 语义直接跳过。失败仅告警，**不抛出**，避免 markdown 持久化失败二次放大主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarkdownService {

    private final MinioService minioService;

    /** 会产出 markdown 全文的文档类扩展名（音视频/图片等无 markdown 语义，不落盘） */
    public static final Set<String> MARKDOWN_ELIGIBLE_EXTS = Set.of(
            "doc", "docx", "pdf", "ppt", "pptx", "xls", "xlsx",
            "csv", "txt", "md", "markdown", "rtf", "html", "htm", "xml");

    /**
     * 将 markdown 全文落盘到 MinIO 约定路径 {@code {kbId}/{fileId}/parsed.md}。
     *
     * <p>调用方语义：
     * <ul>
     *   <li>此方法永不抛出（除非上层想要严格语义，可在外包 try/catch 显式处理）</li>
     *   <li>失败原因通过日志 warn 输出</li>
     * </ul>
     */
    public void persistParsedMarkdown(String kbId, String fileId, String extension, ParseResult parseResult) {
        if (extension == null || !MARKDOWN_ELIGIBLE_EXTS.contains(extension.toLowerCase())) {
            return;
        }
        String md = parseResult != null ? parseResult.getText() : null;
        if (md == null || md.isEmpty()) {
            log.debug("Skip markdown persist: empty text for file {}", fileId);
            return;
        }
        String key = kbId + "/" + fileId + "/parsed.md";
        try (ByteArrayInputStream in =
                     new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8))) {
            minioService.upload(key, in, "text/markdown");
            log.info("Markdown persisted for file {}: object_key={}, bytes={}",
                    fileId, key, md.getBytes(StandardCharsets.UTF_8).length);
        } catch (Exception e) {
            log.warn("Markdown persist failed for file {}: {}", fileId, e.getMessage());
        }
    }
}
