package com.fastrag.module.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.common.enums.KBRole;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbQaPair;
import com.fastrag.module.knowledge.entity.KbTag;
import com.fastrag.module.knowledge.entity.KbTagRelation;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.knowledge.mapper.KbTagMapper;
import com.fastrag.module.knowledge.mapper.KbTagRelationMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.knowledge.model.KbCreateRequest;
import com.fastrag.module.knowledge.model.KbDto;
import com.fastrag.module.knowledge.service.KbService;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.service.KbAccessChecker;
import com.fastrag.security.service.KbAclService;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KbServiceImpl implements KbService {
    private final KnowledgeBaseMapper mapper;
    private final KbTagMapper kbTagMapper;
    private final KbTagRelationMapper kbTagRelationMapper;
    private final KbFileMapper fileMapper;
    private final KbChunkMapper chunkMapper;
    private final MilvusService milvusService;
    private final GraphStore graphStore;
    private final MinioService minioService;
    private final KbQaPairMapper qaPairMapper;
    private final KbAclService aclService;
    private final KbAccessChecker accessChecker;

    /** 仅平台级 API Token（程序化访问）全局可见；所有登录用户（含超管/kb_admin）按本组织 ∪ ACL 过滤 */
    private boolean isPlatformAdmin(LoginUser user) {
        return user.getUserId().startsWith("api-token:");
    }

    @Override
    public Map<String, Object> list(String kw, String cat, int page, int pageSize) {
        var w = new LambdaQueryWrapper<KnowledgeBase>();
        // 平台管理员（超管/kb_admin/API Token）：可见全部知识库；普通用户仅本组织库 ∪ ACL 授权库
        LoginUser user = SecurityUtil.getCurrentUser();
        boolean admin = user != null && isPlatformAdmin(user);
        if (!admin) {
            List<String> accessible = user != null
                    ? accessChecker.getAccessibleKbIds(user.getUserId(), user.getOrgId())
                    : Collections.emptyList();
            if (accessible.isEmpty()) {
                var empty = new HashMap<String, Object>();
                empty.put("list", Collections.emptyList());
                empty.put("total", 0);
                empty.put("page", page);
                empty.put("pageSize", pageSize);
                return empty;
            }
            w.in(KnowledgeBase::getId, accessible);
        }
        // 关键词/分类筛选（两者都为空时不拼条件，避免生成 WHERE () 导致 SQL 语法错误）
        if (StrUtil.isNotBlank(kw) || StrUtil.isNotBlank(cat)) {
            w.and(q -> {
                if (StrUtil.isNotBlank(kw)) q.like(KnowledgeBase::getName, kw).or().like(KnowledgeBase::getDescription, kw);
                if (StrUtil.isNotBlank(cat)) q.eq(KnowledgeBase::getCategory, cat);
            });
        }
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
        e.setGraphLlmModel(req.getGraphLlmModel());
        e.setFileTypeConfig(req.getFileTypeConfig() != null ? JSONUtil.toJsonStr(req.getFileTypeConfig()) : null);
        e.setRetrievalConfig(req.getRetrievalConfig() != null ? JSONUtil.toJsonStr(req.getRetrievalConfig()) : null);
        e.setCreator(creator);
        e.setOrgId(SecurityUtil.getCurrentUser() != null ? SecurityUtil.getCurrentUser().getOrgId() : null);
        e.setUsedSize(0L);
        e.setTotalSize(0L);
        // 类型跟随共享设置：指定人共享(private)→personal，全局/部门→team
        e.setType("private".equals(e.getPermission()) ? "personal" : "team");
        mapper.insert(e);

        // 创建者自动成为知识库 owner
        aclService.addAclEntry(e.getId(), creator, KBRole.owner, creator);

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
        if (req.getGraphLlmModel() != null) e.setGraphLlmModel(req.getGraphLlmModel());
        mapper.updateById(e);

        // 同步标签实体
        if (req.getTags() != null) {
            syncTagRelations(id, req.getTags(), null);
        }

        return toDto(e);
    }

    @Override
    @Transactional
    public void delete(String id) {
        // 1. 查询该 KB 下所有文件（含已软删除的），逐个清理关联数据
        List<KbFile> files = fileMapper.selectList(
                new LambdaQueryWrapper<KbFile>().eq(KbFile::getKbId, id));
        String collection = "kb_" + id;
        for (KbFile f : files) {
            String fileId = f.getId();
            // 清理 chunks
            chunkMapper.delete(new LambdaQueryWrapper<KbChunk>()
                    .eq(KbChunk::getKbId, id).eq(KbChunk::getFileId, fileId));
            // 清理 Milvus 向量
            try {
                milvusService.deleteByFileId(collection, fileId);
            } catch (Exception e) {
                log.warn("[KB Delete] Milvus cleanup failed for file {}: {}", fileId, e.getMessage());
            }
            // 清理知识图谱
            try {
                graphStore.deleteFileGraph(id, fileId);
            } catch (Exception e) {
                log.warn("[KB Delete] Graph cleanup failed for file {}: {}", fileId, e.getMessage());
            }
            // 清理 MinIO 存储文件
            try {
                if (f.getObjectKey() != null) {
                    minioService.delete(f.getObjectKey());
                }
                minioService.deleteByPrefix(id + "/" + fileId);
            } catch (Exception e) {
                log.warn("[KB Delete] MinIO cleanup failed for file {}: {}", fileId, e.getMessage());
            }
            // 清理 QA 对
            try {
                qaPairMapper.delete(new LambdaQueryWrapper<KbQaPair>()
                        .eq(KbQaPair::getKbId, id)
                        .eq(KbQaPair::getFileId, fileId));
            } catch (Exception e) {
                log.warn("[KB Delete] QA pair cleanup failed for file {}: {}", fileId, e.getMessage());
            }
        }
        // 2. 删除该 KB 下所有文件记录（含已软删除的）
        fileMapper.delete(new LambdaQueryWrapper<KbFile>().eq(KbFile::getKbId, id));
        // 3. 删除 KB 本身
        mapper.deleteById(id);
        log.info("[KB Delete] Knowledge base {} deleted, {} files cleaned up", id, files.size());
    }

    @Override
    public List<Map<String, Object>> getCategories() {
        // 仅统计当前用户可访问的知识库（平台管理员统计全部；普通用户为本组织库 ∪ ACL 授权库）
        LoginUser user = SecurityUtil.getCurrentUser();
        boolean admin = user != null && isPlatformAdmin(user);
        List<KnowledgeBase> all = Collections.emptyList();
        if (admin) {
            all = mapper.selectList(null);
        } else if (user != null) {
            List<String> accessible = accessChecker.getAccessibleKbIds(user.getUserId(), user.getOrgId());
            if (!accessible.isEmpty()) {
                all = mapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                        .in(KnowledgeBase::getId, accessible));
            }
        }
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
                tag.setKbId(kbId);
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
        d.setOrgId(e.getOrgId());
        d.setCreatedAt(e.getCreatedAt());
        d.setUsedSize(e.getUsedSize());
        d.setTotalSize(e.getTotalSize());
        d.setType(e.getType());
        d.setParseMode(e.getParseMode());
        d.setSplitMode(e.getSplitMode());
        d.setPermission(e.getPermission());
        d.setGraphAutoBuild(e.getGraphAutoBuild());
        d.setGraphLlmModel(e.getGraphLlmModel());
        d.setFileTypeConfig(StrUtil.isNotBlank(e.getFileTypeConfig()) ? JSONUtil.parse(e.getFileTypeConfig()) : null);
        d.setRetrievalConfig(StrUtil.isNotBlank(e.getRetrievalConfig()) ? JSONUtil.parse(e.getRetrievalConfig()) : null);
        return d;
    }
}
