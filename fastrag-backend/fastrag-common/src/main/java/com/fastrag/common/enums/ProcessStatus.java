package com.fastrag.common.enums;

/**
 * 处理状态枚举。
 *
 * <p>定义异步任务的处理生命周期状态：pending（待处理）、processing（处理中）、
 * completed（已完成）、failed（失败）。
 * 用于知识库文件摄入、图谱构建等异步任务的状态跟踪。</p>
 */
public enum ProcessStatus {
  pending, processing, completed, failed
}
