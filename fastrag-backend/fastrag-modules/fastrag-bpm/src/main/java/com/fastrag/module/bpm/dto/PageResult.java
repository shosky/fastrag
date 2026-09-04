package com.fastrag.module.bpm.dto;
import lombok.AllArgsConstructor; import lombok.Data; import lombok.NoArgsConstructor;
import java.util.List;

/** 通用分页响应 */
@Data @AllArgsConstructor @NoArgsConstructor
public class PageResult<T> {
    private long total;
    private Integer page;
    private Integer size;
    private List<T> records;
}