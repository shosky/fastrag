package com.fastrag.module.graph.entity;

/**
 * 图谱缝合输出实体，对应数据库表 {@code kb_graph_stitch}。
 *
 * <p>记录文件级缝合 pass（跨分片关系补抽）与实体合并候选的产出，是跨分片图谱整合的
 * 持久化载体：</p>
 * <ul>
 *   <li>{@code stitchType='relation'}：缝合补出的跨分片关系（status=1 已应用），
 *       replay 模式以此零 LLM 成本重放</li>
 *   <li>{@code stitchType='merge'}：缝合 LLM 判定的同义实体合并候选（status=0 待人工确认，
 *       经现有 {@code POST /graph/merge} 执行；status=1 表示已自动合并）</li>
 * </ul>
 *
 * @see com.fastrag.module.graph.entity.KbGraphIndex
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_graph_stitch")
public class KbGraphStitch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String kbId;
    private String fileId;
    /** relation=缝合关系 / merge=合并候选 */
    private String stitchType;
    /** JSON 载荷：relation 为三元组+归属分片；merge 为 canonical/alias */
    private String payload;
    /** 0=待应用（人工候选） 1=已应用（可 replay） 2=跳过 */
    private Integer status;
    /** 关系归属分片 ID（首现分片） */
    private String chunkSource;
    private LocalDateTime createdAt;
}
