package com.fastrag.module.iam.model;
import lombok.Data; import java.util.List;

@Data
public class PermissionDto {
    private Long id;
    private String permKey;
    private String name;
    private String type;   // menu / action
    private String group;  // menu / kb / app / workflow / review / admin
    private String parentKey;
    private String category; // menu / page_action / api
    private String description;
    private List<String> roleIds;   // 拥有此权限的角色ID列表
    private List<PermissionDto> children;
}
