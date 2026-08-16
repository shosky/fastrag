package com.fastrag.common.enums;

/**
 * 日志分类枚举。
 *
 * <p>定义系统日志的分类维度：operation（操作日志，记录用户操作行为）、
 * retrieval（检索日志，记录向量检索和问答调用）、publish（发布日志，记录应用发布操作）。
 * 用于日志的分类存储和检索过滤。</p>
 */
public enum LogCategory {
  operation, retrieval, publish
}
