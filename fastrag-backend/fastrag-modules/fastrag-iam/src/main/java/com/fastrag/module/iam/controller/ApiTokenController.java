package com.fastrag.module.iam.controller;

/**
 * API Token 管理控制器，提供平台级程序化访问的 Token 生命周期管理接口。
 *
 * <p>核心职责：
 * <ul>
 *   <li>Token 列表查询（返回值已脱敏，不暴露真实 Token）</li>
 *   <li>创建新的 API Token</li>
 *   <li>撤销（删除）指定的 API Token</li>
 * </ul>
 *
 * <p>提供的 REST API 端点：
 * <ul>
 *   <li>{@code GET  /api/api-tokens} —— 获取全部 Token 列表</li>
 *   <li>{@code POST /api/api-tokens} —— 创建 API Token</li>
 *   <li>{@code DELETE /api/api-tokens/{tokenId}} —— 撤销指定 Token</li>
 * </ul>
 *
 * <p>所有接口均要求 {@code kb:manage} 权限。创建操作会通过 {@code @Loggable}
 * 记录审计日志。Token 的实际校验逻辑由 {@code ApiTokenValidatorImpl} 完成，
 * 与 Spring Security 过滤器链集成。
 *
 * @see ApiTokenService
 * @see ApiTokenCreateRequest
 * @see ApiTokenDto
 */
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.iam.model.ApiTokenCreateRequest;
import com.fastrag.module.iam.model.ApiTokenDto;
import com.fastrag.module.iam.service.ApiTokenService;
import com.fastrag.security.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/api-tokens")
@RequiredArgsConstructor
public class ApiTokenController {

    private final ApiTokenService apiTokenService;

    /** 获取全部 Token 列表（token 值已脱敏不返回） */
    @GetMapping
    @PreAuthorize("@perm.has('kb:manage')")
    public ApiResponse<List<ApiTokenDto>> list() {
        List<ApiTokenDto> tokens = apiTokenService.listTokens();
        // 脱敏：清空 token 值
        tokens.forEach(dto -> dto.setToken(null));
        return ApiResponse.success(tokens);
    }

    /** 创建 API Token */
    @Loggable(category = LogCategory.operation, action = ActionType.token_created)
    @PostMapping
    @PreAuthorize("@perm.has('kb:manage')")
    public ApiResponse<ApiTokenDto> create(@Valid @RequestBody ApiTokenCreateRequest request) {
        String operatorId = SecurityUtil.getCurrentUserId();
        ApiTokenDto dto = apiTokenService.createToken(operatorId, request);
        return ApiResponse.success(dto);
    }

    /** 撤销 API Token */
    @Loggable(category = LogCategory.operation, action = ActionType.token_deleted, target = "#tokenId")
    @DeleteMapping("/{tokenId}")
    @PreAuthorize("@perm.has('kb:manage')")
    public ApiResponse<Void> revoke(@PathVariable String tokenId) {
        apiTokenService.revokeToken(tokenId);
        return ApiResponse.success();
    }
}
