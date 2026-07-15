package com.fastrag.module.tools.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.tools.entity.SkillScope;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface SkillScopeMapper extends BaseMapper<SkillScope> {
    @Delete("DELETE FROM skill_scope WHERE skill_id = #{skillId}")
    void deleteBySkillId(String skillId);
}
