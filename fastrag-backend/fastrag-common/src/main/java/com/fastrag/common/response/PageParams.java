package com.fastrag.common.response;
import lombok.Data;
import jakarta.validation.constraints.Min;
@Data
public class PageParams {
    @Min(1) private int page = 1;
    @Min(1) private int pageSize = 20;
    private String keyword;
}
