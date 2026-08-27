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
     * 获取指定 KB（可选 fileId）全部叶（非 parent）分片的字符总数，用于管理页"总 Token"聚合。
     * 排除父分片，避免父子模式重复统计（parent 内容 = 子分片拼接）。
     */
    @Select("SELECT COALESCE(SUM(CHAR_LENGTH(content)), 0) FROM kb_chunk " +
            "WHERE kb_id = #{kbId} AND (chunk_type IS NULL OR chunk_type != 'parent') " +
            "AND (#{fileId} IS NULL OR #{fileId} = '' OR file_id = #{fileId})")
    long sumLeafCharLength(@Param("kbId") String kbId, @Param("fileId") String fileId);

    /**
     * 获取指定文件下最大的子分片 chunkIndex，用于新增分片时确定插入位置
     * 返回 -1 表示该文件还没有分片（排除父分片，父分片索引取首个子分片位置）
     */
    @Select("SELECT COALESCE(MAX(chunk_index), -1) FROM kb_chunk WHERE file_id = #{fileId} " +
            "AND (chunk_type IS NULL OR chunk_type != 'parent')")
    int selectMaxChunkIndex(@Param("fileId") String fileId);

    /**
     * 获取指定文件下手动分片的最大序号（ID 形如 {fileId}_manual_{n}），
     * 用于手动创建分片时分配不冲突的 ID。无手动分片返回 0。
     */
    @Select("SELECT IFNULL(MAX(CAST(SUBSTRING_INDEX(id, '_manual_', -1) AS UNSIGNED)), 0) " +
            "FROM kb_chunk WHERE file_id = #{fileId} AND LOCATE('_manual_', id) > 0")
    int selectMaxManualSeq(@Param("fileId") String fileId);
}
