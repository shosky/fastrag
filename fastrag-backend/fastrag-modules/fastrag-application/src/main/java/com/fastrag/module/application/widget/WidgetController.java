package com.fastrag.module.application.widget;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.application.widget.dto.WidgetSaveRequest;
import com.fastrag.module.application.widget.entity.Widget;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Widget 资产 REST 面（API-001~006）：登录即可（widget:* 权限键预留不强校验，快照 #4/#24）；
 * 属主隔离在服务层裁决。
 */
@RestController
@RequestMapping("/api/widgets")
@RequiredArgsConstructor
public class WidgetController {

    private final WidgetAssetService widgetAssetService;

    @GetMapping
    public ApiResponse<List<Widget>> list(@RequestParam(defaultValue = "false") boolean builtin) {
        LoginUser user = SecurityUtil.getCurrentUser();
        return ApiResponse.success(widgetAssetService.list(builtin, user.getUserId(), isAdmin(user)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Widget> get(@PathVariable String id) {
        LoginUser user = SecurityUtil.getCurrentUser();
        return ApiResponse.success(widgetAssetService.get(id, user.getUserId(), isAdmin(user)));
    }

    @PostMapping
    public ApiResponse<Widget> create(@RequestBody WidgetSaveRequest body) {
        LoginUser user = SecurityUtil.getCurrentUser();
        Widget w = widgetAssetService.create(body.getName(), body.getDescription(), body.getIcon(),
                body.getTemplate(), body.getSchemaJson(), body.getDefaultJson(), user.getUserId());
        return ApiResponse.success(w);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable String id, @RequestBody WidgetSaveRequest body) {
        LoginUser user = SecurityUtil.getCurrentUser();
        widgetAssetService.update(id, body.getName(), body.getDescription(), body.getIcon(),
                body.getTemplate(), body.getSchemaJson(), body.getDefaultJson(),
                user.getUserId(), isAdmin(user));
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        LoginUser user = SecurityUtil.getCurrentUser();
        widgetAssetService.delete(id, user.getUserId(), isAdmin(user));
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/copy")
    public ApiResponse<Widget> copy(@PathVariable String id) {
        LoginUser user = SecurityUtil.getCurrentUser();
        return ApiResponse.success(widgetAssetService.copy(id, user.getUserId()));
    }

    /** 管理员判定：对齐 SandboxConfigServiceImpl 口径（admin/superadmin） */
    private boolean isAdmin(LoginUser user) {
        if (user == null || user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream().anyMatch(r ->
                "admin".equalsIgnoreCase(r) || "superadmin".equalsIgnoreCase(r));
    }
}
