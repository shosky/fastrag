package com.fastrag.common.response;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 分页查询结果封装。
 *
 * <p>后端分页查询的统一返回格式，包含当前页数据列表（list）、总条数（total）、
 * 当前页码（page）和每页大小（pageSize）。与 {@link PageParams} 配对使用。</p>
 *
 * <p>提供静态工厂方法 {@code of(list, total, page, pageSize)} 用于快速构建分页结果对象。</p>
 *
 * @param <T> 列表元素的泛型类型
 */
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
