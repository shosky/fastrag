package com.fastrag.common.response;
import lombok.Data;
import java.io.Serializable;
import java.util.List;
@Data
public class PageResult<T> implements Serializable {
    private List<T> list;
    private long total;
    private int page;
    private int pageSize;
    public static <T> PageResult<T> of(List<T> list, long total, int page, int pageSize) {
        PageResult<T> r = new PageResult<>();
        r.setList(list); r.setTotal(total); r.setPage(page); r.setPageSize(pageSize);
        return r;
    }
}
