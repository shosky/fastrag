package com.fastrag.module.graph.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
@Data
@TableName("kb_benchmark_question")
public class KbBenchmarkQuestion {
    @TableId(type = IdType.AUTO) private Long id;
    private String benchmarkId, question, goldChunks, goldAnswer;
    private Integer questionIndex;
}
