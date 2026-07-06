package com.fastrag.module.graph.entity;
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
    private Integer totalChunks, builtChunks, entityCount, relationCount, indexVersion;
    private LocalDateTime lastBuiltAt;
    private String buildError;
    private String settings; // JSON: maxNodes, searchDepth, excludeChunkNodes, buildMode, triggerFileId
}
