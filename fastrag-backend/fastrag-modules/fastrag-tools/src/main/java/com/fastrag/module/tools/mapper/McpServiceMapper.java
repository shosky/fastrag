package com.fastrag.module.tools.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.tools.entity.McpService;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface McpServiceMapper extends BaseMapper<McpService> {
    @Select("SELECT COUNT(1) > 0 FROM mcp_service WHERE slug = #{slug}")
    boolean existsBySlug(String slug);

    @Select("SELECT * FROM mcp_service WHERE slug = #{slug} LIMIT 1")
    McpService selectBySlug(String slug);

    @Select("SELECT * FROM mcp_service WHERE is_builtin = 1 ORDER BY name")
    List<McpService> selectBuiltin();
}
