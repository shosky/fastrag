package com.fastrag.module.retrieval.model;

/**
 * 检索结果条目模型。
 *
 * <p>封装单条检索结果的信息，包括匹配内容、相似度分数、来源通道和关联元数据。
 * 支持多种检索来源（Milvus向量、MySQL全文、知识图谱、QA对）的统一结果表示。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code index} - 结果序号（排序后的位置）</li>
 *   <li>{@code similarity} - 相似度分数（0~1，越高越相关）</li>
 *   <li>{@code distance} - 距离分数（= 1 - similarity）</li>
 *   <li>{@code content} - 匹配的文本内容</li>
 *   <li>{@code source} - 数据来源（mysql / milvus / mysql_fulltext / graph / qa）</li>
 *   <li>{@code channel} - 召回通道（vector / fulltext / graph / qa），用于多路召回场景</li>
 *   <li>{@code fileId} - 来源文件ID</li>
 *   <li>{@code chunkIndex} - 分块在文件中的序号</li>
 *   <li>{@code highlights} - 高亮片段列表</li>
 *   <li>{@code previewSnippet} - 内容预览（截取前200字）</li>
 * </ul>
 */
import lombok.Data;
import java.util.List;

@Data
public class SearchResultItem {
    private int index;
    private double similarity;
    private String content;
    private String source;        // mysql / milvus / mysql_fulltext / graph / qa
    private String channel;       // vector / fulltext / graph / qa （多路召回来源通道）
    private String fileId;
    private String fileName;      // 来源文件名（检索时从 kb_file 查得）
    private String chunkType;     // 分块类型："text" / "image" / "parent"
    private List<String> imageKeys; // PDF 提取的图片对象键列表（JSON 数组）
    private int chunkIndex;
    private String parentId;      // 父子分片：命中的子分片所属父分片 ID（单层分片为 null）
    private double distance;
    private List<String> highlights;
    private String previewSnippet;
}
