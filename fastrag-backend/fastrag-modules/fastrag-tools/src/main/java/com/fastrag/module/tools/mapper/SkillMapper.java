package com.fastrag.module.tools.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.tools.entity.Skill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SkillMapper extends BaseMapper<Skill> {
    @Select("SELECT COUNT(1) > 0 FROM skill WHERE slug = #{slug}")
    boolean existsBySlug(String slug);

    @Select("SELECT * FROM skill WHERE slug = #{slug} LIMIT 1")
    Skill selectBySlug(String slug);

    @Select("SELECT * FROM skill WHERE is_builtin = 1 ORDER BY name")
    List<Skill> selectBuiltin();

    @Select("SELECT created_by FROM skill WHERE id = #{id}")
    String selectCreatedBy(String id);
}
