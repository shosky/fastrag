package com.fastrag.common.enums;

/**
 * 工具类型枚举。
 *
 * <p>定义系统中AI Agent可调用工具的类型：builtin（内置工具，如文件系统操作等）、
 * knowledge（知识库工具，用于检索知识库内容）、http（HTTP工具，调用外部REST API）。
 * 用于工具注册表的工具分类和Agent调用时的类型路由。</p>
 */
public enum ToolType {
  builtin, knowledge, http
}
