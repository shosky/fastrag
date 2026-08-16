package com.fastrag.module.knowledge.controller;

/**
 * 解析策略元数据控制器，提供「文档类型 ↔ 扩展名」的唯一权威映射。
 *
 * <p>核心职责：
 * 前端解析策略表单据此渲染「文档类型」选择器与扩展名选项，避免前后端
 * 各自维护一份扩展名/解析方法清单导致口径漂移（如 .ppt 漏映射）。
 * 数据来源为 {@link com.fastrag.module.knowledge.parser.ParseMethodRegistry}。
 *
 * <p>REST 端点：
 * <ul>
 *   <li>GET /api/parse-strategies/meta — 返回全部解析方法（含 label 与兼容扩展名）及全部受支持扩展名（无权限限制）</li>
 * </ul>
 */
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.parser.ParseMethodRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/parse-strategies/meta")
@RequiredArgsConstructor
public class ParseStrategyMetaController {

    @GetMapping
    public ApiResponse<?> meta() {
        List<Map<String, Object>> methods = Arrays.stream(ParseMethodRegistry.values())
                .map(m -> Map.of(
                        "code", m.getCode(),
                        "label", m.getLabel(),
                        "extensions", m.getExtensions()
                ))
                .toList();
        return ApiResponse.success(Map.of(
                "methods", methods,
                "supportedExtensions", ParseMethodRegistry.supportedExtensions()
        ));
    }
}
