package com.fastrag.module.bpm.enums;

import cn.hutool.core.util.StrUtil;
import com.fastrag.common.exception.BusinessException;

/** 流程边类型：default 默认顺序 / condition 条件分支 / parallel 并行 / exception 异常分支 */
public enum EdgeKind {
    default_edge, condition, parallel, exception;

    public static EdgeKind of(String v) {
        if (StrUtil.isBlank(v) || "default".equals(v)) return default_edge;
        for (EdgeKind k : values()) if (k.name().equals(v)) return k;
        throw BusinessException.badRequest("未知边类型: " + v);
    }
}