package com.fastrag.module.iam.manager;
import com.fastrag.security.util.SecurityUtil; import org.springframework.stereotype.Component;
@Component("perm") public class PermissionChecker {
    public boolean has(String p) { return SecurityUtil.hasPermission(p); }
    public boolean hasAny(String... ps) { try { return SecurityUtil.getCurrentUser().hasAnyPermission(ps); } catch(Exception e){return false;} }
}
