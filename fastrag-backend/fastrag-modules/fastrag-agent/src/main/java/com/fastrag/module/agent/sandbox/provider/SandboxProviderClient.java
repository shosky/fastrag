package com.fastrag.module.agent.sandbox.provider;

import com.fastrag.module.agent.sandbox.SandboxHandle;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 沙箱后端协议缝（agent.SandboxProviderGateway 契约，design 缝 6）——provider 中立实例操作。
 * <p>
 * 三适配器：DockerSandboxAdapter（Engine API 直连，ADR-0006）、CubeSandboxAdapter、
 * E2BSandboxAdapter（控制面 REST + envd）。适配器内藏协议差异与错误分类映射；
 * <b>网关内不做路径策略（归执行门面 + SandboxWorkspaceLayout）、不做降级决策（归执行门面）。</b>
 * <p>
 * 约定：容器内工作区布局为 {@code /workspace}（SandboxWorkspaceLayout 单一事实源）；
 * 文件操作接口为 provider 中立形态，适配器内经 exec + archive 实现（ADR-0006：容器内零服务端）。
 */
public interface SandboxProviderClient {

    /** 后端类型标识：docker / cube / e2b */
    String provider();

    /** 能力位（终端/桌面/快照——本期恒 false 占位，二期按 capabilities 激活） */
    default ProviderCapabilities capabilities() {
        return new ProviderCapabilities(false, false, false);
    }

    /**
     * 创建实例（加固基线为 Docker 适配器 Pre 不变量：CapDrop ALL + 7 caps、no-new-privileges、
     * memory=memorySwap、Init=true、资源三上限；managed label + metadata 必带）。
     *
     * @throws SandboxProviderException INVALID_REQUEST（镜像/模板缺失等——不判 NOT_FOUND，防误重建）
     */
    SandboxHandle create(CreateContext ctx);

    /**
     * 连接既有实例：exited/created → 自动恢复为 running（REQ-H08 exited 自恢复语义）。
     *
     * @throws SandboxProviderException NOT_FOUND（实例不存在）
     */
    SandboxHandle connect(SandboxHandle handle);

    /**
     * 删除实例（停止并删除容器/沙箱；幂等——实例已不存在按成功返回）。
     */
    void delete(SandboxHandle handle);

    /**
     * 按 metadata 过滤列举实例（含 paused——库存查询与孤儿对账的依据，REQ-H05/H11）。
     * <b>endpoint 感知</b>：Docker 适配器按 hint 定位 daemon（null = 部署缺省），发现的实例登记该
     * endpoint（进程重启后由周期扫描自愈）；Cube/E2B 忽略 hint。
     *
     * @param metadata             期望的 metadata 键值（如 configId、managed 标记）；空 = 全量
     * @param states               限定状态集；空 = 全部
     * @param endpointHost         daemon/控制面地址 hint（Docker：DOCKER_HOST 形态）；null = 部署缺省
     * @param endpointTlsCertPath  TLS 证书目录 hint（远端 TCP 必填）；可空
     * @param endpointAllowPrivate 私网 endpoint 放行 hint（回环/内网 daemon 须 true）
     */
    List<SandboxHandle> list(Map<String, String> metadata, Set<String> states,
                             String endpointHost, String endpointTlsCertPath, boolean endpointAllowPrivate);

    /** 控制面/daemon 探活（REQ-H05 配置验证）。不可达抛 UNAVAILABLE 分类异常。 */
    void health(CreateContext probe);

    /**
     * 在实例内执行命令（in-container timeout 包装 + 客户端 grace，killed=true 表超时被杀）。
     */
    ExecResult exec(SandboxHandle handle, ExecRequest request);

    // ---------- 文件面（provider 中立；适配器内经 exec + archive 实现） ----------

    /** 读文件文本内容（二进制按 UTF-8 容错替换）。 */
    String readFile(SandboxHandle handle, String path);

    /** 写文件文本内容（父目录自动创建）。 */
    void writeFile(SandboxHandle handle, String path, String content);

    /** str_replace 编辑文件内容；oldString 不存在时抛 INVALID_REQUEST。 */
    void editFile(SandboxHandle handle, String path, String oldString, String newString, boolean replaceAll);

    /** 文件是否存在及类型。 */
    boolean exists(SandboxHandle handle, String path);

    /** 创建目录（含父目录；已存在按成功）。 */
    void makeDir(SandboxHandle handle, String path);

    /** 删除文件或目录（递归）。 */
    void remove(SandboxHandle handle, String path);

    /** 列目录（find 实现或 provider 等价接口）。 */
    List<DirEntry> listDir(SandboxHandle handle, String path);

    /** 读实例内活动标记的最近活跃时间（空闲回收依据；Docker = 标记文件 mtime）。 */
    Instant lastActivity(SandboxHandle handle);

    /**
     * 实例创建时固定的空闲 TTL（REQ-H02「TTL 随实例创建时的配置固定」；Docker = 容器 label）。
     * 读不到返回 null，调用方按进程内创建记录与服务缺省兜底。
     */
    default Duration idleTtl(SandboxHandle handle) {
        return null;
    }

    /** Provider 能力位。 */
    record ProviderCapabilities(boolean terminal, boolean desktop, boolean snapshot) {
    }
}
