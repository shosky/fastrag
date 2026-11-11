package com.fastrag.module.application.widget.dto;

/**
 * 回传推进计划（IF-005.prepare 产出）：可见消息文本随当轮用户消息落库，
 * 结构化标注块仅注入本轮引擎输入（不落消息列）。
 */
public record WidgetTurnPlan(String visibleMessageText, String contextBlock) {}
