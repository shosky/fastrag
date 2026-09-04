package com.fastrag.module.bpm.entity;

import lombok.Data;

/**
 * 旧 /api/workflows/* 接口的测试用例入参 DTO。
 * 与新 BPM TestCaseRequest 字段对应：name/input/expected。
 */
@Data
public class WfTestCase {
    private String name;
    /** 输入参数 JSON 字符串或 Map */
    private Object input;
    /** 期望输出字符串 */
    private String expected;
}