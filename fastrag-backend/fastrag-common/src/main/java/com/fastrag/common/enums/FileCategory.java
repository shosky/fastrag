package com.fastrag.common.enums;

/**
 * 文件类别枚举。
 * <p>对上传到知识库的文件进行分类，用于文件管理、解析策略选择和存储路径路由。
 *
 * <p>枚举值说明：
 * <ul>
 *   <li>{@code document} - 文档类文件，如 PDF、DOCX、TXT、MD 等，通常需要进行文本提取和分片处理</li>
 *   <li>{@code image} - 图片类文件，如 PNG、JPG、GIF 等，可能需要进行 OCR 识别或多模态模型处理</li>
 *   <li>{@code audio} - 音频类文件，如 MP3、WAV 等，可能需要进行语音转文字处理</li>
 *   <li>{@code video} - 视频类文件，如 MP4、AVI 等，可能需要进行关键帧提取或字幕解析</li>
 * </ul>
 *
 * <p>使用场景：在文件上传、解析策略匹配、文件类型过滤等场景中作为分类依据。
 */
public enum FileCategory {
  document, image, audio, video
}
