package com.fastrag.module.knowledge.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 问答对信息DTO。
 *
 * <p>表示知识库中一个问答对的完整信息，包含问答对ID、所属知识库ID、
 * 所属文件ID和文件名、问题、答案、来源、状态和创建时间。
 * 用于QA模式的知识库内容管理展示。</p>
 */
@Data
public class QaPairDto { private String id,kbId,fileId,fileName,question,answer,source,status; private LocalDateTime createdAt; }
