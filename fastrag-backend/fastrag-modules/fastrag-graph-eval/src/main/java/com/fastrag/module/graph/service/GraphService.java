package com.fastrag.module.graph.service;

/**
 * 知识图谱数据管理服务接口。
 *
 * <p>定义知识图谱的全功能业务操作，包括图谱数据可视化查询、子图搜索、
 * 图谱构建（全量/增量/重试）、文件图谱管理、PPR排序检索增强和设置管理。
 * 实现类为 {@link com.fastrag.module.graph.service.impl.GraphServiceImpl}。</p>
 *
 * <p>核心业务功能：</p>
 * <ul>
 *   <li>图谱可视化：获取图谱全量数据（节点+边）和统计信息</li>
 *   <li>子图查询：关键词搜索节点、获取实体类型标签列表</li>
 *   <li>图谱构建：支持full（全量）和incremental（增量）两种构建模式，
 *       从文档chunk中通过LLM提取实体和关系，构建知识图谱</li>
 *   <li>重试构建：清空现有图谱数据后全量重建</li>
 *   <li>文件管理：删除指定文件关联的所有图谱数据（实体、关系、提及记录）</li>
 *   <li>PPR排序：基于Personalized PageRank算法，对chunk进行相关性排序用于检索增强</li>
 *   <li>设置管理：获取和保存图谱构建配置参数</li>
 * </ul>
 *
 * @see com.fastrag.module.graph.service.impl.GraphServiceImpl
 */
import java.util.*;

public interface GraphService {
    Map<String,Object> getGraphData(String kbId,Integer maxNodes,Boolean excludeChunks);
    Map<String,Object> getGraphStats(String kbId);
    Map<String,Object> getIndexStatus(String kbId);
    void buildIndex(String kbId,String mode,List<String> fileIds);
    void retryBuild(String kbId);
    Map<String,Object> getBuildStatus(String kbId);
    Map<String,Object> getSettings(String kbId);
    void saveSettings(String kbId,Map<String,Object> settings);

    /** 关键词子图查询 */
    Map<String,Object> searchNodes(String kbId, String keyword, Integer maxNodes);

    /** 获取实体类型标签列表 */
    List<String> getLabels(String kbId);

    /** 删除文件关联的图谱数据 */
    void deleteFileGraph(String kbId, String fileId);

    /** Personalized PageRank 排序（检索增强用） */
    List<Map<String,Object>> rankChunksByPpr(String kbId, List<String> entityNames, int topK);
}
