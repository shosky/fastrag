package com.fastrag.module.tools.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.tools.entity.SkillDependency;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 技能依赖关系Mapper接口。
 *
 * <p>对应 skill_dependency 表，提供技能依赖关系的CRUD操作。
 * 包含自定义删除方法：按技能ID删除其所有依赖记录。</p>
 */
@Mapper public interface SkillDependencyMapper extends BaseMapper<SkillDependency> {
    @Delete("DELETE FROM skill_dependency WHERE skill_id = #{skillId}")
    void deleteBySkillId(String skillId);
}
