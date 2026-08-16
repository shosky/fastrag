package com.fastrag.ai.model;

/**
 * 文本向量化（Embedding）请求模型，封装发送给 Embedding API 的请求参数。
 *
 * <p>本类是 {@link com.fastrag.ai.embedding.EmbeddingService} 的请求载体，
 * 遵循 OpenAI Embeddings API 的请求格式。</p>
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code model} - 嵌入模型名称，如 "text-embedding-v3"、"BAAI/bge-large-zh-v1.5" 等</li>
 *   <li>{@code input} - 待向量化的文本列表，支持批量输入，API 会为每条文本返回一个向量</li>
 * </ul>
 *
 * <p>注意：EmbeddingService 在发送请求时，会将非 ASCII 字符（如中文）转义为
 * Unicode 转义序列（\\uXXXX）以兼容 SiliconFlow 等网关的 UTF-8 处理限制。</p>
 */
import lombok.Data;
import java.util.List;

@Data
public class EmbeddingRequest {
    private String model;
    private List<String> input;
}
