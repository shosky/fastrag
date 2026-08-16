package com.fastrag.module.iam.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 用户登录响应DTO。
 *
 * <p>封装登录成功后的返回数据，包含登录令牌(token)和当前用户信息(UserInfoDto)。
 * 用户信息包含用户ID、用户名、真实姓名、头像、电话、邮箱、所属组织ID、
 * 角色列表和权限列表。被 AuthController 的 login 接口返回。</p>
 */
@Data @Builder
public class LoginResponse {
    private String token;
    private UserInfoDto userInfo;

    /**
     * 当前登录用户信息。
     */
    @Data @Builder
    public static class UserInfoDto {
        private String id;
        private String username;
        private String realName;
        private String avatar;
        private String phone;
        private String email;
        private String orgId;
        private List<String> roles;
        private List<String> permissions;
    }
}
