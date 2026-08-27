package com.fastrag.module.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.knowledge.entity.KbTag;
import com.fastrag.module.knowledge.entity.KbTagRelation;
import com.fastrag.module.knowledge.mapper.KbTagMapper;
import com.fastrag.module.knowledge.mapper.KbTagRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 标签关联辅助器（分册四 rag-file-metadata-management.md）。
 *
 * <p>统一维护 kb_tag_relation（targetType 支持 'kb'/'file'）与 kb_tag.usage_count 的增删改。
 * 所有文件打标/去标/KB 打标路径都走这里，保证 usage_count 与 relation 一致。
 * 方法本身不开启事务，由调用方（Service 层）统一控制（@Transactional）。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TagRelationHelper {

    private final KbTagMapper tagMapper;
    private final KbTagRelationMapper relationMapper;

    /** 目标类型常量 */
    public static final String TARGET_KB = "kb";
    public static final String TARGET_FILE = "file";

    /** 查询某目标当前关联的所有标签（按关联创建时间排序） */
    public List<KbTag> listTags(String targetType, String targetId) {
        List<KbTagRelation> rels = relationMapper.selectList(new LambdaQueryWrapper<KbTagRelation>()
                .eq(KbTagRelation::getTargetType, targetType)
                .eq(KbTagRelation::getTargetId, targetId));
        if (rels.isEmpty()) return new ArrayList<>();
        Map<String, KbTagRelation> byTagId = rels.stream().collect(Collectors.toMap(KbTagRelation::getTagId, r -> r));
        var tagIds = rels.stream().map(KbTagRelation::getTagId).collect(Collectors.toList());
        return tagMapper.selectBatchIds(tagIds).stream()
                .sorted(Comparator.comparing(t -> byTagId.get(t.getId()).getCreatedAt() == null ? 0L
                        : byTagId.get(t.getId()).getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC)))
                .collect(Collectors.toList());
    }

    /** 替换式设置标签：删除不在新列表中的关联，新增缺少的关联（差量更新 usage_count） */
    public void replaceTags(String targetType, String targetId, List<String> tagIds) {
        List<String> want = tagIds == null ? new ArrayList<>() : tagIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());

        // 1. 旧关联（删除前先查询，用于减 usage_count）
        List<KbTagRelation> oldRels = relationMapper.selectList(new LambdaQueryWrapper<KbTagRelation>()
                .eq(KbTagRelation::getTargetType, targetType)
                .eq(KbTagRelation::getTargetId, targetId));
        List<String> oldIds = oldRels.stream().map(KbTagRelation::getTagId).collect(Collectors.toList());

        // 2. 差量：要新增 / 要移除
        List<String> toAdd = want.stream().filter(id -> !oldIds.contains(id)).collect(Collectors.toList());
        List<String> toRemove = oldIds.stream().filter(id -> !want.contains(id)).collect(Collectors.toList());

        // 3. 校验标签存在且未删除
        for (String id : toAdd) {
            if (tagMapper.selectById(id) == null) {
                throw BusinessException.badRequest("标签不存在: " + id);
            }
        }

        // 4. 移除并减计数
        for (String id : toRemove) {
            relationMapper.delete(new LambdaQueryWrapper<KbTagRelation>()
                    .eq(KbTagRelation::getTagId, id)
                    .eq(KbTagRelation::getTargetType, targetType)
                    .eq(KbTagRelation::getTargetId, targetId));
            decrementUsage(id);
        }

        // 5. 新增并加计数
        for (String id : toAdd) {
            KbTagRelation rel = new KbTagRelation();
            rel.setTagId(id);
            rel.setTargetType(targetType);
            rel.setTargetId(targetId);
            try {
                relationMapper.insert(rel);
            } catch (Exception e) {
                // 唯一索引 uk_tag_target 冲突（并发时）：已存在则忽略，计数不重复累加
                log.debug("Tag relation already exists: {}/{}/{}", targetType, targetId, id);
                continue;
            }
            incrementUsage(id);
        }
    }

    /** 增量添加标签（已存在则忽略，计数不重复累加） */
    public void addTag(String targetType, String targetId, String tagId) {
        if (tagMapper.selectById(tagId) == null) {
            throw BusinessException.badRequest("标签不存在: " + tagId);
        }
        long exists = relationMapper.selectCount(new LambdaQueryWrapper<KbTagRelation>()
                .eq(KbTagRelation::getTagId, tagId)
                .eq(KbTagRelation::getTargetType, targetType)
                .eq(KbTagRelation::getTargetId, targetId));
        if (exists > 0) return;
        KbTagRelation rel = new KbTagRelation();
        rel.setTagId(tagId);
        rel.setTargetType(targetType);
        rel.setTargetId(targetId);
        relationMapper.insert(rel);
        incrementUsage(tagId);
    }

    /** 增量移除标签（不存在则忽略，计数不误减） */
    public void removeTag(String targetType, String targetId, String tagId) {
        int deleted = relationMapper.delete(new LambdaQueryWrapper<KbTagRelation>()
                .eq(KbTagRelation::getTagId, tagId)
                .eq(KbTagRelation::getTargetType, targetType)
                .eq(KbTagRelation::getTargetId, targetId));
        if (deleted > 0) {
            decrementUsage(tagId);
        }
    }

    /** 删除标签本体（仅允许无任何引用的标签被删除：usage_count 为 0 时清理关联后删除） */
    public void deleteTag(String tagId) {
        KbTag tag = tagMapper.selectById(tagId);
        if (tag == null) throw BusinessException.notFound("标签不存在");
        long relationCount = relationMapper.selectCount(new LambdaQueryWrapper<KbTagRelation>()
                .eq(KbTagRelation::getTagId, tagId));
        if (relationCount > 0) {
            throw BusinessException.badRequest("标签仍被 " + relationCount + " 个目标引用，无法删除（请先移除引用）");
        }
        // 冗余安全校验：usage_count 必须为 0
        if (tag.getUsageCount() != null && tag.getUsageCount() > 0) {
            throw BusinessException.badRequest("标签 usage_count 异常非零，请先清理引用后重试");
        }
        relationMapper.delete(new LambdaQueryWrapper<KbTagRelation>()
                .eq(KbTagRelation::getTagId, tagId));
        tagMapper.deleteById(tagId);
    }

    /** 统计某标签被引用的目标数（关系数，跨 targetType） */
    public long countTagReferences(String tagId) {
        return relationMapper.selectCount(new LambdaQueryWrapper<KbTagRelation>()
                .eq(KbTagRelation::getTagId, tagId));
    }

    private void incrementUsage(String tagId) {
        KbTag tag = tagMapper.selectById(tagId);
        if (tag == null) return;
        tag.setUsageCount(tag.getUsageCount() == null ? 1 : tag.getUsageCount() + 1);
        tagMapper.updateById(tag);
    }

    private void decrementUsage(String tagId) {
        KbTag tag = tagMapper.selectById(tagId);
        if (tag == null) return;
        if (tag.getUsageCount() != null && tag.getUsageCount() > 0) {
            tag.setUsageCount(tag.getUsageCount() - 1);
            tagMapper.updateById(tag);
        }
    }
}