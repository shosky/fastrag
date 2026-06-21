package com.fastrag.security.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
public class LoginUser implements Serializable {
    private String userId;
    private String username;
    private List<String> roles;
    private List<String> permissions;

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
