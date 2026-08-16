package com.fastrag.module.knowledge.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 问答对创建请求DTO。
 *
 * <p>封装手动创建问答对的请求参数，包含问题（必填）、答案（必填）、
 * 来源和所属文件ID。问答对可用于知识库的QA检索模式。被 QA 管理接口接收。</p>
 */
@Data
public class QaCreateRequest { @NotBlank private String question; @NotBlank private String answer; private String source,fileId; }
