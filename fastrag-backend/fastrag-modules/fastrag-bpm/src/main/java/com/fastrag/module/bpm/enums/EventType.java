package com.fastrag.module.bpm.enums;

/** 流程实例事件类型（bpm_flow_instance_event.event_type） */
public enum EventType {
    FLOW_STARTED, FLOW_COMPLETED, FLOW_FAILED, FLOW_PAUSED, FLOW_RESUMED, FLOW_CANCELLED,
    NODE_STARTED, NODE_COMPLETED, NODE_FAILED, NODE_TIMEOUT, INPUT_REQUESTED, INPUT_RECEIVED
}