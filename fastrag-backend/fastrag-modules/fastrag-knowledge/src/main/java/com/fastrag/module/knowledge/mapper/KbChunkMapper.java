package com.fastrag.module.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.knowledge.entity.KbChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KbChunkMapper extends BaseMapper<KbChunk> {

    /**
     * MySQL FULLTEXT 搜索（使用 MATCH...AGAINST IN NATURAL LANGUAGE MODE）
     * 依赖 kb_chunk.content 上的 FULLTEXT 索引 idx_content_fulltext
     */
    @Select("SELECT *, MATCH(content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) AS relevance " +
            "FROM kb_chunk " +
            "WHERE kb_id = #{kbId} " +
            "  AND MATCH(content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) > 0 " +
            "ORDER BY relevance DESC " +
            "LIMIT #{limit}")
    List<KbChunk> fulltextSearch(@Param("kbId") String kbId,
                                 @Param("query") String query,
                                 @Param("limit") int limit);

    /**
     * 按 ID 列表批量查询 chunk
     */
    @Select({"<script>",
            "SELECT * FROM kb_chunk WHERE id IN ",
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"})
    List<KbChunk> selectByIds(@Param("ids") List<String> ids);

    /**
     * 按 fileId 查询所有 chunk（按 chunk_index 排序），用于 parent_document 上下文组装
     */
    @Select("SELECT * FROM kb_chunk WHERE file_id = #{fileId} ORDER BY chunk_index ASC")
    List<KbChunk> selectByFileId(@Param("fileId") String fileId);
}
