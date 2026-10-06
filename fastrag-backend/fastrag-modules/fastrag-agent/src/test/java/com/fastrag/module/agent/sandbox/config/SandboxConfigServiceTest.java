package com.fastrag.module.agent.sandbox.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.agent.context.User;
import com.fastrag.module.agent.dto.SandboxConfigCreateDTO;
import com.fastrag.module.agent.dto.SandboxConfigUpdateDTO;
import com.fastrag.module.agent.dto.SandboxConfigVO;
import com.fastrag.module.agent.entity.SandboxConfig;
import com.fastrag.module.agent.mapper.SandboxConfigMapper;
import com.fastrag.module.agent.sandbox.SandboxHandle;
import com.fastrag.module.agent.sandbox.config.impl.SandboxConfigServiceImpl;
import com.fastrag.module.agent.sandbox.provider.CreateContext;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderClient;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderException;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderRegistry;
import com.fastrag.module.agent.sandbox.security.EndpointDialGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SandboxConfigStore 用例（docs/tdd/agent.md S10 表，contract agent.SandboxConfigStore）。
 * <p>
 * mock：SandboxConfigMapper / SandboxProviderRegistry+Client（health/list）；
 * EndpointDialGuard 用真实实例（字面量 IP 判定不触网）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SandboxConfigServiceTest {

    private static final String MISSING_ENV_REF = "FASTRAG_S10_DEFINITELY_MISSING_ENV";

    @Mock
    private SandboxConfigMapper mapper;

    @Mock
    private com.fastrag.module.agent.sandbox.SandboxBindingStore bindingStore;

    private final EndpointDialGuard dialGuard = new EndpointDialGuard();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final User owner = user("u1", "user");
    private final User other = user("u2", "user");
    private final User admin = user("admin-1", "admin");

    private static User user(String uid, String role) {
        User u = new User();
        u.setUid(uid);
        u.setRole(role);
        return u;
    }

    // ---------- 构造工具 ----------

    private SandboxConfigServiceImpl service(boolean ymlFallback) {
        return new SandboxConfigServiceImpl(mapper, new SandboxProviderRegistry(List.of()), dialGuard,
                objectMapper, bindingStore, ymlFallback);
    }

    private SandboxConfigServiceImpl serviceWith(SandboxProviderClient client, boolean ymlFallback) {
        return new SandboxConfigServiceImpl(mapper, new SandboxProviderRegistry(List.of(client)), dialGuard,
                objectMapper, bindingStore, ymlFallback);
    }

    private SandboxConfigCreateDTO dockerCreate(String host, boolean allowPrivate) {
        SandboxConfigCreateDTO dto = new SandboxConfigCreateDTO();
        dto.setName("dev-docker");
        dto.setSandboxType("docker");
        dto.setAllowPrivateEndpoints(allowPrivate);
        Map<String, Object> docker = new LinkedHashMap<>();
        docker.put("image", "fastrag-sandbox:latest");
        if (host != null) {
            docker.put("host", host);
        }
        dto.setDockerConfig(docker);
        return dto;
    }

    private SandboxConfig persistedCubeConfig(String id, String userId, String ref) throws Exception {
        SandboxConfig config = new SandboxConfig();
        config.setId(id);
        config.setUserId(userId);
        config.setName("cube-dev");
        config.setSandboxType("cube");
        config.setEnabled(true);
        config.setDefaultTimeoutSec(0);
        config.setAllowPrivateEndpoints(false);
        Map<String, Object> cube = new LinkedHashMap<>();
        // 公网字面量 IP（TEST-NET-3）：过 EndpointDialGuard 且不触 DNS，保证用例离线可跑
        cube.put("api_url", "https://203.0.113.10:8000");
        cube.put("api_key_ref", ref);
        config.setCubeConfig(objectMapper.writeValueAsString(cube));
        return config;
    }

    private Map<String, Object> parseBlock(String json) throws Exception {
        if (json == null) {
            return new LinkedHashMap<>();
        }
        return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
        });
    }

    // ---------- 1. [归属写权限] 非归属者非管理员更新时 403 语义业务错（REQ-H04） ----------

    @Test
    void 归属写权限_非归属者非管理员更新时报403语义业务错() throws Exception {
        SandboxConfig existing = persistedCubeConfig("cfg-1", "u1", "K1");
        when(mapper.selectById("cfg-1")).thenReturn(existing);

        SandboxConfigUpdateDTO patch = new SandboxConfigUpdateDTO();
        patch.setName("renamed");

        assertThatThrownBy(() -> service(false).update("cfg-1", patch, other))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(403));
        // 记录不变
        verify(mapper, never()).updateById(any(SandboxConfig.class));
    }

    // ---------- 2. [类型校验] sandbox_type=host 时 CONFIG_INVALID 拒绝落库（REQ-H04） ----------

    @Test
    void 类型校验_host类型拒绝落库() {
        SandboxConfigCreateDTO dto = dockerCreate(null, false);
        dto.setSandboxType("host"); // host 不做（2026-09-28 裁决）

        assertThatThrownBy(() -> service(false).create(dto, owner))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SandboxConfigService.E_SANDBOX_CONFIG_INVALID));
        verify(mapper, never()).insert(any(SandboxConfig.class));
    }

    // ---------- 3. [网络校验] docker 配置携带域名列表时 保存被拒（REQ-H15） ----------

    @Test
    void 网络校验_docker配置携带域名列表时保存被拒() {
        SandboxConfigCreateDTO dto = dockerCreate(null, false);
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("allow_domains", List.of("internal.example.com"));
        dto.setNetworkPolicy(policy);

        assertThatThrownBy(() -> service(false).create(dto, owner))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SandboxConfigService.E_SANDBOX_CONFIG_INVALID));
        verify(mapper, never()).insert(any(SandboxConfig.class));
    }

    // ---------- 4. [网络校验] network_mode=host 时 保存被拒（REQ-H15） ----------

    @Test
    void 网络校验_networkMode为host时保存被拒() {
        SandboxConfigCreateDTO dto = dockerCreate(null, false);
        dto.getDockerConfig().put("network_mode", "host");

        assertThatThrownBy(() -> service(false).create(dto, owner))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SandboxConfigService.E_SANDBOX_CONFIG_INVALID));
        verify(mapper, never()).insert(any(SandboxConfig.class));
    }

    // ---------- 5. [拨号校验] endpoint 为云 metadata 地址时 显式放行亦恒拒（REQ-H16） ----------

    @Test
    void 拨号校验_云metadata地址显式放行亦恒拒() {
        // allowPrivate=0 → 拒
        assertThatThrownBy(() -> service(false).create(dockerCreate("tcp://169.254.169.254:2375", false), owner))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SandboxConfigService.E_SANDBOX_CONFIG_INVALID));
        // allowPrivate=1 → 仍拒（metadata/link-local 恒拒，与本位无关）
        assertThatThrownBy(() -> service(false).create(dockerCreate("tcp://169.254.169.254:2375", true), owner))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SandboxConfigService.E_SANDBOX_CONFIG_INVALID));
        verify(mapper, never()).insert(any(SandboxConfig.class));
    }

    // ---------- 6. [拨号校验] 私网 endpoint 未放行时 拒；放行后过（REQ-H16） ----------

    @Test
    void 拨号校验_私网endpoint未放行拒放行后过() {
        // 未放行 → 拒
        assertThatThrownBy(() -> service(false).create(dockerCreate("tcp://10.0.0.5:2376", false), owner))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SandboxConfigService.E_SANDBOX_CONFIG_INVALID));
        verify(mapper, never()).insert(any(SandboxConfig.class));

        // 显式放行 → 过（落库）
        when(mapper.insert(any(SandboxConfig.class))).thenReturn(1);
        service(false).create(dockerCreate("tcp://10.0.0.5:2376", true), owner);
        ArgumentCaptor<SandboxConfig> captor = ArgumentCaptor.forClass(SandboxConfig.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getAllowPrivateEndpoints()).isTrue();
        assertThat(captor.getValue().getIdentityFingerprint()).isNotBlank();
    }

    // ---------- 7. [脱敏] 读取配置时 密钥只回显引用名不含值（REQ-H06） ----------

    @Test
    void 脱敏_读取配置时密钥只回显引用名不含值() throws Exception {
        // 引用名取必然存在的环境变量（PATH），证明服务端可解析出值而出参绝不含值
        String refName = "PATH";
        String envValue = System.getenv(refName);
        assertThat(envValue).isNotBlank();

        SandboxConfig existing = persistedCubeConfig("cfg-1", "u1", refName);
        when(mapper.selectById("cfg-1")).thenReturn(existing);

        SandboxConfigVO vo = service(false).maskForResponse(service(false).get("cfg-1", owner));

        assertThat(vo.getCubeConfig()).containsEntry("api_key_ref", refName);
        // VO 不存在 resolve 值字段：序列化后的 JSON 全文不含环境值
        String json = objectMapper.writeValueAsString(vo);
        assertThat(json).contains(refName);
        assertThat(json).doesNotContain(envValue);
    }

    // ---------- 8. [空引用不覆盖] 更新传空 api_key_ref 时 既有引用保留（REQ-H06/ADR-0007） ----------

    @Test
    void 空引用不覆盖_更新传空api_key_ref时既有引用保留() throws Exception {
        SandboxConfig existing = persistedCubeConfig("cfg-1", "u1", "K1");
        when(mapper.selectById("cfg-1")).thenReturn(existing);

        SandboxConfigUpdateDTO patch = new SandboxConfigUpdateDTO();
        Map<String, Object> cube = new LinkedHashMap<>();
        cube.put("api_key_ref", ""); // 空引用
        patch.setCubeConfig(cube);

        service(false).update("cfg-1", patch, owner);

        ArgumentCaptor<SandboxConfig> captor = ArgumentCaptor.forClass(SandboxConfig.class);
        verify(mapper).updateById(captor.capture());
        assertThat(parseBlock(captor.getValue().getCubeConfig()))
                .containsEntry("api_key_ref", "K1");
    }

    // ---------- 9. [指纹重算] 换镜像时 指纹变；改超时不变（REQ-H09） ----------

    @Test
    void 指纹重算_换镜像指纹变改超时不变() {
        when(mapper.insert(any(SandboxConfig.class))).thenReturn(1);
        service(false).create(dockerCreate(null, false), owner);
        ArgumentCaptor<SandboxConfig> insertCaptor = ArgumentCaptor.forClass(SandboxConfig.class);
        verify(mapper).insert(insertCaptor.capture());
        SandboxConfig saved = insertCaptor.getValue();
        saved.setId("cfg-1");
        String fingerprintA = saved.getIdentityFingerprint();
        when(mapper.selectById("cfg-1")).thenReturn(saved);

        // 换镜像（身份字段）→ 指纹变
        SandboxConfigUpdateDTO imagePatch = new SandboxConfigUpdateDTO();
        Map<String, Object> docker = new LinkedHashMap<>();
        docker.put("image", "fastrag-sandbox:v2");
        imagePatch.setDockerConfig(docker);
        service(false).update("cfg-1", imagePatch, owner);
        ArgumentCaptor<SandboxConfig> updateCaptor = ArgumentCaptor.forClass(SandboxConfig.class);
        verify(mapper, times(1)).updateById(updateCaptor.capture());
        String fingerprintB = updateCaptor.getValue().getIdentityFingerprint();
        assertThat(fingerprintB).isNotBlank().isNotEqualTo(fingerprintA);

        // 仅改超时（非身份字段）→ 指纹不变
        SandboxConfigUpdateDTO timeoutPatch = new SandboxConfigUpdateDTO();
        timeoutPatch.setDefaultTimeoutSec(120);
        service(false).update("cfg-1", timeoutPatch, owner);
        ArgumentCaptor<SandboxConfig> secondCaptor = ArgumentCaptor.forClass(SandboxConfig.class);
        verify(mapper, times(2)).updateById(secondCaptor.capture());
        assertThat(secondCaptor.getAllValues().get(1).getIdentityFingerprint()).isEqualTo(fingerprintB);
    }

    // ---------- 9b. [身份变更批量 stale] 指纹变更触发 markStaleByConfigId（REQ-H09 接线） ----------

    @Test
    void 身份变更时存量绑定被批量标记stale() {
        when(mapper.insert(any(SandboxConfig.class))).thenReturn(1);
        service(false).create(dockerCreate(null, false), owner);
        ArgumentCaptor<SandboxConfig> insertCaptor = ArgumentCaptor.forClass(SandboxConfig.class);
        verify(mapper).insert(insertCaptor.capture());
        SandboxConfig saved = insertCaptor.getValue();
        saved.setId("cfg-9");
        when(mapper.selectById("cfg-9")).thenReturn(saved);

        // 换镜像（身份字段）→ 指纹变更 → 批量 stale 恰好一次
        SandboxConfigUpdateDTO imagePatch = new SandboxConfigUpdateDTO();
        Map<String, Object> docker = new LinkedHashMap<>();
        docker.put("image", "fastrag-sandbox:v2");
        imagePatch.setDockerConfig(docker);
        service(false).update("cfg-9", imagePatch, owner);
        verify(bindingStore, times(1)).markStaleByConfigId("cfg-9");

        // 仅改超时（非身份字段）→ 不触发批量 stale
        SandboxConfigUpdateDTO timeoutPatch = new SandboxConfigUpdateDTO();
        timeoutPatch.setDefaultTimeoutSec(300);
        service(false).update("cfg-9", timeoutPatch, owner);
        verify(bindingStore, times(1)).markStaleByConfigId("cfg-9"); // 仍为 1 次
    }

    // ---------- 10. [验证失败] daemon 不可达时 VERIFY_FAILED 且配置原状（REQ-H05） ----------

    @Test
    void 验证失败_daemon不可达时报VERIFY_FAILED且配置原状() throws Exception {
        SandboxConfig existing = persistedCubeConfig("cfg-1", "u1", "K1");
        existing.setSandboxType("docker");
        existing.setDockerConfig(objectMapper.writeValueAsString(Map.of(
                "image", "fastrag-sandbox:latest", "host", "tcp://203.0.113.10:2376")));
        when(mapper.selectById("cfg-1")).thenReturn(existing);

        SandboxProviderClient client = mock(SandboxProviderClient.class);
        when(client.provider()).thenReturn("docker");
        doThrow(SandboxProviderException.unavailable("daemon down: connection refused"))
                .when(client).health(any(CreateContext.class));

        assertThatThrownBy(() -> serviceWith(client, false).verify("cfg-1"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(SandboxConfigService.E_SANDBOX_VERIFY_FAILED);
                    assertThat(e.getMessage()).contains("UNAVAILABLE").contains("daemon down");
                });
        // 配置保持原状：验证失败无任何写入
        verify(mapper, never()).updateById(any(SandboxConfig.class));
        verify(mapper, never()).insert(any(SandboxConfig.class));
    }

    // ---------- 11. [删除守卫] 名下有实例（含 paused）时 拒删 409（REQ-H05） ----------

    @Test
    void 删除守卫_名下有实例含paused时拒删409() throws Exception {
        SandboxConfig existing = persistedCubeConfig("cfg-1", "u1", "K1");
        existing.setSandboxType("docker");
        when(mapper.selectById("cfg-1")).thenReturn(existing);

        SandboxProviderClient client = mock(SandboxProviderClient.class);
        when(client.provider()).thenReturn("docker");
        when(client.list(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(
                SandboxHandle.builder().sandboxId("sbx-1").provider("docker")
                        .configId("cfg-1").state("paused").build()));

        assertThatThrownBy(() -> serviceWith(client, false).delete("cfg-1", owner))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SandboxConfigService.E_SANDBOX_CONFIG_IN_USE));
        verify(mapper, never()).deleteById(any(Serializable.class));

        // 库存过滤按 managed 标记 + configId 且含 paused；endpoint hint = 配置未填 daemon → null 走部署缺省
        verify(client).list(
                eq(Map.of("com.fastrag.sandbox.managed", "true", "com.fastrag.sandbox.config-id", "cfg-1")),
                eq(Set.of("running", "paused", "transitioning")),
                isNull(), isNull(), eq(false));
    }

    // ---------- 12. [凭据缺失] 引用环境变量缺失时 resolve 时报错（REQ-H06/ADR-0007） ----------

    @Test
    void 凭据缺失_引用环境变量缺失时resolve报错() throws Exception {
        SandboxConfig existing = persistedCubeConfig("cfg-1", "u1", MISSING_ENV_REF);

        assertThatThrownBy(() -> service(false).resolveCredentials(existing))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SandboxConfigService.E_SANDBOX_CREDENTIAL_MISSING));
    }

    // ---------- 13. [总开关] 三级取值 SysConfig > yml > 默认 false（REQ-H07） ----------

    @Test
    void 总开关_三级取值SysConfig大于yml大于默认false() {
        // 一级：SysConfig 有值以其为准（true 压过 yml=false）
        when(mapper.selectSysConfigValue()).thenReturn("true");
        assertThat(service(false).isGloballyEnabled()).isTrue();
        // SysConfig 显式 false 压过 yml=true
        when(mapper.selectSysConfigValue()).thenReturn("false");
        assertThat(service(true).isGloballyEnabled()).isFalse();
        // 二级：SysConfig 空 → yml 回退生效
        when(mapper.selectSysConfigValue()).thenReturn(null);
        assertThat(service(true).isGloballyEnabled()).isTrue();
        // 三级：全空 → 默认 false
        assertThat(service(false).isGloballyEnabled()).isFalse();
    }
}
