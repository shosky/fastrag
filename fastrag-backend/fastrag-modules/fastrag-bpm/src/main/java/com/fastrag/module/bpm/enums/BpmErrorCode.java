package com.fastrag.module.bpm.enums;
import com.fastrag.common.exception.BusinessException;

/**
 * BPM 错误码（区间 40000-40999，命名空间与其它模块隔离）。
 * 所有业务异常通过 {@code BusinessException.of(code, msg)} 抛出，GlobalExceptionHandler 统一处理。
 */
public enum BpmErrorCode {

    // 流程定义 / 版本
    FLOW_NOT_FOUND(40001, "流程不存在"),
    FLOW_VERSION_NOT_FOUND(40002, "流程版本不存在"),
    FLOW_NOT_PUBLISHED(40003, "流程未发布,不可执行"),
    FLOW_VALIDATION_FAILED(40010, "流程定义校验失败"),
    FLOW_CIRCULAR_REFERENCE(40011, "流程存在循环引用"),
    FLOW_SUBFLOW_DEPTH_EXCEEDED(40012, "子流程嵌套超过5层"),
    FLOW_CANVAS_SIZE_EXCEEDED(40013, "流程画布大小超过限制"),

    // 节点
    NODE_TYPE_NOT_REGISTERED(40020, "未注册的节点类型: {0}"),
    NODE_CONFIG_INVALID(40021, "节点参数校验失败: {0}"),
    NODE_START_NOT_FOUND(40022, "流程缺少开始节点"),
    NODE_END_NOT_FOUND(40023, "流程缺少结束节点"),
    NODE_UNREACHABLE(40024, "存在不可达节点"),
    NODE_HAS_CYCLE(40025, "流程存在环"),

    // 实例
    INSTANCE_NOT_FOUND(40030, "实例不存在"),
    INSTANCE_STATE_INVALID(40031, "实例当前状态不允许此操作"),
    INSTANCE_INPUT_TIMEOUT(40032, "用户输入超时"),
    INSTANCE_INPUT_TOKEN_INVALID(40033, "用户输入 token 无效或已过期"),

    // 执行
    EXECUTION_TIMEOUT(40040, "节点执行超时"),
    EXECUTION_FAILED(40041, "节点执行失败: {0}"),
    EXPRESSION_PARSE_ERROR(40042, "表达式解析错误: {0}"),
    EXPRESSION_EVAL_ERROR(40043, "表达式求值错误: {0}"),

    // 权限
    PERMISSION_DENIED(40050, "无权操作该流程"),
    VISIBILITY_DENIED(40051, "该流程不可见"),

    // 子流程
    SUBFLOW_NOT_FOUND(40060, "子流程不存在或未发布"),
    SUBFLOW_INPUT_MAPPING_ERROR(40061, "子流程输入映射错误"),
    SUBFLOW_OUTPUT_MAPPING_ERROR(40062, "子流程输出映射错误"),

    // 模板 / 测试用例
    TEMPLATE_NOT_FOUND(40070, "模板不存在"),
    TEST_CASE_NOT_FOUND(40071, "测试用例不存在"),

    // 导入导出
    IMPORT_FORMAT_INVALID(40080, "导入文件格式错误"),
    IMPORT_VERSION_NOT_SUPPORTED(40081, "导入文件版本不兼容");

    private final int code;
    private final String message;

    BpmErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    /** 将 message 中的 {0}/{1} 占位符按 args 格式化后,生成对应 BusinessException。例:BpmErrorCode.NODE_CONFIG_INVALID.of("字段 x 缺失") */
    public BusinessException of(Object... args) {
        String msg = args == null || args.length == 0 ? message : java.text.MessageFormat.format(message, args);
        return BusinessException.of(code, msg);
    }
}