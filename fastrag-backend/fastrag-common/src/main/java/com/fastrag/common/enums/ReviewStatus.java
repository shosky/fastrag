package com.fastrag.common.enums;

/**
 * 审核状态枚举。
 *
 * <p>定义内容审核的处理结果状态：pending（待审核）、approved（审核通过）、
 * rejected（审核拒绝）、timeout（审核超时）。
 * 用于知识库内容审核流程的状态跟踪。</p>
 */
public enum ReviewStatus {
  pending, approved, rejected, timeout
}
