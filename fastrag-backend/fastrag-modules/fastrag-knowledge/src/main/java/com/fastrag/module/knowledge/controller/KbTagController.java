package com.fastrag.module.knowledge.controller;
/**
 * 知识库标签管理控制器（分册四 rag-file-metadata-management.md）。
 *
 * <p>提供知识库标签的查询与写管理 API：创建/更新/删除（激活既有只读端点未激活的 CRUD 能力），
 * 供文件打标、KB 标签管理页使用。标签归属于创建时的 KB（kb_tag.kbId），跨 KB 共享标签不做。</p>
 *
 * <p>REST 端点（基础路径 /api/kb-tags）：
 * <ul>
 *   <li>GET /             — 查询标签列表（可传 kbId 过滤；无权限限制，需登录）</li>
 *   <li>GET /{id}         — 获取单个标签详情</li>
 *   <li>POST /            — 创建标签（需所归属 KB 的 editor 及以上权限，手动 ACL 校验）</li>
 *   <li>PUT /{id}         — 更新标签（需该标签归属 KB 的 editor 及以上权限）</li>
 *   <li>DELETE /{id}      — 删除标签（需 editor 及以上权限；仅允许无任何引用的标签，先清引用）</li>
 * </ul></p>
 *
 * <p>注意：本控制器不在 /api/kb/{kbId} 路径下，KbAuthAspect 无法自动解析 kbId，
 * 写操作通过 KbAccessChecker 手动校验（与 KbAuthAspect 同策略：API token 放行、ACL 优先、组织兜底）。</p>
 */
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.enums.KBRole;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.entity.KbTag;
import com.fastrag.module.knowledge.mapper.KbTagMapper;
import com.fastrag.module.knowledge.model.KbTagRequest;
import com.fastrag.module.knowledge.service.TagRelationHelper;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.service.KbAccessChecker;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kb-tags")
@RequiredArgsConstructor
public class KbTagController {

    private final KbTagMapper kbTagMapper;
    private final TagRelationHelper tagRelationHelper;
    private final KbAccessChecker accessChecker;

    /**
     * 列出所有标签（可选按 kbId 过滤），按名称升序，用于前端自动补全/标签管理页
     */
    @GetMapping
    public ApiResponse<List<KbTag>> list(@RequestParam(required = false) String kbId) {
        LambdaQueryWrapper<KbTag> wrapper = new LambdaQueryWrapper<KbTag>()
                .orderByAsc(KbTag::getName);
        if (StrUtil.isNotBlank(kbId)) {
            wrapper.eq(KbTag::getKbId, kbId);
        }
        return ApiResponse.success(kbTagMapper.selectList(wrapper));
    }

    /**
     * 获取单个标签详情
     */
    @GetMapping("/{id}")
    public ApiResponse<KbTag> get(@PathVariable String id) {
        return ApiResponse.success(kbTagMapper.selectById(id));
    }

    /**
     * 创建标签：需所归属 KB 的 editor 及以上权限（手动 ACL 校验）
     */
    @PostMapping
    public ApiResponse<KbTag> create(@RequestBody KbTagRequest req) {
        requireKbEditor(req.getKbId());
        if (StrUtil.isBlank(req.getName())) {
            throw BusinessException.badRequest("标签名称不能为空");
        }
        KbTag exist = kbTagMapper.selectOne(new LambdaQueryWrapper<KbTag>()
                .eq(KbTag::getName, req.getName().trim()));
        if (exist != null) {
            throw BusinessException.badRequest("标签名称已存在: " + req.getName().trim());
        }
        KbTag tag = new KbTag();
        tag.setKbId(req.getKbId());
        tag.setName(req.getName().trim());
        tag.setColor(blankToNull(req.getColor()));
        tag.setDescription(blankToNull(req.getDescription()));
        tag.setTagTypeId(blankToNull(req.getTagTypeId()));
        tag.setUsageCount(0);
        tag.setCreatedBy(SecurityUtil.getCurrentUser() != null
                ? SecurityUtil.getCurrentUser().getUsername() : "system");
        kbTagMapper.insert(tag);
        return ApiResponse.success(tag);
    }

    /**
     * 更新标签：需该标签归属 KB 的 editor 及以上权限
     */
    @PutMapping("/{id}")
    public ApiResponse<KbTag> update(@PathVariable String id, @RequestBody KbTagRequest req) {
        KbTag tag = kbTagMapper.selectById(id);
        if (tag == null) throw BusinessException.notFound("标签不存在");
        requireKbEditor(tag.getKbId());
        if (StrUtil.isNotBlank(req.getName())) {
            String newName = req.getName().trim();
            KbTag dup = kbTagMapper.selectOne(new LambdaQueryWrapper<KbTag>()
                    .eq(KbTag::getName, newName)
                    .ne(KbTag::getId, id));
            if (dup != null) throw BusinessException.badRequest("标签名称已存在: " + newName);
            tag.setName(newName);
        }
        if (req.getColor() != null) tag.setColor(blankToNull(req.getColor()));
        if (req.getDescription() != null) tag.setDescription(blankToNull(req.getDescription()));
        if (req.getTagTypeId() != null) tag.setTagTypeId(blankToNull(req.getTagTypeId()));
        kbTagMapper.updateById(tag);
        return ApiResponse.success(tag);
    }

    /**
     * 删除标签：需 editor 及以上权限；仅允许无任何引用的标签（usage_count/relation 为 0）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        KbTag tag = kbTagMapper.selectById(id);
        if (tag == null) throw BusinessException.notFound("标签不存在");
        requireKbEditor(tag.getKbId());
        tagRelationHelper.deleteTag(id);
        return ApiResponse.success();
    }

    // ===== 权限校验（与 KbAuthAspect 同策略） =====

    /** 要求当前用户在指定 KB 具备 editor 及以上权限；平台级 API token 全局放行 */
    private void requireKbEditor(String kbId) {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (user == null) throw BusinessException.forbidden("未登录");
        if (user.getUserId().startsWith("api-token:")) return;
        KBRole role = accessChecker.resolveRole(kbId, user);
        if (role == null || role.ordinal() > KBRole.editor.ordinal()) {
            throw BusinessException.forbidden("知识库权限不足（需要编辑权限）");
        }
    }

    private String blankToNull(String s) {
        return StrUtil.isBlank(s) ? null : s.trim();
    }
}