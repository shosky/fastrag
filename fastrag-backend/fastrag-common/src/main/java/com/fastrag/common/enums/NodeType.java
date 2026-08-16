package com.fastrag.common.enums;

/**
 * 工作流节点类型枚举。
 *
 * <p>定义工作流编排中各种节点的类型：start（起始节点）、end（结束节点）、
 * llm（LLM调用节点）、knowledge_retrieval（知识库检索节点）、intent（意图识别节点）、
 * selector（条件选择节点）、plugin（插件调用节点）。
 * 用于工作流图的构建和执行引擎对节点的分发处理。</p>
 */
public enum NodeType {
  start, end, llm, knowledge_retrieval, intent, selector, plugin
}
