package com.fastrag.module.knowledge.controller;
/**
 * 解析策略模板控制器，提供预定义的解析策略模板列表。
 *
 * <p>核心职责：
 * 返回系统内置的解析策略模板，供前端在创建解析策略时作为参考基线。
 * 模板为硬编码数据，包含四种策略类型：通用文档策略（auto）、PDF 专用策略（pdf）、
 * PPT 专用策略（pptx）和音视频策略（media），每种模板定义了适用的文件扩展名列表
 * 和对应的解析方法（parseMethod）。
 *
 * <p>REST 端点：
 * <ul>
 *   <li>GET /api/parse-strategy-templates — 返回全部预定义策略模板列表（无权限限制）</li>
 * </ul>
 *
 * <p>模板类型：
 * <ul>
 *   <li>auto  — 通用文档策略，支持 pdf/docx/doc/xlsx/xls/pptx/ppt/md/txt/csv，使用 default 解析方法</li>
 *   <li>pdf   — PDF 专用策略，仅支持 .pdf，使用 pdf 解析方法</li>
 *   <li>pptx  — PPT 专用策略，支持 .pptx/.ppt，使用 pptx 解析方法</li>
 *   <li>media — 音视频策略，支持常见音频与视频扩展名，使用 default 解析方法（按扩展名自动选择 ASR/视频解析）</li>
 * </ul>
 */
import com.fastrag.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/parse-strategy-templates")
@RequiredArgsConstructor
public class ParseStrategyTemplateController {

    @GetMapping
    public ApiResponse<?> list() {
        return ApiResponse.success(List.of(
                Map.of(
                        "key", "auto",
                        "name", "通用文档策略",
                        "description", "自动识别文件类型，使用默认解析方法",
                        "extensions", List.of(".pdf", ".docx", ".doc", ".xlsx", ".xls", ".pptx", ".ppt", ".md", ".txt", ".csv"),
                        "parseMethod", "default"
                ),
                Map.of(
                        "key", "pdf",
                        "name", "PDF 专用策略",
                        "description", "针对 PDF 文件优化的解析方法",
                        "extensions", List.of(".pdf"),
                        "parseMethod", "pdf"
                ),
                Map.of(
                        "key", "pptx",
                        "name", "PPT 专用策略",
                        "description", "针对 PPT 文件优化的解析方法，支持整页解析",
                        "extensions", List.of(".pptx", ".ppt"),
                        "parseMethod", "pptx"
                ),
                Map.of(
                        "key", "media",
                        "name", "音视频策略",
                        "description", "音视频文件解析（按扩展名自动选择 ASR / 视频解析）",
                        "extensions", List.of(".mp3", ".wav", ".m4a", ".aac", ".ogg", ".flac", ".wma", ".mp4", ".avi", ".mov", ".mkv", ".flv", ".wmv", ".webm"),
                        "parseMethod", "default"
                )
        ));
    }
}
