package com.fastrag.module.platform.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
@Data @TableName("sys_dictionary") public class SysDictionary {
    @TableId(type=IdType.AUTO) private Long id;
    private String dictType,dictKey,dictValue;
}
