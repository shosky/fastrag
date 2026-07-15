package com.fastrag.module.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.module.knowledge.entity.KbTag;
import com.fastrag.module.knowledge.entity.KbTagRelation;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbTagMapper;
import com.fastrag.module.knowledge.mapper.KbTagRelationMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.knowledge.model.KbCreateRequest;
import com.fastrag.module.knowledge.model.KbDto;
import com.fastrag.module.knowledge.service.KbService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KbServiceImpl implements KbService {
    private final KnowledgeBaseMapper mapper;
    private final KbTagMapper kbTagMapper;
    private final KbTagRelationMapper kbTagRelationMapper;

    @Override
    public Map<String, Object> list(String kw, String cat, int page, int pageSize) {
        var w = new LambdaQueryWrapper<KnowledgeBase>();
        if (StrUtil.isNotBlank(kw)) w.like(KnowledgeBase::getName, kw).or().like(KnowledgeBase::getDescription, kw);
        if (StrUtil.isNotBlank(cat)) w.eq(KnowledgeBase::getCategory, cat);
        w.orderByDesc(KnowledgeBase::getCreatedAt);
        var r = mapper.selectPage(new Page<>(page, pageSize), w);
        var result = new HashMap<String, Object>();
        result.put("list", r.getRecords().stream().map(this::toDto).collect(Collectors.toList()));
        result.put("total", r.getTotal());
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    @Override
    public KbDto get(String id) {
        var e = mapper.selectById(id);
        if (e == null) throw new RuntimeException("KB not found: " + id);
        return toDto(e);
    }

    @Override
    @Transactional
    public KbDto create(KbCreateRequest req, String creator) {
        var e = new KnowledgeBase();
        e.setName(req.getName());
        e.setCategory(req.getCategory());
        e.setDescription(req.getDescription());
        e.setTags(req.getTags() != null ? JSONUtil.toJsonStr(req.getTags()) : null);
        e.setPermission(req.getPermission() != null ? req.getPermission() : "private");
        e.setEmbeddingModel(req.getEmbeddingModel());
        e.setParseMode(req.getParseMode());
        e.setSplitMode(req.getSplitMode());
        e.setGraphAutoBuild(req.getGraphAutoBuild() != null && req.getGraphAutoBuild() ? 1 : 0);
        e.setFileTypeConfig(req.getFileTypeConfig() != null ? JSONUtil.toJsonStr(req.getFileTypeConfig()) : null);
        e.setRetrievalConfig(req.getRetrievalConfig() != null ? JSONUtil.toJsonStr(req.getRetrievalConfig()) : null);
        e.setCreator(creator);
        e.setUsedSize(0L);
        e.setTotalSize(0L);
        e.setType("personal");
        mapper.insert(e);

        // 处理标签实体
        if (req.getTags() != null && !req.getTags().isEmpty()) {
            syncTagRelations(e.getId(), req.getTags(), creator);
            // 更新非规范化缓存
            e.setTags(JSONUtil.toJsonStr(req.getTags()));
            mapper.updateById(e);
        }

        return toDto(e);
    }

    @Override
    @Transactional
    public KbDto update(String id, KbCreateRequest req) {
        var e = mapper.selectById(id);
        if (e == null) throw new RuntimeException("KB not found: " + id);
        if (req.getName() != null) e.setName(req.getName());
        if (req.getCategory() != null) e.setCategory(req.getCategory());
        if (req.getDescription() != null) e.setDescription(req.getDescription());
        if (req.getTags() != null) e.setTags(JSONUtil.toJsonStr(req.getTags()));
        if (req.getEmbeddingModel() != null) e.setEmbeddingModel(req.getEmbeddingModel());
        if (req.getParseMode() != null) e.setParseMode(req.getParseMode());
        if (req.getSplitMode() != null) e.setSplitMode(req.getSplitMode());
        if (req.getPermission() != null) e.setPermission(req.getPermission());
        if (req.getFileTypeConfig() != null) e.setFileTypeConfig(JSONUtil.toJsonStr(req.getFileTypeConfig()));
        if (req.getRetrievalConfig() != null) e.setRetrievalConfig(JSONUtil.toJsonStr(req.getRetrievalConfig()));
        if (req.getGraphAutoBuild() != null) e.setGraphAutoBuild(req.getGraphAutoBuild() ? 1 : 0);
        mapper.updateById(e);

        // 同步标签实体
        if (req.getTags() != null) {
            syncTagRelations(id, req.getTags(), null);
        }

        return toDto(e);
    }

    @Override
    public void delete(String id) {
        mapper.deleteById(id);
    }

    @Override
    public List<Map<String, Object>> getCategories() {
        var all = mapper.selectList(null);
        var grouped = all.stream().filter(e -> StrUtil.isNotBlank(e.getCategory()))
                .collect(Collectors.groupingBy(KnowledgeBase::getCategory, Collectors.counting()));
        var result = new ArrayList<Map<String, Object>>();
        grouped.forEach((k, v) -> {
            var m = new HashMap<String, Object>();
            m.put("id", k);
            m.put("name", k);
            m.put("count", v);
            result.add(m);
        });
        return result;
    }

    /**
     * 同步 KB 的标签关联：删除旧关联，创建新关联，更新标签使用计数
     */
    private void syncTagRelations(String kbId, List<String> tagNames, String creator) {
        // 1. 删除该 KB 的所有旧关联
        kbTagRelationMapper.delete(new LambdaQueryWrapper<KbTagRelation>()
                .eq(KbTagRelation::getTargetType, "kb")
                .eq(KbTagRelation::getTargetId, kbId));

        // 2. 获取旧标签列表以便减少 usage_count
        List<KbTagRelation> oldRels = kbTagRelationMapper.selectList(
                new LambdaQueryWrapper<KbTagRelation>()
                        .eq(KbTagRelation::getTargetType, "kb")
                        .eq(KbTagRelation::getTargetId, kbId));
        for (var rel : oldRels) {
            KbTag tag = kbTagMapper.selectById(rel.getTagId());
            if (tag != null && tag.getUsageCount() != null && tag.getUsageCount() > 0) {
                tag.setUsageCount(tag.getUsageCount() - 1);
                kbTagMapper.updateById(tag);
            }
        }
        // 已在上一步删除，重新查为空列表

        // 3. 为每个标签名创建/查找标签，创建关联
        for (String name : tagNames) {
            if (StrUtil.isBlank(name)) continue;
            name = name.trim();

            // 查找或创建标签
            KbTag tag = kbTagMapper.selectOne(
                    new LambdaQueryWrapper<KbTag>().eq(KbTag::getName, name));
            if (tag == null) {
                tag = new KbTag();
                tag.setName(name);
                tag.setUsageCount(0);
                tag.setCreatedBy(creator);
                kbTagMapper.insert(tag);
            }

            // 创建关联
            KbTagRelation rel = new KbTagRelation();
            rel.setTagId(tag.getId());
            rel.setTargetType("kb");
            rel.setTargetId(kbId);
            kbTagRelationMapper.insert(rel);

            // 增加使用计数
            tag.setUsageCount(tag.getUsageCount() == null ? 1 : tag.getUsageCount() + 1);
            kbTagMapper.updateById(tag);
        }
    }

    private KbDto toDto(KnowledgeBase e) {
        var d = new KbDto();
        d.setId(e.getId());
        d.setName(e.getName());
        d.setDescription(e.getDescription());
        d.setCategory(e.getCategory());
        d.setTags(StrUtil.isNotBlank(e.getTags()) ? JSONUtil.toList(e.getTags(), String.class) : null);
        d.setEmbeddingModel(e.getEmbeddingModel());
        d.setDimension(e.getDimension());
        d.setCreator(e.getCreator());
        d.setCreatedAt(e.getCreatedAt());
        d.setUsedSize(e.getUsedSize());
        d.setTotalSize(e.getTotalSize());
        d.setType(e.getType());
        d.setParseMode(e.getParseMode());
        d.setSplitMode(e.getSplitMode());
        d.setPermission(e.getPermission());
        d.setGraphAutoBuild(e.getGraphAutoBuild());
        d.setFileTypeConfig(StrUtil.isNotBlank(e.getFileTypeConfig()) ? JSONUtil.parse(e.getFileTypeConfig()) : null);
        d.setRetrievalConfig(StrUtil.isNotBlank(e.getRetrievalConfig()) ? JSONUtil.parse(e.getRetrievalConfig()) : null);
        return d;
    }
}
