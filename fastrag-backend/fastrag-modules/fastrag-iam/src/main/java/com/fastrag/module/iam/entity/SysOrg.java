package com.fastrag.module.iam.entity;

/**
 * 组织架构实体类，对应数据库表 {@code sys_org}，采用邻接表模式存储树形组织结构。
 *
 * <p>用于表示公司/部门的层级关系。每个组织节点通过 {@code parentId} 指向父节点，
 * 形成树形结构。支持多级部门嵌套。
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code name} —— 组织/部门名称</li>
 *   <li>{@code alias} —— 组织别名/简称</li>
 *   <li>{@code parentId} —— 父节点 ID，根节点为 {@code null} 或空字符串</li>
 *   <li>{@code level} —— 组织层级深度，根节点为 1</li>
 *   <li>{@code sort} —— 同级排序权重</li>
 * </ul>
 *
 * <p>由 {@code OrgServiceImpl} 负责树形结构的构建、CRUD 操作及部门成员查询。
 * {@code SysUser} 实体通过 {@code orgId} 字段关联到此实体，表示用户所属部门。
 *
 * @see com.fastrag.module.iam.service.impl.OrgServiceImpl
 * @see SysUser
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_org")
public class SysOrg {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String name;
    private String alias;
    private String parentId;
    private Integer level;
    private Integer sort;
}
