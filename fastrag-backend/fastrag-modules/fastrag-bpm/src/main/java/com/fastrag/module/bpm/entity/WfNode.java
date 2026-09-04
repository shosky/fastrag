package com.fastrag.module.bpm.entity;

import lombok.Data;

/**
 * 旧 /api/workflows/* 接口的节点入参 DTO。
 * 与新 BPM NodeRequest 字段重合，但旧前端会用驼峰 x/y，新 BPM 也兼容。
 */
@Data
public class WfNode {
    private String type;
    private String name;
    private Integer x;
    private Integer y;
}