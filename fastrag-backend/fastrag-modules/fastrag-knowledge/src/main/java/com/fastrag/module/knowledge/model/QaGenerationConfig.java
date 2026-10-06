package com.fastrag.module.knowledge.model;

import cn.hutool.json.JSONUtil;
import com.fastrag.common.exception.BusinessException;
import lombok.Data;

/**
 * 问答对提炼参数（qa-generation 契约 QaGenerationConfig）。
 *
 * <p>字段口径（contract/interfaces.md `## qa-generation`）：
 * count 1-10 缺省 3；customInstruction ≤500 字符可空；mode replace|append 缺省 replace；
 * model ≤128 字符可空（不校验存在性，未知模型表现为分片级失败入统计）。
 *
 * <p> kb_file.qa_generation_config 列仅存 {count, customInstruction, model}
 * （无 mode——qa 模式自动触发恒 replace 语义，由摄入挂钩组装）；
 * 消息与任务行 config 快照为全量四字段。
 */
@Data
public class QaGenerationConfig {
    public static final String MODE_REPLACE = "replace";
    public static final String MODE_APPEND = "append";

    /** 每父分片生成条数（1-10，缺省 3） */
    private Integer count;
    /** 自定义指令（拼入 prompt 的额外要求，≤500 字符可空） */
    private String customInstruction;
    /** 重跑模式：replace（缺省，先删该文件旧 ai 对）/ append（保留仅追加） */
    private String mode;
    /** 模型标识（model.code），空走默认网关 */
    private String model;

    /** 应用缺省并校验；非法抛业务错误（契约 E_QA_GEN_PARAM_INVALID） */
    public QaGenerationConfig normalized() {
        QaGenerationConfig c = new QaGenerationConfig();
        int n = this.count == null ? 3 : this.count;
        if (n < 1 || n > 10) {
            throw BusinessException.badRequest("每分片生成条数须在 1-10 之间");
        }
        c.setCount(n);
        String ins = this.customInstruction == null ? null : this.customInstruction.trim();
        if (ins != null && ins.isEmpty()) {
            ins = null;
        }
        if (ins != null && ins.length() > 500) {
            throw BusinessException.badRequest("自定义指令不能超过 500 字符");
        }
        c.setCustomInstruction(ins);
        String m = this.mode == null || this.mode.isBlank() ? MODE_REPLACE : this.mode;
        if (!MODE_REPLACE.equals(m) && !MODE_APPEND.equals(m)) {
            throw BusinessException.badRequest("mode 仅支持 replace / append");
        }
        c.setMode(m);
        String model = this.model == null ? null : this.model.trim();
        if (model != null && model.isEmpty()) {
            model = null;
        }
        if (model != null && model.length() > 128) {
            throw BusinessException.badRequest("model 标识不能超过 128 字符");
        }
        c.setModel(model);
        return c;
    }

    /** 从 kb_file.qa_generation_config / 任务行 config JSON 反序列化；空白返回 null */
    public static QaGenerationConfig fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSONUtil.toBean(json, QaGenerationConfig.class);
    }

    /** 序列化为任务行 config 快照 */
    public String toJson() {
        return JSONUtil.toJsonStr(this);
    }
}
