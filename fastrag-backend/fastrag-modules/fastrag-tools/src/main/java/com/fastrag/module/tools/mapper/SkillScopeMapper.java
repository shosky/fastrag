package com.fastrag.module.tools.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.tools.entity.SkillScope;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 技能作用域Mapper接口。
 *
 * <p>对应 skill_scope 表，提供技能作用域（应用范围）配置的CRUD操作。
 * 定义技能在哪些应用或场景中可用。包含自定义删除方法：按技能ID删除其作用域记录。</p>
 */
@Mapper public interface SkillScopeMapper extends BaseMapper<SkillScope> {
    @Delete("DELETE FROM skill_scope WHERE skill_id = #{skillId}")
    void deleteBySkillId(String skillId);
}
