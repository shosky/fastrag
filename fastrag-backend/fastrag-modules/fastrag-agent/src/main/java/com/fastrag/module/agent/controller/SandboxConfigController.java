package com.fastrag.module.agent.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.agent.context.CurrentUser;
import com.fastrag.module.agent.context.User;
import com.fastrag.module.agent.dto.SandboxConfigCreateDTO;
import com.fastrag.module.agent.dto.SandboxConfigUpdateDTO;
import com.fastrag.module.agent.dto.SandboxConfigVO;
import com.fastrag.module.agent.entity.SandboxConfig;
import com.fastrag.module.agent.sandbox.config.SandboxConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 沙箱配置 REST 控制器（design SandboxConfigModule 的 HTTP 薄实现细节——
 * 沿端点排除规则不立契约，业务语义见 contract agent.SandboxConfigStore）。
 *
 * <p>提供的 REST API 端点：
 * <ul>
 *   <li>POST /api/sandbox-configs —— 创建配置（校验链失败 → 41001 业务错）</li>
 *   <li>PUT /api/sandbox-configs/{id} —— 更新配置（patch 合并 + 指纹重算）</li>
 *   <li>GET /api/sandbox-configs/{id} —— 读取配置（脱敏恒开：api_key_ref 只回显引用名）</li>
 *   <li>GET /api/sandbox-configs —— 列举（普通用户回自己的，管理员回全量）</li>
 *   <li>DELETE /api/sandbox-configs/{id} —— 删除（名下有实例 → 40901 业务错）</li>
 *   <li>POST /api/sandbox-configs/{id}/verify —— 探活验证（失败 → 41002 业务错）</li>
 *   <li>GET /api/sandbox-configs/{id}/sandboxes —— 实例库存（含 paused）</li>
 * </ul></p>
 *
 * <p>权限：写操作归属者或管理员（服务层判定，越权 403 业务错）；当前用户经
 * {@code @CurrentUser} 注解注入（与 AgentController 同机制）。</p>
 */
@RestController
@RequestMapping("/api/sandbox-configs")
@RequiredArgsConstructor
public class SandboxConfigController {

    private final SandboxConfigService sandboxConfigService;

    /** 创建配置（REQ-H04）。 */
    @PostMapping
    public ApiResponse<SandboxConfigVO> create(@RequestBody @Valid SandboxConfigCreateDTO dto,
                                               @CurrentUser User user) {
        SandboxConfig config = sandboxConfigService.create(dto, user);
        return ApiResponse.success(sandboxConfigService.maskForResponse(config));
    }

    /** 更新配置（patch 合并；身份字段变更重算指纹，REQ-H09）。 */
    @PutMapping("/{id}")
    public ApiResponse<SandboxConfigVO> update(@PathVariable String id,
                                               @RequestBody @Valid SandboxConfigUpdateDTO dto,
                                               @CurrentUser User user) {
        SandboxConfig config = sandboxConfigService.update(id, dto, user);
        return ApiResponse.success(sandboxConfigService.maskForResponse(config));
    }

    /** 读取配置（脱敏恒开，REQ-H06）。 */
    @GetMapping("/{id}")
    public ApiResponse<SandboxConfigVO> get(@PathVariable String id,
                                            @CurrentUser User user) {
        return ApiResponse.success(sandboxConfigService.maskForResponse(sandboxConfigService.get(id, user)));
    }

    /** 列举配置（普通用户回自己的，管理员回全量）。 */
    @GetMapping
    public ApiResponse<List<SandboxConfigVO>> list(@CurrentUser User user) {
        List<SandboxConfigVO> voList = sandboxConfigService.listByUser(user).stream()
                .map(sandboxConfigService::maskForResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(voList);
    }

    /** 删除配置（库存 &gt;0 含 paused 拒绝，REQ-H05）。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id,
                                    @CurrentUser User user) {
        sandboxConfigService.delete(id, user);
        return ApiResponse.success();
    }

    /** 探活验证（Docker ping daemon / Cube·E2B 控制面鉴权，REQ-H05）。 */
    @PostMapping("/{id}/verify")
    public ApiResponse<SandboxConfigService.VerifyResult> verify(@PathVariable String id) {
        return ApiResponse.success(sandboxConfigService.verify(id));
    }

    /** 实例库存查询（managed 标记 + configId 过滤，含 paused/transitioning，REQ-H05）。 */
    @GetMapping("/{id}/sandboxes")
    public ApiResponse<List<SandboxConfigService.SandboxInstanceInfo>> listInstances(@PathVariable String id) {
        return ApiResponse.success(sandboxConfigService.listInstances(id));
    }
}
