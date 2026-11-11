package com.fastrag.module.application.widget;

import com.fastrag.module.application.widget.dto.WidgetActionPayload;
import com.fastrag.module.application.widget.dto.WidgetTurnPlan;

/**
 * 回传推进（IF-005）：把用户卡片回传负载合成为一轮对话输入——尺寸护栏、
 * 可见用户消息文本、[WIDGET_ACTION] 结构化标注块；编排层一行调用。
 */
public interface WidgetActionProcessor {

    /** 校验 payload ≤16KB 且 action='sys.submit'，合成推进计划；越限/非法抛业务错误（400），对话不推进 */
    WidgetTurnPlan prepare(WidgetActionPayload action);
}
