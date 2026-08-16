package com.fastrag.module.iam.model;

import lombok.Data;
import java.util.List;

/**
 * 组织架构节点DTO。
 *
 * <p>表示组织架构中的一个节点，包含节点ID、名称、别名、父节点ID、层级、
 * 成员数量和子节点列表。用于组织架构树的构建和展示。
 * 被 OrgService 相关接口返回。</p>
 */
@Data
public class OrgNodeDto {
    private String id;
    private String name;
    private String alias;
    private String parentId;
    private Integer level;
    private Integer memberCount;
    private List<OrgNodeDto> children;
}
