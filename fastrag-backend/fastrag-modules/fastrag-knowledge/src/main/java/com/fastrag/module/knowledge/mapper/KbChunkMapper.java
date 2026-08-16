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
     * 排除父分片（chunk_type='parent'）：父分片不参与召回，仅作为命中后的上下文
     */
    @Select("SELECT *, MATCH(content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) AS relevance " +
            "FROM kb_chunk " +
            "WHERE kb_id = #{kbId} " +
            "  AND MATCH(content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) > 0 " +
            "  AND (chunk_type IS NULL OR chunk_type != 'parent') " +
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
     * 按 fileId 查询所有子分片（按 chunk_index 排序），用于 parent_document / window 上下文组装。
     * 排除父分片（chunk_type='parent'），避免整篇拼接时父分片内容与子分片重复。
     */
    @Select("SELECT * FROM kb_chunk WHERE file_id = #{fileId} " +
            "AND (chunk_type IS NULL OR chunk_type != 'parent') ORDER BY chunk_index ASC")
    List<KbChunk> selectByFileId(@Param("fileId") String fileId);

    /**
     * 按父分片 ID 查询其全部子分片（按 chunk_index 排序），用于分片列表父子展示
     */
    @Select("SELECT * FROM kb_chunk WHERE parent_id = #{parentId} ORDER BY chunk_index ASC")
    List<KbChunk> selectChildrenByParentId(@Param("parentId") String parentId);

    /**
     * 获取指定文件下最大的子分片 chunkIndex，用于新增分片时确定插入位置
     * 返回 -1 表示该文件还没有分片（排除父分片，父分片索引取首个子分片位置）
     */
    @Select("SELECT COALESCE(MAX(chunk_index), -1) FROM kb_chunk WHERE file_id = #{fileId} " +
            "AND (chunk_type IS NULL OR chunk_type != 'parent')")
    int selectMaxChunkIndex(@Param("fileId") String fileId);
}
