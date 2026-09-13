package com.fastrag.module.graph.entity;

/**
 * 同义实体合并审计实体，对应数据库表 {@code kb_graph_merge_audit}。
 *
 * <p>记录每次实体合并的 source/target 与合并前快照，作为人工回滚与问题追溯的依据。
 * 合并为破坏性操作（source 节点删除、边与 mentions 迁向 target），审计记录保留
 * source 快照后可在需要时重建。</p>
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_graph_merge_audit")
public class KbGraphMergeAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String kbId;
    /** 被合并（删除）实体 ID */
    private String srcEntityId;
    /** 保留实体 ID */
    private String tgtEntityId;
    /** source 完整属性 JSON（回滚重建用） */
    private String srcSnapshot;
    /** 迁移边与 mentions 清单（回滚用） */
    private String edgeSnapshot;
    /** 执行人：system / 用户 ID */
    private String operator;
    private LocalDateTime createdAt;
}
