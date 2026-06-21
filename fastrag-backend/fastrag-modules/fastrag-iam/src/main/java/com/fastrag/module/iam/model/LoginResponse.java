package com.fastrag.module.iam.model;
import lombok.Builder; import lombok.Data; import java.util.List;
@Data @Builder public class LoginResponse {
    private String token; private UserInfoDto userInfo;
    @Data @Builder public static class UserInfoDto {
        private String id; private String username; private String realName; private String avatar;
        private String phone; private String email; private List<String> roles; private List<String> permissions;
    }
}
