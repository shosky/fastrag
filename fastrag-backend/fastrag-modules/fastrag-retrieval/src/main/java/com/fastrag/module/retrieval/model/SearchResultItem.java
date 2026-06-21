package com.fastrag.module.retrieval.model;
import lombok.Data;
import java.util.List;
@Data public class SearchResultItem { private int index; private double similarity; private String content,source,fileId; private int chunkIndex; private double distance; private List<String> highlights; private String previewSnippet; }
