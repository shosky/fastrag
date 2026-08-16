package com.fastrag.module.knowledge.controller;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.entity.KbCategory;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbCategoryMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.publish.service.LogService;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 知识库分类管理控制器，按组织隔离提供分类的 CRUD 操作。
 *
 * <p>核心职责：
 * 管理知识库分类（KbCategory），实现分类的组织级隔离与统计。所有分类数据
 * 严格按组织隔离，不同组织的用户只能看到和操作本组织分类；仅平台级 API Token
 * 享有全局可见和管理权限。分类列表中附带每个分类下本组织知识库的数量统计。
 *
 * <p>可见性与权限规则：
 * <ul>
 *   <li>普通登录用户（含超管/kb_admin）：仅可见和操作所属组织的分类，count 仅统计本组织知识库</li>
 *   <li>平台级 API Token（userId 以 "api-token:" 开头）：全局可见、可创建（可指定任意组织）、可编辑和删除任意分类</li>
 * </ul>
 *
 * <p>REST 端点（基础路径 /api/kb-categories）：
 * <ul>
 *   <li>GET    /              — 按组织查询分类列表（含知识库数量统计），API Token 返回全量</li>
 *   <li>GET    /{id}          — 获取单个分类详情</li>
 *   <li>GET    /{id}/usage    — 查询分类下知识库数量（口径与列表一致）</li>
 *   <li>POST   /              — 创建分类（权限键 kb:category:create），非 API Token 强制归属自己组织</li>
 *   <li>PUT    /{id}          — 更新分类（权限键 kb:category:edit），非 API Token 不能迁移组织归属</li>
 *   <li>DELETE /{id}          — 删除分类（权限键 kb:category:delete），非 API Token 仅能删除本组织分类</li>
 * </ul>
 *
 * <p>依赖服务：KbCategoryMapper（分类数据访问）、KnowledgeBaseMapper（知识库统计）、
 * LogService（操作审计日志）。
 */
@RestController @RequestMapping("/api/kb-categories") @RequiredArgsConstructor
public class KbCategoryController {
    private final KbCategoryMapper mapper;
    private final KnowledgeBaseMapper kbMapper;
    private final LogService logService;

    private boolean isApiToken(LoginUser user) {
        return user.getUserId().startsWith("api-token:");
    }

    @GetMapping public ApiResponse<?> list() {
        LoginUser user = SecurityUtil.getCurrentUser();
        boolean apiToken = isApiToken(user);

        // 分类范围：API Token 全量；其余所有用户仅本组织（超管/kb_admin 一致）
        var catW = new LambdaQueryWrapper<KbCategory>().orderByAsc(KbCategory::getSort);
        if (!apiToken) {
            if (!StringUtils.hasText(user.getOrgId())) return ApiResponse.success(Collections.emptyList());
            catW.eq(KbCategory::getOrgId, user.getOrgId());
        }
        var cats = mapper.selectList(catW);

        // count 仅统计本组织知识库（同组织成员看到完全一致的统计；API Token 统计全部）
        List<KnowledgeBase> allKbs = Collections.emptyList();
        if (apiToken) {
            allKbs = kbMapper.selectList(null);
        } else if (StringUtils.hasText(user.getOrgId())) {
            allKbs = kbMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                    .eq(KnowledgeBase::getOrgId, user.getOrgId()));
        }
        Map<String, Long> countMap = new HashMap<>();
        for (var kb : allKbs) {
            if (kb.getCategory() != null) countMap.merge(kb.getCategory(), 1L, Long::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (var c : cats) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId()); m.put("name", c.getName());
            m.put("description", c.getDescription()); m.put("color", c.getColor());
            m.put("icon", c.getIcon()); m.put("sort", c.getSort());
            m.put("orgId", c.getOrgId());
            m.put("count", countMap.getOrDefault(c.getId(), 0L));
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) {
        return ApiResponse.success(mapper.selectById(id));
    }

