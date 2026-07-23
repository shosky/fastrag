package com.fastrag.module.iam.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.iam.entity.*; import com.fastrag.module.iam.mapper.*;
import com.fastrag.module.iam.model.*; import com.fastrag.module.iam.service.PermissionService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*; import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {
    private final SysPermissionMapper permMapper;
    private final SysRolePermissionMapper rpMapper;
    private final SysRoleMapper roleMapper;

    @Override
    public List<PermissionDto> listPermissions() {
        var perms = permMapper.selectList(null);
        // 批量加载每个权限关联的角色
        var allRps = rpMapper.selectList(null);
        var permKeyToRoleIds = allRps.stream()
            .collect(Collectors.groupingBy(SysRolePermission::getPermissionKey,
                Collectors.mapping(SysRolePermission::getRoleId, Collectors.toList())));
        return perms.stream().map(p -> {
            var d = toDto(p);
            d.setRoleIds(permKeyToRoleIds.getOrDefault(p.getPermKey(), List.of()));
            return d;
        }).collect(Collectors.toList());
    }

    @Override
    public List<PermissionDto> getPermissionTree() {
        var flat = listPermissions();
        // 按 group 分组构建树
        Map<String, List<PermissionDto>> groupMap = flat.stream()
            .collect(Collectors.groupingBy(p -> p.getGroup() != null ? p.getGroup() : "other", LinkedHashMap::new, Collectors.toList()));
        List<PermissionDto> tree = new ArrayList<>();
        for (var entry : groupMap.entrySet()) {
            var groupNode = new PermissionDto();
            groupNode.setPermKey("group:" + entry.getKey());
            groupNode.setName(getGroupLabel(entry.getKey()));
            groupNode.setType("group");
            groupNode.setGroup(entry.getKey());
            // 在 group 下按 parentKey 构建子树
            groupNode.setChildren(buildSubTree(entry.getValue()));
            tree.add(groupNode);
        }
        return tree;
    }

    @Override
    public PermissionDto createPermission(PermissionCreateRequest req) {
        if (permMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermKey, req.getPermKey())) != null)
            throw BusinessException.badRequest("权限标识已存在");
        var p = new SysPermission();
        p.setPermKey(req.getPermKey()); p.setName(req.getName());
        p.setType(req.getType()); p.setGroup(req.getGroup());
        p.setParentKey(req.getParentKey());
        p.setCategory(req.getCategory() != null ? req.getCategory() : "page_action");
        p.setDescription(req.getDescription());
        permMapper.insert(p);
        return toDto(p);
    }

    @Override
    public PermissionDto updatePermission(Long id, PermissionCreateRequest req) {
        var p = permMapper.selectById(id);
        if (p == null) throw BusinessException.notFound("权限不存在");
        p.setName(req.getName()); p.setType(req.getType());
        p.setGroup(req.getGroup()); p.setParentKey(req.getParentKey());
        if (req.getCategory() != null) p.setCategory(req.getCategory());
        p.setDescription(req.getDescription());
        permMapper.updateById(p);
        return toDto(p);
    }

    @Override
    public void deletePermission(Long id) {
        var p = permMapper.selectById(id);
        if (p == null) throw BusinessException.notFound("权限不存在");
        // 删除角色-权限关联
        rpMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
            .eq(SysRolePermission::getPermissionKey, p.getPermKey()));
        permMapper.deleteById(id);
    }

    private PermissionDto toDto(SysPermission p) {
        var d = new PermissionDto();
        d.setId(p.getId()); d.setPermKey(p.getPermKey());
        d.setName(p.getName()); d.setType(p.getType());
        d.setGroup(p.getGroup()); d.setParentKey(p.getParentKey());
        d.setCategory(p.getCategory());
        d.setDescription(p.getDescription());
        return d;
    }

    /** 在 group 内部按 parentKey 构建层级树 */
    private List<PermissionDto> buildSubTree(List<PermissionDto> perms) {
        Map<String, List<PermissionDto>> childrenMap = new LinkedHashMap<>();
        List<PermissionDto> roots = new ArrayList<>();
        for (var p : perms) {
            String parent = p.getParentKey();
            if (parent == null || parent.isEmpty()) {
                roots.add(p);
            } else {
                childrenMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(p);
            }
        }
        // 递归设置子节点
        for (var root : roots) {
            root.setChildren(buildChildren(root.getPermKey(), childrenMap));
        }
        return roots;
    }

    private List<PermissionDto> buildChildren(String parentKey, Map<String, List<PermissionDto>> childrenMap) {
        List<PermissionDto> children = childrenMap.getOrDefault(parentKey, List.of());
        for (var child : children) {
            child.setChildren(buildChildren(child.getPermKey(), childrenMap));
        }
        return children;
    }

    private String getGroupLabel(String group) {
        return switch (group) {
            case "menu" -> "菜单权限";
            case "kb" -> "知识库";
            case "admin" -> "管理后台";
            case "app" -> "应用中心";
            case "workflow" -> "业务流";
            case "review" -> "审核管理";
            default -> group;
        };
    }
}
