package com.fastrag.module.knowledge.model;

import com.fastrag.module.knowledge.entity.KbQaGenerationTask;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 问答对提炼任务视图（qa-generation 契约 QaGenerationTaskView，trigger 的返回与 status 的查询结果）。
 */
@Data
public class QaGenerationTaskView {
    private String kbId;
    private String fileId;
    /** running / success / partial / failed */
    private String status;
    private Integer totalChunks;
    private Integer succeededChunks;
    private Integer failedChunks;
    private QaGenerationConfig config;
    private String error;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static QaGenerationTaskView from(KbQaGenerationTask task) {
        QaGenerationTaskView v = new QaGenerationTaskView();
        v.setKbId(task.getKbId());
        v.setFileId(task.getFileId());
        v.setStatus(task.getStatus());
        v.setTotalChunks(task.getTotalChunks());
        v.setSucceededChunks(task.getSucceededChunks());
        v.setFailedChunks(task.getFailedChunks());
        v.setConfig(QaGenerationConfig.fromJson(task.getConfig()));
        v.setError(task.getError());
        v.setCreatedAt(task.getCreatedAt());
        v.setUpdatedAt(task.getUpdatedAt());
        return v;
    }
}
