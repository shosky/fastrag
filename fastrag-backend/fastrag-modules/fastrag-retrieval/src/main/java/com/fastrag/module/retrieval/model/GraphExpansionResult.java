package com.fastrag.module.retrieval.model;

/**
 * 知识图谱扩展结果模型。
 *
 * <p>封装图谱扩展操作的返回结果，包含从图谱中展开得到的实体列表、
 * 关系列表以及增强后的查询文本。用于在检索前对用户查询进行图谱增强。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code entities} - 图谱扩展得到的实体列表（Map 结构，包含 name、type 等属性）</li>
 *   <li>{@code relations} - 图谱扩展得到的关系列表（Map 结构，包含 label、source、target 等属性）</li>
 *   <li>{@code expandedQuery} - 将图谱实体名称和关系标签拼入原始 query 后的增强查询文本</li>
 * </ul>
 */
import lombok.Data;
import java.util.List; import java.util.Map;
@Data public class GraphExpansionResult { private List<Map<String,Object>> entities; private List<Map<String,Object>> relations; private String expandedQuery; }
