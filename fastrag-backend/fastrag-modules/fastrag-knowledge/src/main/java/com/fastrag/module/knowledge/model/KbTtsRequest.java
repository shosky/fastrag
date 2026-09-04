package com.fastrag.module.knowledge.model;

import lombok.Data;

@Data
public class KbTtsRequest {
    /** 要朗读的文本；为空时使用知识条目 content */
    private String text;
    /** 音色 male / female，默认读取 ai.tts.voice 配置 */
    private String voice;
}
