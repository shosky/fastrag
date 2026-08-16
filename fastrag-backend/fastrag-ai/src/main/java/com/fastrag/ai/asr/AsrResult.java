package com.fastrag.ai.asr;

/**
 * ASR（自动语音识别）结果模型，封装语音转文字的完整识别结果。
 *
 * <p>包含完整识别文本和按时间戳分割的分段信息，由 {@link AsrService} 调用
 * ASR 接口后解析返回。</p>
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code text} - 完整识别文本（所有分段拼接后的全文）</li>
 *   <li>{@code segments} - 分段列表，每段包含文本内容及起止时间（秒）</li>
 * </ul>
 *
 * <p>内部类 {@link AsrSegment} 表示单条语音分段，包含该段的文字内容、
 * 起始时间和结束时间，可用于实现字幕对齐、语音分段展示等场景。</p>
 */
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AsrResult {
    private String text;
    private List<AsrSegment> segments;

    @Data
    @Builder
    public static class AsrSegment {
        private String text;
        private Double start;
        private Double end;
    }
}
