package com.fastrag.module.retrieval.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.retrieval.entity.KbRetrievalLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface KbRetrievalLogMapper extends BaseMapper<KbRetrievalLog> {

    @Select("<script>" +
            "SELECT COUNT(*) AS total_queries, " +
            "SUM(CASE WHEN has_result = 0 THEN 1 ELSE 0 END) AS no_result_count, " +
            "ROUND(IFNULL(AVG(latency_ms), 0), 2) AS avg_latency_ms, " +
            "ROUND(IFNULL(AVG(hit_count), 0), 2) AS avg_hit_count " +
            "FROM kb_retrieval_log" +
            " WHERE 1=1" +
            "<if test='kbId != null and kbId != \"\"'> AND kb_id = #{kbId}</if>" +
            "<if test='kbIds != null and kbIds.size() > 0'> AND kb_id IN " +
            "<foreach collection='kbIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>" +
            "</script>")
    Map<String, Object> aggregateStats(@Param("kbId") String kbId, @Param("kbIds") List<String> kbIds);

    @Select("<script>" +
            "SELECT query, COUNT(*) AS count FROM kb_retrieval_log " +
            "WHERE query IS NOT NULL" +
            "<if test='kbId != null and kbId != \"\"'> AND kb_id = #{kbId}</if>" +
            "<if test='kbIds != null and kbIds.size() > 0'> AND kb_id IN " +
            "<foreach collection='kbIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>" +
            " GROUP BY query ORDER BY count DESC LIMIT 10" +
            "</script>")
    List<Map<String, Object>> selectTopQueries(@Param("kbId") String kbId, @Param("kbIds") List<String> kbIds);

    @Select("<script>" +
            "SELECT query, COUNT(*) AS count FROM kb_retrieval_log " +
            "WHERE has_result = 0 AND query IS NOT NULL" +
            "<if test='kbId != null and kbId != \"\"'> AND kb_id = #{kbId}</if>" +
            "<if test='kbIds != null and kbIds.size() > 0'> AND kb_id IN " +
            "<foreach collection='kbIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>" +
            " GROUP BY query ORDER BY count DESC LIMIT 10" +
            "</script>")
    List<Map<String, Object>> selectNoResultQueries(@Param("kbId") String kbId, @Param("kbIds") List<String> kbIds);
}
