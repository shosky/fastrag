package com.fastrag.module.knowledge.controller;

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
                        "description", "音视频文件解析，支持 ASR 语音识别",
                        "extensions", List.of(".mp3", ".wav", ".m4a", ".aac", ".ogg", ".mp4", ".avi", ".mov", ".mkv", ".flv"),
                        "parseMethod", "audio"
                )
        ));
    }
}
