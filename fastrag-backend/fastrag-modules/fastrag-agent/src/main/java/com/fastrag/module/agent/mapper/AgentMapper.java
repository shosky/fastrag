package com.fastrag.module.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.agent.entity.Agent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AgentMapper extends BaseMapper<Agent> {

    @Select("SELECT COUNT(*) FROM agent WHERE slug = #{slug}")
    boolean existsBySlug(@Param("slug") String slug);

    @Select("SELECT * FROM agent WHERE slug = #{slug} LIMIT 1")
    Agent selectBySlug(@Param("slug") String slug);
}
