package com.fastrag.module.tools.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.mapper.SkillMapper;
import com.fastrag.module.tools.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private final SkillMapper mapper;

    @Override
    public List<Skill> list(String keyword, String category) {
        LambdaQueryWrapper<Skill> w = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) w.like(Skill::getName, keyword);
        if (StrUtil.isNotBlank(category)) w.eq(Skill::getCategory, category);
        w.orderByDesc(Skill::getUpdatedAt);
        return mapper.selectList(w);
    }

    @Override
    public List<Skill> listAccessible(String keyword, String category, String sourceType) {
        LambdaQueryWrapper<Skill> w = new LambdaQueryWrapper<>();
        w.eq(Skill::getEnabled, 1);
        if (StrUtil.isNotBlank(keyword)) w.like(Skill::getName, keyword);
        if (StrUtil.isNotBlank(category)) w.eq(Skill::getCategory, category);
        if (StrUtil.isNotBlank(sourceType)) w.eq(Skill::getSourceType, sourceType);
        w.orderByDesc(Skill::getUpdatedAt);
        return mapper.selectList(w);
    }

    @Override
    public List<Skill> listBuiltin() {
        return mapper.selectBuiltin();
    }

    @Override
    public Skill get(String id) {
        return mapper.selectById(id);
    }

    @Override
    public Skill getBySlug(String slug) {
        return mapper.selectBySlug(slug);
    }

    @Override
    public Skill create(Map<String, Object> form) {
        Skill s = new Skill();
        s.setName((String) form.get("name"));
        s.setIdentifier((String) form.get("identifier"));
        s.setSlug((String) form.get("slug"));
        s.setDescription((String) form.get("description"));
        s.setSourceType((String) form.getOrDefault("sourceType", "custom"));
        s.setCategory((String) form.get("category"));
        s.setEnabled(1);
        mapper.insert(s);
        return s;
    }

    @Override
    public Skill update(String id, Map<String, Object> form) {
        Skill s = mapper.selectById(id);
        if (s != null) {
            if (form.containsKey("name")) s.setName((String) form.get("name"));
            if (form.containsKey("description")) s.setDescription((String) form.get("description"));
            if (form.containsKey("category")) s.setCategory((String) form.get("category"));
            if (form.containsKey("content")) s.setContent((String) form.get("content"));
            if (form.containsKey("metadata")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = (Map<String, Object>) form.get("metadata");
                s.setMetadata(meta);
            }
            mapper.updateById(s);
        }
        return s;
    }

    @Override
    public void delete(String id) {
        Skill s = mapper.selectById(id);
        if (s != null && Integer.valueOf(1).equals(s.getIsBuiltin())) {
            throw new RuntimeException("内置技能不允许删除");
        }
        mapper.deleteById(id);
    }

    @Override
    public void toggleEnabled(String id) {
        Skill s = mapper.selectById(id);
        if (s != null) {
            s.setEnabled(s.getEnabled() == 1 ? 0 : 1);
            mapper.updateById(s);
        }
    }

    @Override
    public boolean existsBySlug(String slug) {
        return mapper.existsBySlug(slug);
    }
}
