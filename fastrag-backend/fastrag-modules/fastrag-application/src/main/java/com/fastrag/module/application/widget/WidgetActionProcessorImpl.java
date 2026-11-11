package com.fastrag.module.application.widget;

import cn.hutool.json.JSONUtil;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.application.widget.dto.WidgetActionPayload;
import com.fastrag.module.application.widget.dto.WidgetTurnPlan;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * WidgetActionProcessor 实现：纯函数式合成（零 DB 依赖）。
 * 威胁模型分界：回传是用户输入通道（用户本可发送任意消息），widgetId 不做绑定校验——
 * fail-closed 防线只设在 LLM 工具通道（IF-004.resolveSnapshot）。
 */
@Component
public class WidgetActionProcessorImpl implements WidgetActionProcessor {

    static final String SUBMIT_ACTION = "sys.submit";

    @Override
    public WidgetTurnPlan prepare(WidgetActionPayload action) {
        if (action == null || !SUBMIT_ACTION.equals(action.getAction())) {
            throw new BusinessException(400, "回传仅接受 sys.submit 动作");
        }
        if (action.getWidgetId() == null || action.getWidgetId().isBlank()
                || action.getWidgetKey() == null || action.getWidgetKey().isBlank()) {
            throw new BusinessException(400, "回传缺少 widgetId 或 widgetKey");
        }
        Map<String, Object> payload = action.getPayload() == null ? Map.of() : action.getPayload();
        if (JSONUtil.toJsonStr(payload).getBytes(StandardCharsets.UTF_8).length
                > WidgetComponentWhitelist.MAX_ACTION_PAYLOAD_BYTES) {
            throw new BusinessException(400, "回传负载超过 16KB 上限");
        }
        String visible = "（来自 Widget 卡片 " + action.getWidgetKey() + "）提交操作：" + summarize(payload);
        String block = "[WIDGET_ACTION] widgetId=" + action.getWidgetId()
                + " widgetKey=" + action.getWidgetKey()
                + " action=" + SUBMIT_ACTION
                + "\npayload=" + JSONUtil.toJsonStr(payload)
                + "\n以上是用户在对话卡片上提交的选择，请基于它继续任务。";
        return new WidgetTurnPlan(visible, block);
    }

    /** 负载摘要（可见消息用）：顶层键值对逐一列出，整体截断 200 字符 */
    private String summarize(Map<String, Object> payload) {
        if (payload.isEmpty()) {
            return "（无表单数据）";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> en : payload.entrySet()) {
            if (sb.length() > 0) {
                sb.append("，");
            }
            sb.append(en.getKey()).append("=").append(en.getValue() == null ? "" : en.getValue());
        }
        return sb.length() > 200 ? sb.substring(0, 200) + "…" : sb.toString();
    }
}
