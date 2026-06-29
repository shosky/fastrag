package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("db_table") public class DbTable {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String dbId,tableName,tableComment,columns;
    private Long rowCount; private Integer enabled; private LocalDateTime syncedAt;
}
