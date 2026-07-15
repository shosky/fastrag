package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.math.BigDecimal; import java.time.LocalDateTime;
@Data @TableName("wf_monitor_data") public class WfMonitorData {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String workflowId, metricType, dimension, details;
    private BigDecimal metricValue;
    private LocalDateTime periodStart, periodEnd;
}
