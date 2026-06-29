package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("db_instance") public class DbInstance {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String name,description,dbType,host,username,password,dbName,jdbcUrl,poolConfig,status,createdBy;
    private Integer port,readOnly; private LocalDateTime createdAt,updatedAt;
}
