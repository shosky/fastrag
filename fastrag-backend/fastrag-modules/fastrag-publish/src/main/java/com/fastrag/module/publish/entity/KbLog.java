package com.fastrag.module.publish.entity;

/**
 * 知识库操作日志实体类。
 *
 * <p>对应数据库表 {@code kb_log}，记录知识库内各类业务操作的日志信息，
 * 包括文件上传、文件夹操作、分块管理、策略变更等操作的审计记录。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code kbId} - 关联的知识库ID</li>
 *   <li>{@code category} - 日志分类（如 operation、retrieval、publish）</li>
 *   <li>{@code action} - 操作类型（枚举 {@link com.fastrag.common.enums.ActionType}）</li>
 *   <li>{@code target} - 操作目标对象ID（如 fileId、folderId 等）</li>
 *   <li>{@code detail} - 操作详情描述</li>
 *   <li>{@code operator} - 操作人</li>
 *   <li>{@code status} - 操作结果状态（success / failed）</li>
 *   <li>{@code timestamp} - 操作时间</li>
 * </ul>
 *
 * @see com.fastrag.module.publish.mapper.KbLogMapper
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("kb_log")
public class KbLog {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String kbId, category, action, target, detail, operator, status, extra;
    private LocalDateTime timestamp;
}
