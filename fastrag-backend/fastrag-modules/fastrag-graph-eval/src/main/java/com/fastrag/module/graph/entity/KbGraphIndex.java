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
    private Integer buildProgress, entityExtractProgress, relationExtractProgress;
    private Integer totalChunks, builtChunks, entityCount, relationCount, indexVersion;
    private LocalDateTime lastBuiltAt;
    private String buildError;
}
