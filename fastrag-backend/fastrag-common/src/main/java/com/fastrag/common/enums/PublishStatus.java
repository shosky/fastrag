package com.fastrag.common.enums;

/**
 * 发布状态枚举。
 *
 * <p>定义应用发布的生命周期状态：draft（草稿）、pending_review（待审核）、
 * approved（审核通过）、published（已发布）、rejected（审核拒绝）。
 * 用于应用发布流程的状态管理。</p>
 */
public enum PublishStatus {
  draft, pending_review, approved, published, rejected
}
