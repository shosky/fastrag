package com.fastrag.module.agent.sandbox.provider.docker;

import com.fastrag.module.agent.sandbox.SandboxHandle;
import com.fastrag.module.agent.sandbox.SandboxWorkspaceLayout;
import com.fastrag.module.agent.sandbox.provider.CreateContext;
import com.fastrag.module.agent.sandbox.provider.DirEntry;
import com.fastrag.module.agent.sandbox.provider.ExecRequest;
import com.fastrag.module.agent.sandbox.provider.ExecResult;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderClient;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderErrorKind;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderException;
import com.fastrag.module.agent.sandbox.security.EndpointDialGuard;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.BadRequestException;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.UnauthorizedException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Docker 适配器（agent.SandboxProviderGateway 契约的 Docker 侧，design 缝 6；ADR-0006）。
 *
 * <p><b>容器内零服务端，宿主机 Engine API 直驱</b>（对齐 WeKnora docker_remote_client.go 语义）：
 * 执行 = ExecCreate/Attach/Inspect，容器内 {@code timeout(1)} 包装 + 客户端 10s grace 实现超时杀；
 * 文件面全部经 exec 实现（禁用 archive copy 端点——其以 root 运行且解析 symlink，绕过容器内权限）；
 * 活动标记折叠进 exec 包装器（空闲回收依据，REQ-H02 机制位），调用方零感知。</p>
 *
 * <p>契约硬约束：</p>
 * <ul>
 *   <li>加固基线（REQ-H14，create 的 Pre 不变量）：CapDrop ALL + 仅 7 白名单 caps、
 *       no-new-privileges、memory == memorySwap（禁 swap）、Init=true、资源三上限；</li>
 *   <li>managed 标记 + configId/userId/sessionId metadata（REQ-H11/H05，Post 不变量）；</li>
 *   <li>TCP daemon 建连前恒经 {@link EndpointDialGuard#guardDial}（REQ-H16）；</li>
 *   <li>归一错误锚例：exec 超时 = TIMEOUT；运行中实例消失 = NOT_FOUND；
 *       <b>create 时镜像缺失 = INVALID_REQUEST（不判 NOT_FOUND，防生命周期误重建）</b>。</li>
 * </ul>
 *
 * <p>网关内不做路径策略（读写根/钳制归执行门面 + SandboxWorkspaceLayout）、不做降级决策。
 * 进入容器命令的用户路径一律经 {@link #shellQuote} 单引号安全包装（对齐 WeKnora shellQuote），
 * 禁止裸拼。</p>
 */
@Slf4j
@Component
public class DockerSandboxAdapter implements SandboxProviderClient {

    static final String PROVIDER = "docker";
    static final String DEFAULT_IMAGE = "fastrag-sandbox:latest";
    static final String CONTAINER_NAME_PREFIX = "fastrag-sbx-";
    /** 与 CreateContext.managedMetadata() 一致的 managed 标记 label（孤儿对账与库存过滤唯一依据） */
    static final String LABEL_MANAGED = "com.fastrag.sandbox.managed";
    static final String LABEL_CONFIG_ID = "com.fastrag.sandbox.config-id";
    static final String LABEL_IDLE_TTL = "com.fastrag.sandbox.idle-ttl-seconds";
    /** 活动标记（REQ-H02）：每次 exec 的包装器 touch，空闲回收读 mtime（S3 起路径常量收敛到布局类，与保留路径校验同源） */
    static final String ACTIVITY_MARKER = SandboxWorkspaceLayout.ACTIVITY_MARKER;

    /**
     * 活动标记防御容差（backlog S3 / gap-review P1-7）：容器/宿主小幅时钟漂移容忍上限。
     * 标记超前超过该值即判脏数据，回退 StartedAt——否则空闲回收永不触发。
     */
    static final Duration MARKER_FUTURE_TOLERANCE = Duration.ofMinutes(2);

    /** 客户端 deadline = 超时 + 10s grace（对齐 WeKnora dockerExecGrace）：让容器内 wrapper 先杀并回报 killed */
    static final Duration EXEC_GRACE = Duration.ofSeconds(10);
    /** exec 包装器内 timeout -k 的 kill 宽限秒数 */
    static final int TIMEOUT_KILL_GRACE_SECONDS = 5;
    /** 文件面 exec 的容器内超时预算（对齐 WeKnora dockerFilesystemOpTimeout） */
    static final Duration FS_OP_TIMEOUT = Duration.ofSeconds(30);
    /** create/connect 轮询至 running 的上限 */
    static final Duration START_READY_TIMEOUT = Duration.ofSeconds(15);
    static final long START_READY_POLL_MILLIS = 100;
    /** 镜像 pull 预算（冷拉分钟级，独立于调用方 deadline） */
    static final Duration IMAGE_PULL_BUDGET = Duration.ofSeconds(300);
    static final int STOP_TIMEOUT_SECONDS = 5;
    static final Duration DEFAULT_EXEC_TIMEOUT = Duration.ofSeconds(120);
    static final String DEFAULT_DOCKER_HOST = "unix:///var/run/docker.sock";
    static final String OP_CREATE = "create";
    /** Windows 无 unix domain socket（docker-java DomainSocket 类初始化必失败），unix 缺省短路报可操作错误 */
    static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    /** CapDrop ALL 后白名单返还的 7 caps（REQ-H14，对齐 WeKnora dockerSandboxCapabilities） */
    static final Capability[] ALLOWED_CAPS = {
            Capability.CHOWN, Capability.DAC_OVERRIDE, Capability.FOWNER, Capability.FSETID,
            Capability.SETGID, Capability.SETUID, Capability.KILL
    };

    private final EndpointDialGuard dialGuard;
    /** 私网 daemon 放行兜底（配置未携带该位时的部署级缺省——per-config 值经 CreateContext 传导） */
    private final boolean allowPrivateEndpoints;

    /** endpoint(host|tls|allowPrivate) → 复用的 daemon 客户端（对齐 WeKnora 共享连接池） */
    private final ConcurrentHashMap<String, DockerClient> clientsByEndpoint = new ConcurrentHashMap<>();
    /** sandboxId → 创建/发现时的 endpoint（进程内；重启后回退缺省 daemon——TODO 登记） */
    private final ConcurrentHashMap<String, EndpointSpec> endpointBySandbox = new ConcurrentHashMap<>();

    /** endpoint 身份：两个配置指向同一 daemon 可共享客户端；allowPrivate 参与身份（改变拨号守护行为） */
    private record EndpointSpec(String host, String tlsCertPath, boolean allowPrivate) {
    }

    /** 一次 exec 的原始产出（字节缓冲在回调与复制线程交接后才读） */
    private record Captured(byte[] stdout, byte[] stderr, int exitCode) {
        String stdoutText() {
            return new String(stdout, StandardCharsets.UTF_8);
        }

        String stderrText() {
            return new String(stderr, StandardCharsets.UTF_8);
        }
    }

    public DockerSandboxAdapter(EndpointDialGuard dialGuard,
            @Value("${agent.sandbox.allow-private-endpoints:false}") boolean allowPrivateEndpoints) {
        this.dialGuard = dialGuard;
        this.allowPrivateEndpoints = allowPrivateEndpoints;
    }

    // ---------- SandboxProviderClient：实例生命周期 ----------

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(false, false, false);
    }

    @Override
    public SandboxHandle create(CreateContext ctx) {
        if (ctx == null) {
            throw SandboxProviderException.invalidRequest("create: context is required");
        }
        EndpointSpec spec = specOf(ctx.getDockerHost(), ctx.getDockerTlsCertPath(),
                ctx.getAllowPrivateEndpoints() != null ? ctx.getAllowPrivateEndpoints() : allowPrivateEndpoints);
        DockerClient client = clientFor(spec);
        String image = isBlank(ctx.getImageOrTemplate()) ? DEFAULT_IMAGE : ctx.getImageOrTemplate().trim();
        // 契约锚例：create 时镜像缺失 = INVALID_REQUEST（先 ensureImage，不判 NOT_FOUND）
        ensureImage(client, image);
        String name = containerName(ctx.getConfigId(), ctx.getSessionKey());

        SandboxHandle reused = reuseExisting(client, name, spec, ctx);
        if (reused != null) {
            return reused;
        }

        Map<String, String> labels = new LinkedHashMap<>(ctx.managedMetadata());
        labels.put(LABEL_IDLE_TTL, String.valueOf(idleTtlSeconds(ctx)));
        String id;
        try {
            id = client.createContainerCmd(image)
                    .withName(name)
                    // 长驻占位：容器是 exec 的宿，不跑任何服务（ADR-0006 容器内零服务端）
                    .withCmd("bash", "-c", "sleep infinity")
                    .withWorkingDir(SandboxWorkspaceLayout.ROOT)
                    // docker-java 3.x withEnv 对 null 做 requireNonNull（"env was not specified"）：
                    // 空环境变量必须传空集合而非 null（2026-09-30 修复，空 env 创建容器曾 100% 失败）
                    .withEnv(envSlice(ctx.getEnvVars()))
                    .withLabels(labels)
                    .withHostConfig(buildHostConfig(ctx))
                    .exec()
                    .getId();
        } catch (ConflictException raced) {
            // 同名容器在竞态窗口出现（确定性命名 + 并发 create）→ 直接复用
            SandboxHandle reused2 = reuseExisting(client, name, spec, ctx);
            if (reused2 != null) {
                return reused2;
            }
            throw wrapOp(OP_CREATE, raced);
        } catch (RuntimeException | LinkageError e) {
            throw wrapOp(OP_CREATE, e);
        }
        try {
            client.startContainerCmd(id).exec();
            waitUntilRunning(client, id, OP_CREATE);
        } catch (RuntimeException | LinkageError e) {
            removeQuietly(client, id); // 起不来的容器是无人认领的残留，立即清理
            throw wrapOp(OP_CREATE, e);
        }
        endpointBySandbox.put(id, spec);
        log.info("docker sandbox created: container={} image={} config={} session={}",
                id.substring(0, Math.min(12, id.length())), image, ctx.getConfigId(), ctx.getSessionKey());
        return SandboxHandle.builder()
                .sandboxId(id)
                .provider(PROVIDER)
                .configId(ctx.getConfigId())
                .state("running")
                .build();
    }

    @Override
    public SandboxHandle connect(SandboxHandle handle) {
        String id = requireOwnHandle(handle, "connect");
        DockerClient client = clientForSandbox(id);
        ensureRunning(client, id, "connect");
        handle.setState("running");
        handle.touch();
        return handle;
    }

    @Override
    public void delete(SandboxHandle handle) {
        String id = requireOwnHandle(handle, "delete");
        DockerClient client = clientForSandbox(id);
        try {
            client.stopContainerCmd(id).withTimeout(STOP_TIMEOUT_SECONDS).exec();
        } catch (NotFoundException absent) {
            // 已不存在：落入 remove，幂等
        } catch (RuntimeException | LinkageError e) {
            log.debug("docker sandbox delete: stop {} failed, continue with force remove: {}", id, e.toString());
        }
        try {
            client.removeContainerCmd(id).withForce(true).withRemoveVolumes(true).exec();
        } catch (NotFoundException absent) {
            // 幂等：实例已不存在按成功返回
        } catch (RuntimeException | LinkageError e) {
            throw wrapOp("delete", e);
        }
        endpointBySandbox.remove(id);
        log.info("docker sandbox deleted: container={}", id);
    }

    @Override
    public List<SandboxHandle> list(Map<String, String> metadata, Set<String> states,
                                    String endpointHost, String endpointTlsCertPath, boolean endpointAllowPrivate) {
        // endpoint 感知：hint 非空按 hint 定位 daemon（库存/回收/对账按配置逐 daemon 扫描），空 = 部署缺省
        EndpointSpec spec = isBlank(endpointHost)
                ? defaultSpec()
                : specOf(endpointHost, endpointTlsCertPath, endpointAllowPrivate);
        DockerClient client = clientFor(spec);
        Set<String> labelFilters = new LinkedHashSet<>();
        labelFilters.add(LABEL_MANAGED + "=true");
        if (metadata != null) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                if (!isBlank(entry.getKey())) {
                    labelFilters.add(entry.getKey() + "=" + (entry.getValue() == null ? "" : entry.getValue()));
                }
            }
        }
        List<Container> containers;
        try {
            containers = client.listContainersCmd()
                    .withShowAll(true)
                    .withLabelFilter(new ArrayList<>(labelFilters))
                    .exec();
        } catch (RuntimeException | LinkageError e) {
            throw wrapOp("list", e);
        }
        List<SandboxHandle> handles = new ArrayList<>(containers.size());
        for (Container item : containers) {
            String normalized = mapDockerState(item.getState());
            if (states != null && !states.isEmpty()
                    && !states.contains(normalized)
                    && !states.contains(normalizeRaw(item.getState()))) {
                continue;
            }
            Map<String, String> labels = item.getLabels() == null ? Map.of() : item.getLabels();
            String id = item.getId();
            // 发现即登记该 endpoint：进程重启后 exec/lastActivity 借此自愈（周期扫描驱动）
            endpointBySandbox.put(id, spec);
            handles.add(SandboxHandle.builder()
                    .sandboxId(id)
                    .provider(PROVIDER)
                    .configId(labels.get(LABEL_CONFIG_ID))
                    .state(normalized)
                    .build());
        }
        return handles;
    }

    @Override
    public void health(CreateContext probe) {
        boolean probeAllowPrivate = probe != null && probe.getAllowPrivateEndpoints() != null
                ? probe.getAllowPrivateEndpoints() : allowPrivateEndpoints;
        EndpointSpec spec = specOf(probe == null ? null : probe.getDockerHost(),
                probe == null ? null : probe.getDockerTlsCertPath(), probeAllowPrivate);
        DockerClient client;
        try {
            client = clientFor(spec);
        } catch (SandboxProviderException e) {
            throw e;
        } catch (RuntimeException | LinkageError e) {
            throw new SandboxProviderException(SandboxProviderErrorKind.UNAVAILABLE,
                    "docker daemon unreachable at " + spec.host() + ": " + e.getMessage(), e);
        }
        try {
            client.pingCmd().exec();
        } catch (RuntimeException | LinkageError e) {
            throw new SandboxProviderException(SandboxProviderErrorKind.UNAVAILABLE,
                    "docker daemon unreachable at " + spec.host() + ": " + e.getMessage(), e);
        }
    }

    // ---------- SandboxProviderClient：执行 ----------

    @Override
    public ExecResult exec(SandboxHandle handle, ExecRequest request) {
        String id = requireOwnHandle(handle, "exec");
        if (request == null || isBlank(request.getCommand())) {
            throw SandboxProviderException.invalidRequest("exec: command is required");
        }
        DockerClient client = clientForSandbox(id);
        Duration timeout = request.getTimeout() == null || request.getTimeout().isZero()
                || request.getTimeout().isNegative()
                ? DEFAULT_EXEC_TIMEOUT
                : request.getTimeout();
        long seconds = Math.max(1, ceilSeconds(timeout));
        String wrapper = execWrapper(request.getCommand(), seconds, ACTIVITY_MARKER);
        String workDir = isBlank(request.getWorkDir()) ? SandboxWorkspaceLayout.ROOT : request.getWorkDir().trim();
        List<String> env = envSlice(request.getEnv());
        byte[] stdin = request.getStdin() == null ? null : request.getStdin().getBytes(StandardCharsets.UTF_8);
        Captured captured;
        try {
            captured = execInContainer(client, id, execCmdArray(wrapper), workDir, env, stdin,
                    timeout.plus(EXEC_GRACE), "exec");
        } catch (RuntimeException | LinkageError e) {
            throw wrapOp("exec", e);
        }
        int exitCode = captured.exitCode();
        return ExecResult.builder()
                .stdout(captured.stdoutText())
                .stderr(captured.stderrText())
                .exitCode(exitCode)
                .killed(execWasKilled(exitCode))
                .build();
    }

    // ---------- SandboxProviderClient：文件面（全部经 exec，禁用 archive copy 端点） ----------

    @Override
    public String readFile(SandboxHandle handle, String path) {
        String id = requireOwnHandle(handle, "readFile");
        String clean = requirePath(path, "readFile");
        DockerClient client = clientForSandbox(id);
        if (!pathExists(client, id, clean, "readFile")) {
            throw SandboxProviderException.notFound("readFile: path does not exist: " + clean);
        }
        Captured captured;
        try {
            captured = execFs(client, id, commandReadFile(clean), "readFile");
        } catch (RuntimeException | LinkageError e) {
            throw wrapOp("readFile", e);
        }
        if (captured.exitCode() != 0) {
            throw fileOpError("readFile", clean, captured.stderrText());
        }
        try {
            byte[] decoded = Base64.getMimeDecoder().decode(captured.stdoutText().trim());
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw SandboxProviderException.invalidRequest("readFile: container returned invalid base64 for " + clean);
        }
    }

    @Override
    public void writeFile(SandboxHandle handle, String path, String content) {
        String id = requireOwnHandle(handle, "writeFile");
        String clean = requirePath(path, "writeFile");
        DockerClient client = clientForSandbox(id);
        // 2026-09-30 修复：原实现走 exec + stdin（base64 -d 管道灌内容），httpclient5 传输下 stdin
        // 从未送达容器侧，命令干等至 FS_OP_TIMEOUT 被杀（exit 124、stderr 空），空 env 修复后首次
        // 真机写入即暴露。改走 copyArchiveToContainer 内存 tar——Docker 标准通道，无 stdin 依赖。
        try {
            // QF-7：mkdir 复合 touch 活动标记——tar 写入本身不刷新活动，前置 exec 一次性补齐
            execFs(client, id, commandWriteFilePrepare(parentDir(clean), ACTIVITY_MARKER), "writeFile");
        } catch (RuntimeException | LinkageError e) {
            throw wrapOp("writeFile", e);
        }
        try {
            client.copyArchiveToContainerCmd(id)
                    .withTarInputStream(new ByteArrayInputStream(tarBytes(baseName(clean), content)))
                    .withRemotePath(parentDir(clean))
                    .exec();
        } catch (RuntimeException | LinkageError e) {
            throw wrapOp("writeFile", e);
        }
    }

    @Override
    public void editFile(SandboxHandle handle, String path, String oldString, String newString, boolean replaceAll) {
        String clean = requirePath(path, "editFile");
        if (oldString == null) {
            throw SandboxProviderException.invalidRequest("editFile: oldString is required");
        }
        String content = readFile(handle, clean);
        if (!content.contains(oldString)) {
            throw SandboxProviderException.invalidRequest("editFile: oldString not found in " + clean);
        }
        String replacement = newString == null ? "" : newString;
        String updated = replaceAll
                ? content.replace(oldString, replacement)
                : content.replaceFirst(Pattern.quote(oldString), Matcher.quoteReplacement(replacement));
        writeFile(handle, clean, updated);
    }

    @Override
    public boolean exists(SandboxHandle handle, String path) {
        String id = requireOwnHandle(handle, "exists");
        String clean = requirePath(path, "exists");
        DockerClient client = clientForSandbox(id);
        try {
            return pathExists(client, id, clean, "exists");
        } catch (RuntimeException | LinkageError e) {
            throw wrapOp("exists", e);
        }
    }

    @Override
    public void makeDir(SandboxHandle handle, String path) {
        String id = requireOwnHandle(handle, "makeDir");
        String clean = requirePath(path, "makeDir");
        DockerClient client = clientForSandbox(id);
        Captured captured;
        try {
            captured = execFs(client, id, commandMakeDir(clean), "makeDir");
        } catch (RuntimeException | LinkageError e) {
            throw wrapOp("makeDir", e);
        }
        if (captured.exitCode() != 0) {
            throw SandboxProviderException.invalidRequest(
                    "makeDir " + clean + ": " + firstNonEmptyLine(captured.stderrText()));
        }
    }

    @Override
    public void remove(SandboxHandle handle, String path) {
        String id = requireOwnHandle(handle, "remove");
        String clean = requirePath(path, "remove");
        if ("/".equals(clean)) {
            throw SandboxProviderException.invalidRequest("remove: refusing to remove the container root");
        }
        DockerClient client = clientForSandbox(id);
        Captured captured;
        try {
            captured = execFs(client, id, commandRemove(clean), "remove");
        } catch (RuntimeException | LinkageError e) {
            throw wrapOp("remove", e);
        }
        if (captured.exitCode() != 0) {
            throw SandboxProviderException.invalidRequest(
                    "remove " + clean + ": " + firstNonEmptyLine(captured.stderrText()));
        }
    }

    @Override
    public List<DirEntry> listDir(SandboxHandle handle, String path) {
        String id = requireOwnHandle(handle, "listDir");
        String clean = requirePath(path, "listDir");
        DockerClient client = clientForSandbox(id);
        Captured captured;
        try {
            captured = execFs(client, id, commandListDir(clean), "listDir");
        } catch (RuntimeException | LinkageError e) {
            throw wrapOp("listDir", e);
        }
        if (captured.exitCode() != 0) {
            String stderr = captured.stderrText();
            if (stderr.contains("No such file or directory")) {
                throw SandboxProviderException.notFound("listDir: path does not exist: " + clean);
            }
            throw SandboxProviderException.invalidRequest(
                    "listDir " + clean + ": " + firstNonEmptyLine(stderr));
        }
        return parseFindOutput(clean, captured.stdoutText());
    }

    @Override
    public Instant lastActivity(SandboxHandle handle) {
        String id = requireOwnHandle(handle, "lastActivity");
        DockerClient client = clientForSandbox(id);
        // 走裸 exec（无活动标记 touch、无 timeout 包装）读 mtime：若经包装器，标记会被本次调用刷新，
        // 空闲回收将永远看不到陈旧会话。对齐 WeKnora：标记读取不经 touch 通道。
        try {
            Captured captured = runExecRaw(client, id,
                    new String[]{"stat", "-c", "%Y", ACTIVITY_MARKER},
                    SandboxWorkspaceLayout.ROOT, null, null, Duration.ofSeconds(15), "lastActivity");
            if (captured.exitCode() == 0) {
                Instant marker = parseEpochSeconds(captured.stdoutText());
                Instant safeMarker = defensiveMarker(marker, Instant.now());
                if (safeMarker != null) {
                    return safeMarker;
                }
                // 标记判脏（未来时间超容差）→ 落入 StartedAt 回退
            }
        } catch (NotFoundException gone) {
            throw wrapOp("lastActivity", gone); // 实例消失 = NOT_FOUND（契约锚例）
        } catch (RuntimeException | LinkageError e) {
            log.debug("docker sandbox lastActivity: marker stat failed on {}, fall back to StartedAt: {}",
                    id, e.toString());
        }
        InspectContainerResponse inspected = inspectOrThrow(client, id, "lastActivity");
        return parseRfc3339(inspected.getState() == null ? null : inspected.getState().getStartedAt());
    }

    @Override
    public Duration idleTtl(SandboxHandle handle) {
        // TTL 固定于创建时（REQ-H02）：读容器 label（create 时写入），label 缺失/实例消失返回 null
        try {
            String id = requireOwnHandle(handle, "idleTtl");
            DockerClient client = clientForSandbox(id);
            InspectContainerResponse inspected = inspectOrThrow(client, id, "idleTtl");
            Map<String, String> labels = inspected.getConfig() == null
                    ? null : inspected.getConfig().getLabels();
            String ttl = labels == null ? null : labels.get(LABEL_IDLE_TTL);
            return ttl == null ? null : Duration.ofSeconds(Long.parseLong(ttl.trim()));
        } catch (Exception e) {
            log.debug("docker sandbox idleTtl: label unavailable for {}: {}",
                    handle == null ? null : handle.getSandboxId(), e.toString());
            return null;
        }
    }

    // ---------- 客户端构建与缓存 ----------

    /** exists 探测（test -e → Y/N）；容器消失抛 notFound */
    private boolean pathExists(DockerClient client, String id, String path, String op) {
        Captured captured = execFs(client, id, commandExists(path), op);
        if (captured.exitCode() != 0) {
            throw fileOpError(op, path, captured.stderrText());
        }
        return "Y".equals(captured.stdoutText().trim());
    }

    /** 文件面 exec：容器内 FS_OP_TIMEOUT 包装 + 活动标记 touch + 客户端 grace（文件操作也算活动） */
    private Captured execFs(DockerClient client, String id, String command, String op) {
        return execInContainer(client, id,
                execCmdArray(execWrapper(command, ceilSeconds(FS_OP_TIMEOUT), ACTIVITY_MARKER)),
                SandboxWorkspaceLayout.ROOT, null, null, FS_OP_TIMEOUT.plus(EXEC_GRACE), op);
    }

    private Captured execInContainer(DockerClient client, String id, String[] cmdArray, String workDir,
            List<String> env, byte[] stdin, Duration clientBudget, String op) {
        try {
            return runExecRaw(client, id, cmdArray, workDir, env, stdin, clientBudget, op);
        } catch (ConflictException notRunning) {
            // ExecCreate 409「is not running」→ 恢复 running 后重试一次（exited 自恢复，REQ-H08）
            if (!containerNotRunning(notRunning)) {
                throw notRunning;
            }
            ensureRunning(client, id, op);
            return runExecRaw(client, id, cmdArray, workDir, env, stdin, clientBudget, op);
        }
    }

    /**
     * 裸 exec 原语：ExecCreate → ExecStart（stdin 灌入 + frame 分离收集）→ ExecInspect。
     * 抛出未分类的 docker-java 异常，由调用方 wrapOp 归一；deadline 触发直接抛 TIMEOUT。
     */
    private Captured runExecRaw(DockerClient client, String id, String[] cmdArray, String workDir,
            List<String> env, byte[] stdin, Duration clientBudget, String op) {
        var created = client.execCreateCmd(id)
                .withCmd(cmdArray)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withAttachStdin(stdin != null)
                .withWorkingDir(workDir)
                .withEnv(env)
                .exec();
        long deadlineNanos = System.nanoTime() + clientBudget.toNanos();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ExecCapture callback = new ExecCapture(out, err);
        if (stdin != null) {
            // httpclient5 传输下 exec+stdin 已知不送达容器侧（命令干等至守卫超时杀，2026-09-30 真机实锤），
            // 当前无调用方传 stdin——文件写入已改走 copyArchiveToContainer；新增数据通道勿依赖此分支
            client.execStartCmd(created.getId()).withStdIn(new ByteArrayInputStream(stdin)).exec(callback);
        } else {
            client.execStartCmd(created.getId()).exec(callback);
        }
        boolean finished;
        try {
            long remaining = Math.max(1, deadlineNanos - System.nanoTime());
            finished = callback.awaitCompletion(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            closeQuietly(callback);
            throw new SandboxProviderException(SandboxProviderErrorKind.UNAVAILABLE,
                    op + ": interrupted while awaiting exec output", ie);
        }
        if (!finished) {
            closeQuietly(callback);
            throw SandboxProviderException.timeout(op + ": client deadline exceeded (" + clientBudget.toSeconds()
                    + "s budget incl. grace), cmd=" + firstNonEmptyLine(String.join(" ", cmdArray)));
        }
        if (callback.error() != null) {
            throw toRuntime(callback.error());
        }
        Integer exitCode = client.inspectExecCmd(created.getId()).exec().getExitCode();
        return new Captured(out.toByteArray(), err.toByteArray(), exitCode == null ? -1 : exitCode);
    }

    /**
     * 帧收集回调：ExecStartResultCallback(onNext) 把 STDOUT/RAW 写第一个流、STDERR 写第二个流；
     * onError 记录原始错误供分类。deadline 触发时 close 释放 hijack 连接。
     */
    private static final class ExecCapture extends ExecStartResultCallback {
        private volatile Throwable error;

        ExecCapture(ByteArrayOutputStream out, ByteArrayOutputStream err) {
            super(out, err);
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
            super.onError(throwable);
        }

        Throwable error() {
            return error;
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            log.debug("close exec stream failed: {}", e.toString());
        }
    }

    private static RuntimeException toRuntime(Throwable t) {
        return t instanceof RuntimeException re ? re : new RuntimeException(t.getMessage(), t);
    }

    // ---------- 状态机：exited 自恢复 / 轮询至 running ----------

    private void ensureRunning(DockerClient client, String id, String op) {
        InspectContainerResponse inspected = inspectOrThrow(client, id, op);
        resumeInspected(client, inspected, op);
        waitUntilRunning(client, id, op);
    }

    private void resumeInspected(DockerClient client, InspectContainerResponse inspected, String op) {
        String status = statusOf(inspected);
        switch (status) {
            case "running" -> {
                // already there
            }
            case "dead" -> throw SandboxProviderException.notFound(op + ": docker container is dead: "
                    + inspected.getId());
            case "paused" -> {
                try {
                    client.unpauseContainerCmd(inspected.getId()).exec();
                } catch (RuntimeException | LinkageError e) {
                    throw wrapOp(op, e);
                }
            }
            case "exited", "created" -> {
                try {
                    client.startContainerCmd(inspected.getId()).exec();
                } catch (RuntimeException | LinkageError e) {
                    throw wrapOp(op, e);
                }
            }
            default -> {
                // restarting/removing 等过渡态交给 waitUntilRunning 轮询
            }
        }
    }

    private void waitUntilRunning(DockerClient client, String id, String op) {
        long deadline = System.nanoTime() + START_READY_TIMEOUT.toNanos();
        String lastStatus = "";
        while (true) {
            InspectContainerResponse inspected = inspectOrThrow(client, id, op);
            lastStatus = statusOf(inspected);
            if ("running".equals(lastStatus)) {
                return;
            }
            if ("dead".equals(lastStatus)) {
                throw SandboxProviderException.notFound(op + ": docker container is dead: " + id);
            }
            if (System.nanoTime() - deadline >= 0) {
                throw SandboxProviderException.unavailable(op + ": docker container did not reach running"
                        + " (last state \"" + lastStatus + "\") within " + START_READY_TIMEOUT.toSeconds() + "s");
            }
            try {
                Thread.sleep(START_READY_POLL_MILLIS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new SandboxProviderException(SandboxProviderErrorKind.UNAVAILABLE,
                        op + ": interrupted while waiting for running", ie);
            }
        }
    }

    private InspectContainerResponse inspectOrThrow(DockerClient client, String id, String op) {
        try {
            return client.inspectContainerCmd(id).exec();
        } catch (NotFoundException absent) {
            throw SandboxProviderException.notFound(op + ": docker container does not exist: " + id);
        } catch (RuntimeException | LinkageError e) {
            throw wrapOp(op, e);
        }
    }

    private void removeQuietly(DockerClient client, String id) {
        try {
            client.removeContainerCmd(id).withForce(true).withRemoveVolumes(true).exec();
        } catch (RuntimeException | LinkageError e) {
            log.debug("docker sandbox cleanup: remove {} failed: {}", id, e.toString());
        }
    }

    // ---------- create 辅助 ----------

    private SandboxHandle reuseExisting(DockerClient client, String name, EndpointSpec spec, CreateContext ctx) {
        InspectContainerResponse inspected;
        try {
            inspected = client.inspectContainerCmd(name).exec();
        } catch (NotFoundException absent) {
            return null; // 无同名容器 → 正常新建
        } catch (RuntimeException | LinkageError e) {
            throw wrapOp(OP_CREATE, e);
        }
        if ("dead".equals(statusOf(inspected))) {
            // dead 不可恢复：清掉重建（确定性命名被占用时唯一出路）
            removeQuietly(client, inspected.getId());
            return null;
        }
        // 已存在同名容器 → 直接 connect 复用（重连幂等）
        ensureRunning(client, inspected.getId(), OP_CREATE);
        endpointBySandbox.putIfAbsent(inspected.getId(), spec);
        return SandboxHandle.builder()
                .sandboxId(inspected.getId())
                .provider(PROVIDER)
                .configId(ctx.getConfigId())
                .state("running")
                .build();
    }

    private void ensureImage(DockerClient client, String image) {
        try {
            client.inspectImageCmd(image).exec();
            return; // 本地已有
        } catch (NotFoundException missing) {
            // 本地缺失 → pull
        } catch (RuntimeException | LinkageError e) {
            // daemon 不可达/传输层失败 = UNAVAILABLE（不与镜像缺失混淆）
            throw wrapOp(OP_CREATE, e);
        }
        String[] repoTag = repoTag(image);
        try {
            PullImageCmd pull = client.pullImageCmd(repoTag[0]).withTag(repoTag[1]);
            PullImageResultCallback callback = pull.exec(new PullImageResultCallback());
            boolean done = callback.awaitCompletion(IMAGE_PULL_BUDGET.toMillis(), TimeUnit.MILLISECONDS);
            if (!done) {
                throw SandboxProviderException.invalidRequest(
                        OP_CREATE + ": image pull did not finish within " + IMAGE_PULL_BUDGET.toSeconds() + "s: " + image);
            }
        } catch (SandboxProviderException budgetExceeded) {
            throw budgetExceeded;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw SandboxProviderException.invalidRequest(OP_CREATE + ": interrupted while pulling image " + image);
        } catch (RuntimeException | LinkageError e) {
            // 锚例：镜像拉取失败 = 镜像在该 daemon 上不可得 = INVALID_REQUEST（不判 NOT_FOUND）
            throw SandboxProviderException.invalidRequest(OP_CREATE + ": image '" + image
                    + "' is missing on the daemon and pull failed: " + e.getMessage());
        }
        try {
            client.inspectImageCmd(image).exec();
        } catch (RuntimeException | LinkageError e) {
            throw SandboxProviderException.invalidRequest(OP_CREATE + ": image '" + image
                    + "' is still missing after pull: " + e.getMessage());
        }
    }

    /** REQ-H14 加固基线（create 的 Pre 不变量，可纯函数单测） */
    static HostConfig buildHostConfig(CreateContext ctx) {
        long memory = ctx.getMemoryBytes() > 0 ? ctx.getMemoryBytes() : 2L * 1024 * 1024 * 1024;
        double cpu = ctx.getCpuLimit() > 0 ? ctx.getCpuLimit() : 2.0;
        long pids = ctx.getPidsLimit() > 0 ? ctx.getPidsLimit() : 512;
        String networkMode = isBlank(ctx.getNetworkMode()) ? "bridge" : ctx.getNetworkMode();
        return new HostConfig()
                .withCapDrop(Capability.ALL)
                .withCapAdd(ALLOWED_CAPS)
                .withSecurityOpts(List.of("no-new-privileges"))
                // memory == memorySwap → 禁 swap，失控分配被杀而非拖垮宿主机磁盘
                .withMemory(memory)
                .withMemorySwap(memory)
                .withNanoCPUs((long) (cpu * 1e9))
                .withPidsLimit(pids)
                .withNetworkMode(networkMode)
                .withInit(true); // sleep 不 wait()，无 tini 会积僵尸直到 PidsLimit 耗尽
    }

    private static long idleTtlSeconds(CreateContext ctx) {
        return ctx.getIdleTtl() == null || ctx.getIdleTtl().isZero() || ctx.getIdleTtl().isNegative()
                ? 1800
                : ctx.getIdleTtl().toSeconds();
    }

    // ---------- 客户端按 endpoint 缓存复用 ----------

    private DockerClient clientForSandbox(String sandboxId) {
        EndpointSpec spec = endpointBySandbox.get(sandboxId);
        if (spec == null) {
            spec = defaultSpec();
            endpointBySandbox.putIfAbsent(sandboxId, spec);
        }
        return clientFor(spec);
    }

    private DockerClient clientForDefault() {
        return clientFor(defaultSpec());
    }

    private EndpointSpec defaultSpec() {
        return new EndpointSpec(resolveDockerHost(null), envTlsCertPath(), allowPrivateEndpoints);
    }

    private EndpointSpec specOf(String explicitHost, String explicitTlsCertPath) {
        return specOf(explicitHost, explicitTlsCertPath, allowPrivateEndpoints);
    }

    private EndpointSpec specOf(String explicitHost, String explicitTlsCertPath, boolean allowPrivate) {
        String host = resolveDockerHost(explicitHost);
        String tls = isBlank(explicitTlsCertPath) ? (isBlank(explicitHost) ? envTlsCertPath() : null) : explicitTlsCertPath.trim();
        return new EndpointSpec(host, tls, allowPrivate);
    }

    /** 缺省探测：显式值 → DOCKER_HOST 环境变量 → unix:///var/run/docker.sock */
    static String resolveDockerHost(String explicit) {
        if (!isBlank(explicit)) {
            return explicit.trim();
        }
        String env = System.getenv("DOCKER_HOST");
        if (!isBlank(env)) {
            return env.trim();
        }
        return DEFAULT_DOCKER_HOST;
    }

    private static String envTlsCertPath() {
        String env = System.getenv("DOCKER_CERT_PATH");
        if (!isBlank(env) && Files.isDirectory(Paths.get(env.trim()))) {
            return env.trim();
        }
        return null;
    }

    private DockerClient clientFor(EndpointSpec spec) {
        String key = spec.host() + "|" + (spec.tlsCertPath() == null ? "" : spec.tlsCertPath())
                + "|" + spec.allowPrivate();
        return clientsByEndpoint.computeIfAbsent(key, k -> buildClient(spec));
    }

    private DockerClient buildClient(EndpointSpec spec) {
        String host = spec.host();
        // Windows 无 unix domain socket：docker-java 的 DomainSocket 类初始化必失败（NoClassDefFoundError）。
        // 短路给出可操作指引，而非让用户面对晦涩的「Could not initialize class」。
        if (host.startsWith("unix://") && IS_WINDOWS) {
            throw SandboxProviderException.unavailable(
                    "Windows 下无法使用 unix socket daemon（" + host + "）。"
                            + "请：① Docker Desktop → Settings → General 勾选「Expose daemon on tcp://localhost:2375 without TLS」并 Apply & Restart；"
                            + "② 沙箱配置的 Daemon 地址填 tcp://localhost:2375；③ 打开「私网 endpoint」开关（localhost 为回环地址）。");
        }
        // REQ-H16：TCP daemon 是租户可指定 endpoint，建连前对解析 IP 复查（hostname 可先验公网后变私网）
        if (needsDialGuard(host)) {
            URI uri = URI.create(host);
            int port = uri.getPort() > 0 ? uri.getPort() : (spec.tlsCertPath() == null ? 2375 : 2376);
            try {
                dialGuard.guardDial(uri.getHost(), port, spec.allowPrivate());
            } catch (RuntimeException | LinkageError e) {
                throw SandboxProviderException.invalidRequest(
                        "docker endpoint refused by dial guard: " + host + " (" + e.getMessage() + ")");
            }
        }
        DefaultDockerClientConfig.Builder configBuilder = new DefaultDockerClientConfig.Builder()
                .withDockerHost(host);
        if (spec.tlsCertPath() != null && Files.isDirectory(Paths.get(spec.tlsCertPath()))) {
            configBuilder.withDockerTlsVerify(true).withDockerCertPath(spec.tlsCertPath());
        }
        DefaultDockerClientConfig config = configBuilder.build();
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(URI.create(host))
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

    /** unix socket 是进程本地的，无地址可查；tcp/http/https 恒需拨号守护（对齐 WeKnora dockerHostNeedsDialGuard） */
    static boolean needsDialGuard(String host) {
        String trimmed = host == null ? "" : host.trim().toLowerCase();
        int schemeEnd = trimmed.indexOf("://");
        if (schemeEnd < 0) {
            return false;
        }
        return switch (trimmed.substring(0, schemeEnd)) {
            case "tcp", "http", "https" -> true;
            default -> false;
        };
    }

    // ---------- 纯函数：命令组装 / 解析 / 分类（包内可见，供单测） ----------

    /** 单引号安全包装（对齐 WeKnora shellQuote）：单引号内的单引号写成 '\'' */
    static String shellQuote(String raw) {
        return "'" + (raw == null ? "" : raw).replace("'", "'\\''") + "'";
    }

    /** 确定性容器名（重连幂等）：fastrag-sbx- + sha256(configId|sessionKey) 前 12 位 */
    static String containerName(String configId, String sessionKey) {
        String material = (configId == null ? "" : configId) + "|" + (sessionKey == null ? "" : sessionKey);
        return CONTAINER_NAME_PREFIX + sha256Hex(material).substring(0, 12);
    }

    static String sha256Hex(String material) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * exec 包装脚本（活动标记 + 容器内超时杀，对齐 WeKnora dockerExecCommand 语义）：
     * {@code touch <marker>; timeout -k 5 <sec> bash -c '<command>'}。
     * 客户端 deadline = 超时 + grace，让 wrapper 先杀并回报 killed=true。
     */
    static String execWrapper(String command, long timeoutSeconds, String markerPath) {
        long seconds = Math.max(1, timeoutSeconds);
        return "touch " + markerPath + "; timeout -k " + TIMEOUT_KILL_GRACE_SECONDS + " " + seconds
                + " bash -c " + shellQuote(command);
    }

    /** 包装脚本作为 bash -c 的单个参数传入 */
    static String[] execCmdArray(String wrapper) {
        return new String[]{"bash", "-c", wrapper};
    }

    static String commandReadFile(String path) {
        return "base64 -w0 " + shellQuote(path);
    }

    static String commandExists(String path) {
        return "test -e " + shellQuote(path) + " && echo Y || echo N";
    }

    static String commandMakeDir(String path) {
        return "mkdir -p " + shellQuote(path);
    }

    /**
     * writeFile 前置命令（QF-7）：mkdir -p 父目录 + touch 活动标记复合成单条 exec。
     * tar 通道（copyArchiveToContainer）绕过 execWrapper 的 touch 前缀，活动刷新借
     * 必经的 makeDir 阶段补回——零额外往返，与 lastActivity 的空闲回收语义（REQ-H02）保持一致。
     */
    static String commandWriteFilePrepare(String dir, String markerPath) {
        return commandMakeDir(dir) + "; touch " + markerPath;
    }

    static String commandRemove(String path) {
        return "rm -rf " + shellQuote(path);
    }

    /** 对齐 WeKnora ListDir：find -printf 单格式产出类型/大小/名字，tab 分隔 */
    static String commandListDir(String dir) {
        return "find " + shellQuote(dir) + " -maxdepth 1 -mindepth 1 -printf '%y\\t%s\\t%f\\n'";
    }

    /** Java 侧 dirname（路径已 trim）："/a/b.txt"→"/a"；"/x"→"/"；"rel.txt"→"." */
    static String parentDir(String path) {
        int idx = path.lastIndexOf('/');
        if (idx < 0) {
            return ".";
        }
        if (idx == 0) {
            return "/";
        }
        return path.substring(0, idx);
    }

    /** 末段文件名（tar entry 用；容忍尾斜杠） */
    static String baseName(String path) {
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int idx = p.lastIndexOf('/');
        return idx < 0 ? p : p.substring(idx + 1);
    }

    /**
     * 单文件内存 tar（entry 为纯文件名，配合 withRemotePath=父目录解包覆盖写）。
     * 权限 0644 对齐原 base64 重定向产物。
     */
    static byte[] tarBytes(String entryName, String content) {
        byte[] data = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(bos, StandardCharsets.UTF_8.name())) {
            TarArchiveEntry entry = new TarArchiveEntry(entryName);
            entry.setSize(data.length);
            entry.setMode(TarArchiveEntry.DEFAULT_FILE_MODE);
            tar.putArchiveEntry(entry);
            tar.write(data);
            tar.closeArchiveEntry();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("tar build failed: " + e.getMessage(), e);
        }
        return bos.toByteArray();
    }

    /**
     * 解析 {@code find -printf '%y\t%s\t%f\n'} 输出（对齐 WeKnora parseDockerFindOutput 语义）：
     * 跳过残行而非整体失败——单个不可读条目不得隐藏目录其余部分。y∈{f,d} 之外的类型按非目录保留。
     */
    static List<DirEntry> parseFindOutput(String dir, String output) {
        List<DirEntry> entries = new ArrayList<>();
        if (output == null || output.isEmpty()) {
            return entries;
        }
        for (String line : output.split("\n", -1)) {
            String trimmed = trimTrailingCr(line);
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] fields = trimmed.split("\t", 3);
            if (fields.length < 3 || fields[2].isEmpty()) {
                continue;
            }
            boolean directory = "d".equals(fields[0]);
            long size = -1;
            if (!directory) {
                try {
                    size = Long.parseLong(fields[1]);
                } catch (NumberFormatException ignored) {
                    // 保留 -1（未知大小），不丢弃条目
                }
            }
            String name = fields[2];
            entries.add(DirEntry.builder()
                    .name(name)
                    .path(joinPath(dir, name))
                    .directory(directory)
                    .size(size)
                    .build());
        }
        return entries;
    }

    private static String trimTrailingCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private static String joinPath(String dir, String name) {
        if (dir.length() > 1 && dir.endsWith("/")) {
            return dir + name;
        }
        return dir + "/" + name;
    }

    /** docker 状态 → provider 中立状态（对齐 WeKnora dockerStateOf；exited 有文件系统、可重启，非 terminal） */
    static String mapDockerState(String rawState) {
        return switch (normalizeRaw(rawState)) {
            case "running" -> "running";
            case "paused", "exited", "created" -> "paused";
            case "restarting", "removing" -> "transitioning";
            case "dead" -> "terminal";
            default -> "unknown";
        };
    }

    private static String normalizeRaw(String rawState) {
        return rawState == null ? "" : rawState.trim().toLowerCase();
    }

    private static String statusOf(InspectContainerResponse inspected) {
        if (inspected.getState() == null || inspected.getState().getStatus() == null) {
            return "";
        }
        return inspected.getState().getStatus().trim().toLowerCase();
    }

    /** exit 137 = SIGKILL（timeout -s KILL / -k），124 = timeout(1) 介入（对齐 WeKnora dockerExecWasKilled） */
    static boolean execWasKilled(int exitCode) {
        return exitCode == 137 || exitCode == 124;
    }

    /**
     * 归一错误分类（契约六分类锚例）：NotFoundException 按 op 区分——create 场景镜像缺失 =
     * INVALID_REQUEST（不判 NOT_FOUND，防生命周期误重建），其余 op = NOT_FOUND。
     * 兼收 LinkageError（Windows 本地开发对 unix socket 缺原生支持的传输层失败）→ UNAVAILABLE。
     */
    static SandboxProviderErrorKind classifyError(String op, Throwable e) {
        if (e instanceof NotFoundException) {
            return OP_CREATE.equals(op)
                    ? SandboxProviderErrorKind.INVALID_REQUEST
                    : SandboxProviderErrorKind.NOT_FOUND;
        }
        if (e instanceof UnauthorizedException) {
            return SandboxProviderErrorKind.AUTH;
        }
        if (e instanceof BadRequestException || e instanceof ConflictException) {
            return SandboxProviderErrorKind.INVALID_REQUEST;
        }
        if (isTimeoutSignal(e)) {
            return SandboxProviderErrorKind.TIMEOUT;
        }
        return SandboxProviderErrorKind.UNAVAILABLE;
    }

    static boolean isTimeoutSignal(Throwable e) {
        if (e.getClass().getSimpleName().contains("Timeout")) {
            return true;
        }
        String message = e.getMessage();
        return message != null && (message.contains("Read timed out")
                || message.contains("Connect timed out")
                || message.contains("deadline exceeded"));
    }

    /** 已归一异常原样透传；其余按 op 分类并附原始原因 */
    static SandboxProviderException wrapOp(String op, Throwable e) {
        if (e instanceof SandboxProviderException normalized) {
            return normalized;
        }
        return new SandboxProviderException(classifyError(op, e), op + " failed: " + e.getMessage(), e);
    }

    /** 文件面失败分类（对齐 WeKnora dockerFileOpError）：路径缺失 = NOT_FOUND，其余 = INVALID_REQUEST */
    static SandboxProviderException fileOpError(String op, String cleanPath, String stderr) {
        if (stderr != null && stderr.contains("No such file or directory")) {
            return SandboxProviderException.notFound(op + ": path does not exist: " + cleanPath);
        }
        return SandboxProviderException.invalidRequest(op + " " + cleanPath + ": " + firstNonEmptyLine(stderr));
    }

    /** "name:tag" / "host:port/name:tag" / "name" → [repository, tag] */
    static String[] repoTag(String image) {
        int slash = image.lastIndexOf('/');
        int colon = image.lastIndexOf(':');
        if (colon > slash) {
            return new String[]{image.substring(0, colon), image.substring(colon + 1)};
        }
        return new String[]{image, "latest"};
    }

    static List<String> envSlice(Map<String, String> env) {
        if (env == null || env.isEmpty()) {
            return List.of();
        }
        List<String> pairs = new ArrayList<>(env.size());
        for (Map.Entry<String, String> entry : env.entrySet()) {
            pairs.add(entry.getKey() + "=" + (entry.getValue() == null ? "" : entry.getValue()));
        }
        return pairs;
    }

    static Instant parseEpochSeconds(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(stdout.trim().lines().findFirst().orElse("").trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 活动标记防御（backlog S3 / gap-review P1-7）：解析后的标记若超前 now+{@link #MARKER_FUTURE_TOLERANCE}
     * 以上，视为脏数据（旧实例残留/时钟错乱/被工具面伪造），warn 并返回 null 让调用方回退 StartedAt。
     * 容差内的未来漂移（时钟不同步）照常采信；过去时间与 null 行为不变。
     */
    static Instant defensiveMarker(Instant marker, Instant now) {
        if (marker == null) {
            return null;
        }
        if (marker.isAfter(now.plus(MARKER_FUTURE_TOLERANCE))) {
            log.warn("docker sandbox activity marker {} is {}s ahead of now (tolerance {}s) "
                            + "- treating as corrupted, fall back to StartedAt",
                    marker, marker.getEpochSecond() - now.getEpochSecond(),
                    MARKER_FUTURE_TOLERANCE.getSeconds());
            return null;
        }
        return marker;
    }

    /** RFC3339Nano → Instant；零值（0001-01-01…）或解析失败 → EPOCH */
    static Instant parseRfc3339(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            Instant parsed = OffsetDateTime.parse(raw.trim()).toInstant();
            return parsed.getEpochSecond() <= 0 ? Instant.EPOCH : parsed;
        } catch (RuntimeException | LinkageError e) {
            return Instant.EPOCH;
        }
    }

    static String firstNonEmptyLine(String output) {
        if (output == null) {
            return "";
        }
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed;
            }
        }
        return "";
    }

    /** 对齐 WeKnora dockerContainerNotRunning：ExecCreate 409 的消息信号（Conflict kind 不可替换） */
    static boolean containerNotRunning(Throwable e) {
        return e != null && e.getMessage() != null
                && e.getMessage().toLowerCase().contains("is not running");
    }

    private static String requireOwnHandle(SandboxHandle handle, String op) {
        if (handle == null) {
            throw SandboxProviderException.invalidRequest(op + ": sandbox handle is required");
        }
        if (!PROVIDER.equals(handle.getProvider())) {
            throw SandboxProviderException.invalidRequest(op + ": handle belongs to provider " + handle.getProvider());
        }
        if (isBlank(handle.getSandboxId())) {
            throw SandboxProviderException.invalidRequest(op + ": sandbox handle has no id");
        }
        return handle.getSandboxId().trim();
    }

    private static String requirePath(String path, String op) {
        if (isBlank(path)) {
            throw SandboxProviderException.invalidRequest(op + ": path is required");
        }
        return path.trim();
    }

    private static long ceilSeconds(Duration duration) {
        return (duration.toMillis() + 999) / 1000;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
