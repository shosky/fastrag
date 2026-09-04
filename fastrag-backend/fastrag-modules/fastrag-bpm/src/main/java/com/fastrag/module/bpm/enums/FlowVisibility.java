package com.fastrag.module.bpm.enums;

import cn.hutool.core.util.StrUtil;
import com.fastrag.common.exception.BusinessException;

/** 流程可见性：private 私有 / team 同组织可见 / public 公开（可被复制） */
public enum FlowVisibility {
    private_flow, team, public_flow;

    public static FlowVisibility of(String v) {
        if (StrUtil.isBlank(v) || "private".equals(v)) return private_flow;
        for (FlowVisibility s : values()) if (s.name().equals(v)) return s;
        throw BusinessException.badRequest("未知流程可见性: " + v);
    }
}