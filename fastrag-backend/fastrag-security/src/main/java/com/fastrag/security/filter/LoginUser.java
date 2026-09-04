package com.fastrag.security.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
public class LoginUser implements Serializable, java.security.Principal {
    private String userId;
    private String username;
    private List<String> roles;
    private List<String> permissions;

    /**
     * 重写 Principal.getName():Spring Security 的 AbstractAuthenticationToken.getName()
     * 默认调用 principal.toString(),@Data 生成的 toString 会展开全部字段,长度超标导致 DB 写入失败。
     * 这里返回 userId,与 bpm/iam 等模块把 owner_id 当作 userId 引用对齐。
     * 如需 username,请显式调 getUsername(),例如 SecurityUtil.getCurrentUser().getUsername()。
     */
    @Override
    public String getName() {
        return userId;
    }

    public boolean hasPermission(String perm) {
        return permissions.contains("*") || permissions.contains(perm);
    }

    public boolean hasAnyPermission(String... perms) {
        if (permissions.contains("*")) return true;
        for (String p : perms) {
            if (permissions.contains(p)) return true;
        }
        return false;
    }
}
