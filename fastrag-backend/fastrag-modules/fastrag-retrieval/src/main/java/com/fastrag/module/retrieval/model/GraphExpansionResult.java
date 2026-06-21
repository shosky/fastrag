package com.fastrag.module.retrieval.model;
import lombok.Data;
import java.util.List; import java.util.Map;
@Data public class GraphExpansionResult { private List<Map<String,Object>> entities; private List<Map<String,Object>> relations; private String expandedQuery; }
