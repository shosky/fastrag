package com.fastrag.module.agent.sandbox.provider.cube;

import com.fastrag.module.agent.sandbox.SandboxHandle;
import com.fastrag.module.agent.sandbox.provider.CreateContext;
import com.fastrag.module.agent.sandbox.provider.DirEntry;
import com.fastrag.module.agent.sandbox.provider.ExecRequest;
import com.fastrag.module.agent.sandbox.provider.ExecResult;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderClient;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tencent CubeSandbox 适配器占位（agent.SandboxProviderGateway 契约，design 缝 6；ADR-0006 实装次序：Docker 先行）。
 *
 * <p>目标形态：控制面 REST（创建/删除/列表/TTL 传递，{@code cubeApiUrl} + {@code cubeApiKey}）
 * + 容器内 envd 数据面（exec 与文件操作，{@code cubeProxyUrl}/{@code cubeSandboxDomain}）；
 * 错误映射对齐契约六分类（RemoteErrorKind 同型）。全部操作在控制面接线前抛
 * {@code UNSUPPORTED}（{@link SandboxProviderRegistry#require} 的语义正是「未接线类型按
 * UNSUPPORTED 拒绝」）。</p>
 *
 * <p>TODO（实装锚，按 design §4 实装次序解锁）：</p>
 * <ul>
 *   <li>TODO-CUBE-1：控制面 REST 客户端（api key 走 EffectiveConfig.cubeApiKey 解析值，禁落库明文）；</li>
 *   <li>TODO-CUBE-2：envd 数据面 exec/文件面（provider TTL 传递 cubeTtlSec）；</li>
 *   <li>TODO-CUBE-3：networkPolicy 域名级 allow/deny（Docker 只能 bridge/none 的差异面）；</li>
 *   <li>TODO-CUBE-4：对齐 docs/tdd/agent.md S11「三后端一致性」用例集跑 fake 控制面。</li>
 * </ul>
 */
@Component
public class CubeSandboxAdapter implements SandboxProviderClient {

    @Override
    public String provider() {
        return "cube";
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(false, false, false);
    }

    @Override
    public SandboxHandle create(CreateContext ctx) {
        throw unsupported();
    }

    @Override
    public SandboxHandle connect(SandboxHandle handle) {
        throw unsupported();
    }

    @Override
    public void delete(SandboxHandle handle) {
        throw unsupported();
    }

    @Override
    public List<SandboxHandle> list(Map<String, String> metadata, Set<String> states,
            String endpointHost, String endpointTlsCertPath, boolean endpointAllowPrivate) {
        throw unsupported();
    }

    @Override
    public void health(CreateContext probe) {
        throw unsupported();
    }

    @Override
    public ExecResult exec(SandboxHandle handle, ExecRequest request) {
        throw unsupported();
    }

    @Override
    public String readFile(SandboxHandle handle, String path) {
        throw unsupported();
    }

    @Override
    public void writeFile(SandboxHandle handle, String path, String content) {
        throw unsupported();
    }

    @Override
    public void editFile(SandboxHandle handle, String path, String oldString, String newString, boolean replaceAll) {
        throw unsupported();
    }

    @Override
    public boolean exists(SandboxHandle handle, String path) {
        throw unsupported();
    }

    @Override
    public void makeDir(SandboxHandle handle, String path) {
        throw unsupported();
    }

    @Override
    public void remove(SandboxHandle handle, String path) {
        throw unsupported();
    }

    @Override
    public List<DirEntry> listDir(SandboxHandle handle, String path) {
        throw unsupported();
    }

    @Override
    public Instant lastActivity(SandboxHandle handle) {
        throw unsupported();
    }

    private static SandboxProviderException unsupported() {
        return SandboxProviderException.unsupported("cube 适配器按实装次序预留，控制面接线后启用");
    }
}
