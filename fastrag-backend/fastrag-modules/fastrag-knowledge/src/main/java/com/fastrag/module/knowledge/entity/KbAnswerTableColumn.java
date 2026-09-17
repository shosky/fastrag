package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
/** 表格知识的列定义（表格增加列） */
@Data @TableName("kb_answer_table_column") public class KbAnswerTableColumn {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String tableId,name,colKey;
    private String colType; // text / number / date / link
    private Integer sortOrder;
}
