package com.fastrag.module.iam.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

/**
 * 人员创建请求DTO。
 *
 * <p>封装创建人员（用户）的请求参数，包含用户名（必填）、真实姓名（必填）、
 * 电话、邮箱、密码、所属组织ID和角色ID列表。被 PersonnelService 的 createPersonnel 接口使用。</p>
 */
@Data
public class PersonnelCreateRequest {
    @NotBlank private String username;
    @NotBlank private String realName;
    private String phone;
    private String email;
    private String password;
    private String orgId;
    private List<String> roleIds;
}
