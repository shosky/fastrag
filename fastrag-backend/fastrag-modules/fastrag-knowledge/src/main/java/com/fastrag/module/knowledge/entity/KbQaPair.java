package com.fastrag.module.knowledge.entity;
/**
 * QA 问答对实体类，对应数据库表 kb_qa_pair。
 *
 * <p>核心职责：
 * 存储知识库中的人工问答对，用于补充标准问答内容。支持手动创建（manual）
 * 和 AI 自动生成（ai）两种来源，问答对有草稿（draft）和已确认（confirmed）
 * 两种状态，确认操作标记问答对已通过审核。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 雪花算法生成的唯一标识</li>
 *   <li>kbId — 所属知识库 ID</li>
 *   <li>fileId — 关联的源文件 ID</li>
 *   <li>fileName — 源文件名称（冗余存储，便于展示）</li>
 *   <li>question — 问答对中的问题</li>
 *   <li>answer — 问答对中的答案</li>
 *   <li>source — 问答对来源：manual（手动创建）/ ai（AI 生成）</li>
 *   <li>status — 状态：draft（草稿）/ confirmed（已确认）</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_qa_pair")
public class KbQaPair {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String kbId;
    private String fileId;
    private String fileName;
    private String question;
    private String answer;
    private String source; // manual / ai
    private String status; // draft / confirmed
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