    @GetMapping("/{id}/usage") public ApiResponse<?> usage(@PathVariable String id) {
        KbCategory c = mapper.selectById(id);
        if (c == null) return ApiResponse.success(Map.of("count", 0));
        // 仅统计本组织知识库（与列表页 count 口径一致，同组织成员结果相同；API Token 统计全部）
        LoginUser user = SecurityUtil.getCurrentUser();
        var w = new LambdaQueryWrapper<KnowledgeBase>().eq(KnowledgeBase::getCategory, c.getId());
        if (!isApiToken(user) && StringUtils.hasText(user.getOrgId())) {
            w.eq(KnowledgeBase::getOrgId, user.getOrgId());
        }
        long usage = isApiToken(user) || StringUtils.hasText(user.getOrgId()) ? kbMapper.selectCount(w) : 0L;
        return ApiResponse.success(Map.of("categoryId", id, "categoryName", c.getName(), "count", usage));
    }

    /**
     * 新增分类：权限键 kb:category:create。
     * 所有登录用户（含超管/kb_admin）创建的分类强制归属自己的组织（API Token 可指定任意组织）。
     */
    @PostMapping @PreAuthorize("@perm.has('kb:category:create')") public ApiResponse<?> create(@RequestBody KbCategory c) {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (!isApiToken(user)) c.setOrgId(user.getOrgId());
        if (!StringUtils.hasText(c.getOrgId())) return ApiResponse.badRequest("请选择所属组织");
        if (c.getSort() == null) c.setSort(0);
        c.setCreatedAt(LocalDateTime.now());
        mapper.insert(c);
        try {
            logService.addLog("global", LogCategory.operation, ActionType.config_changed,
                    c.getId(), "创建知识库分类: " + c.getName(), "system", "success", null);
        } catch (Exception e) {
            // ignore log failure
        }
        return ApiResponse.success(c);
    }

    /**
     * 更新分类：权限键 kb:category:edit。
     * 所有登录用户仅能修改本组织分类且不能改归属（API Token 可操作任意分类）。
     */
    @PutMapping("/{id}") @PreAuthorize("@perm.has('kb:category:edit')") public ApiResponse<?> update(@PathVariable String id, @RequestBody KbCategory c) {
        LoginUser user = SecurityUtil.getCurrentUser();
        KbCategory existing = mapper.selectById(id);
        if (existing == null) throw BusinessException.notFound("分类不存在");
        if (!isApiToken(user)) {
            if (!user.getOrgId().equals(existing.getOrgId())) throw BusinessException.forbidden("无权修改其他组织的分类");
            c.setOrgId(existing.getOrgId()); // 不能迁移归属
        }
        c.setId(id); mapper.updateById(c);
        try {
            logService.addLog("global", LogCategory.operation, ActionType.config_changed,
                    id, "更新知识库分类: " + c.getName(), "system", "success", null);
        } catch (Exception e) {
            // ignore log failure
        }
        return ApiResponse.success(mapper.selectById(id));
    }

    /**
     * 删除分类：权限键 kb:category:delete。
     * 所有登录用户仅能删除本组织分类（API Token 可删除任意分类）。
     */
    @DeleteMapping("/{id}") @PreAuthorize("@perm.has('kb:category:delete')") public ApiResponse<?> delete(@PathVariable String id) {
        LoginUser user = SecurityUtil.getCurrentUser();
        KbCategory c = mapper.selectById(id);
        if (c == null) throw BusinessException.notFound("分类不存在");
        if (!isApiToken(user) && !user.getOrgId().equals(c.getOrgId())) {
            throw BusinessException.forbidden("无权删除其他组织的分类");
        }
        mapper.deleteById(id);
        try {
            logService.addLog("global", LogCategory.operation, ActionType.config_changed,
                    id, "删除知识库分类: " + c.getName(), "system", "success", null);
        } catch (Exception e) {
            // ignore log failure
        }
        return ApiResponse.success();
    }
}
