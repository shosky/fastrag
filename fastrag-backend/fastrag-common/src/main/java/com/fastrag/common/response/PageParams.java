package com.fastrag.common.response;

import lombok.Data;
import jakarta.validation.constraints.Min;

/**
 * 分页查询请求参数。
 *
 * <p>前端分页查询的统一请求参数模型，包含页码（page，默认1）、每页大小（pageSize，默认20）
 * 和搜索关键词（keyword，可选）。page和pageSize均有@Min(1)校验，确保不小于1。</p>
 *
 * <p>被各Controller的分页查询接口以@RequestBody或@RequestParam方式接收，
 * 与 {@link PageResult} 配对使用。</p>
 */
@Data
public class PageParams {
    @Min(1) private int page = 1;
    @Min(1) private int pageSize = 20;
    private String keyword;
}
