package com.fastrag.module.graph.entity;

/**
 * 知识图谱构建索引状态实体类，对应数据库表 {@code kb_graph_index}。
 *
 * <p>以知识库ID为主键，记录每个知识库的图谱构建状态、进度统计和配置信息。
 * 每个知识库只有一条索引记录，用于追踪图谱构建的全局状态和元数据。</p>
 *
 * <p>核心字段说明：</p>
 * <ul>
 *   <li>{@code kbId} - 知识库ID，同时作为主键（每个知识库唯一一条记录）</li>
 *   <li>{@code status} - 构建状态（如 idle/building/completed/failed）</li>
 *   <li>{@code buildProgress} - 构建总进度百分比</li>
 *   <li>{@code entityExtractProgress} - 实体提取进度（已废弃）</li>
 *   <li>{@code relationExtractProgress} - 关系提取进度（已废弃）</li>
 *   <li>{@code totalChunks} - 待处理chunk总数</li>
 *   <li>{@code builtChunks} - 已完成处理的chunk数量</li>
 *   <li>{@code failedChunks} - 处理失败的chunk数量</li>
 *   <li>{@code entityCount} - 已提取的实体总数</li>
 *   <li>{@code relationCount} - 已提取的关系总数</li>
 *   <li>{@code indexVersion} - 索引版本号，用于增量构建判断</li>
 *   <li>{@code lastBuiltAt} - 最后构建完成时间</li>
 *   <li>{@code buildError} - 构建错误信息（构建失败时记录）</li>
 *   <li>{@code settings} - 图谱构建配置（JSON格式），包含maxNodes、searchDepth、excludeChunkNodes、buildMode等参数</li>
 * </ul>
 *
 * @see com.fastrag.module.graph.service.GraphService
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("kb_graph_index")
public class KbGraphIndex {
    @TableId(type = IdType.ASSIGN_ID) private String kbId;
    private String status;
    private Integer buildProgress;
    /** @deprecated 该字段已废弃，不再由 GraphServiceImpl 写入 */
    @Deprecated
    private Integer entityExtractProgress;
    /** @deprecated 该字段已废弃，不再由 GraphServiceImpl 写入 */
    @Deprecated
    private Integer relationExtractProgress;
    private Integer totalChunks, builtChunks, failedChunks, entityCount, relationCount, indexVersion;
    private LocalDateTime lastBuiltAt;
    private String buildError;
    private String settings; // JSON: maxNodes, searchDepth, excludeChunkNodes, buildMode, triggerFileId
}
