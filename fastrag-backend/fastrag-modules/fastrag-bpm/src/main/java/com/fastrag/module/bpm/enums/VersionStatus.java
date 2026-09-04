package com.fastrag.module.bpm.enums;

import cn.hutool.core.util.StrUtil;
import com.fastrag.common.exception.BusinessException;

/** 流程版本状态（4 态）：draft 草稿 / published 已发布 / archived 历史归档 / disabled 禁用 */
public enum VersionStatus {
    draft, published, archived, disabled;

    public static VersionStatus of(String v) {
        for (VersionStatus s : values()) if (s.name().equals(StrUtil.nullToEmpty(v))) return s;
        throw BusinessException.badRequest("未知流程版本状态: " + v);
    }
}