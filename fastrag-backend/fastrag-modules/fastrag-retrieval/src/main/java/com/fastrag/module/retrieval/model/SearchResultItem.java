package com.fastrag.module.retrieval.model;

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
    private int chunkIndex;
    private double distance;
    private List<String> highlights;
    private String previewSnippet;
}
