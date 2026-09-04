package com.fastrag.module.bpm.enums;

import cn.hutool.core.util.StrUtil;
import com.fastrag.common.exception.BusinessException;

/** 流程实例状态（6 态 + waiting_input 子态）：pending 待消费 / running 运行 / paused 暂停 / completed 完成 / cancelled 取消 / failed 失败 */
public enum InstanceStatus {
    pending, running, paused, completed, cancelled, failed;

    /** running 子态：等待用户输入（实例 status 仍为 running，通过 pending_input_token 非空识别） */
    public static final String SUBSTATUS_WAITING_INPUT = "waiting_input";

    public static InstanceStatus of(String v) {
        for (InstanceStatus s : values()) if (s.name().equals(StrUtil.nullToEmpty(v))) return s;
        throw BusinessException.badRequest("未知流程实例状态: " + v);
    }
}