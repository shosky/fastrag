package com.fastrag.module.application.service.preset;

/**
 * 直聊内置缺省常量持有者（new-conversation）。
 *
 * <p>契约不变式：与 AppConfigServiceImpl 的 getOrCreate 六默认值、AppServiceImpl 编排器
 * 缺省表达式同源——禁止在任何第二处写同义字面量（contract DialogPresetResolver Post 不变式）。
 * 既有类收敛到本持有者的改造随编排器里程碑执行（implement 文档登记）。
 */
public final class DialogDefaults {

    /** 与 AppServiceImpl 现行缺省 prompt 逐字节一致 */
    public static final String SYSTEM_PROMPT = "你是一个智能助手，请根据用户的问题提供帮助。";
    public static final double TEMPERATURE = 0.7;
    public static final int MAX_TOKENS = 2048;
    public static final int MEMORY_ROUNDS = 5;
    /** 模型路由终点：默认网关（现行语义：modelCode 查无 online 记录时的落点） */
    public static final String DEFAULT_MODEL = "default";

    private DialogDefaults() {
    }
}
