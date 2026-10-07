package com.fastrag.module.operation.model;

/**
 * 审计结果（outcome）枚举——sys_audit_log.status 列的语义扩展（backlog S5 / gap-review P1-11）。
 *
 * <p>原语义仅 success|failed（操作成败）；扩展后统一承载四类结果：
 * 拦截/拒绝类事件（如敏感词 checkInput 命中、鉴权拒绝）应写 {@link #REJECTED} / {@link #FILTERED}，
 * 使审计查询可按 outcome 维度区分「操作失败」与「被安全策略拦截」。
 * 敏感词 checkInput 处的埋点接线登记后续批次（当前事件发布方仍以 success/failed 为主）。</p>
 */
public enum AuditOutcome {

    /** 操作成功 */
    SUCCESS("success"),
    /** 操作失败（异常/报错） */
    FAILED("failed"),
    /** 被拒绝（鉴权/权限类拦截） */
    REJECTED("rejected"),
    /** 被过滤（内容安全类拦截，如敏感词命中） */
    FILTERED("filtered");

    private final String code;

    AuditOutcome(String code) {
        this.code = code;
    }

    /** 入库值（与 sys_audit_log.status 列现值小写风格一致） */
    public String code() {
        return code;
    }
}
