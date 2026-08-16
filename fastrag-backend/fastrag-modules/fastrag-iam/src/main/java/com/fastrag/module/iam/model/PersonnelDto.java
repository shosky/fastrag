package com.fastrag.module.iam.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 人员信息DTO。
 *
 * <p>表示系统中一个人员（用户）的完整信息，包含用户ID、用户名、真实姓名、
 * 电话、邮箱、所属组织ID和名称、状态、关联的角色ID和角色名称列表、创建时间。
 * 用于人员管理列表和详情展示。</p>
 */
@Data
public class PersonnelDto {
    private String id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String orgId;
    private String orgName;
    private String status;
    private List<String> roleIds;
    private List<String> roleNames;
    private LocalDateTime createdAt;
}
