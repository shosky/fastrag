package com.fastrag.module.agent.sandbox.provider.docker;

import com.fastrag.module.agent.sandbox.SandboxHandle;
import com.fastrag.module.agent.sandbox.provider.CreateContext;
import com.fastrag.module.agent.sandbox.provider.DirEntry;
import com.fastrag.module.agent.sandbox.provider.ExecRequest;
import com.fastrag.module.agent.sandbox.provider.ExecResult;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderErrorKind;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderException;
import com.fastrag.module.agent.sandbox.security.EndpointDialGuard;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Docker 适配器对真实 daemon 的集成测试（docs/tdd/agent.md S11 用例表）。
 *
 * <p><b>守护式</b>：类开始 ping daemon，不可达（本地无 Docker 是常态）则 {@code assumeTrue}
 * 跳过全部用例，surefire 报 Skipped &gt; 0 而非失败。镜像用 python:3.12-slim（自带
 * GNU timeout/find -printf/base64/stat，不必等 fastrag-sandbox 镜像）。</p>
 *
 * <p>覆盖：加固基线 inspect 断言（REQ-H14）、managed 标记（REQ-H11/H05）、exec 往返、
 * 超时杀（killed=true）、文件面往返、stop 后自恢复（REQ-H08）、pip install 跨 exec 保持、
 * 错误分类锚例×2（镜像缺失=INVALID_REQUEST、已删容器 exec=NOT_FOUND）、活动标记 mtime 递增（REQ-H02）。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DockerSandboxAdapterIntegrationTest {

    private static final String IMAGE = envOr("FASTRAG_IT_IMAGE", "python:3.12-slim");

    private final DockerSandboxAdapter adapter = new DockerSandboxAdapter(new EndpointDialGuard(), true);

    private DockerClient inspectClient;
    private CreateContext ctx;
    private SandboxHandle sandbox;
    private boolean daemonChecked;
    private boolean daemonUp;
    private String daemonDownReason = "";
    private final String unique = UUID.randomUUID().toString().substring(0, 8);

    private static String envOr(String key, String fallback) {
        String env = System.getenv(key);
        return env == null || env.isBlank() ? fallback : env.trim();
    }

    /**
     * 守护门（每个用例首行调用）：首次调用 ping daemon，不可达（本地无 Docker 是常态）
     * 则 assumption 失败跳过该用例（surefire 记 Skipped）；可达则创建共享沙箱后放行。
     */
    private void requireDaemon() {
        if (!daemonChecked) {
            daemonChecked = true;
            try {
                adapter.health(CreateContext.builder().build());
                daemonUp = true;
                inspectClient = buildInspectClient();
                ctx = CreateContext.builder()
                        .configId("cfg-it-" + unique)
                        .sessionKey("it-user:it-session-" + unique)
                        .userId("it-user")
                        .sessionId("it-session-" + unique)
                        .imageOrTemplate(IMAGE)
                        .cpuLimit(1.5)
                        .memoryBytes(512L * 1024 * 1024)
                        .pidsLimit(256)
                        .networkMode("bridge")
                        .idleTtl(Duration.ofSeconds(1800))
                        .build();
                sandbox = adapter.create(ctx);
            } catch (Throwable unreachable) {
                daemonDownReason = unreachable.getMessage() == null ? unreachable.toString() : unreachable.getMessage();
            }
        }
        assumeTrue(daemonUp, "Docker daemon unreachable, skip S11 integration case: " + daemonDownReason);
    }

    @AfterAll
    void cleanup() {
        if (sandbox != null) {
            try {
                adapter.delete(sandbox);
            } catch (RuntimeException ignored) {
                // 清理尽力而为
            }
        }
        if (inspectClient != null) {
            try {
                inspectClient.close();
            } catch (Exception ignored) {
                // 清理尽力而为
            }
        }
    }

    private static DockerClient buildInspectClient() {
        String host = DockerSandboxAdapter.resolveDockerHost(null);
        DefaultDockerClientConfig config = new DefaultDockerClientConfig.Builder()
                .withDockerHost(host)
                .build();
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(URI.create(host))
                .sslConfig(config.getSSLConfig())
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

    @Test
    @Order(1)
    void 加固基线_inspect断言白名单() {
        requireDaemon();
        InspectContainerResponse inspected = inspectClient.inspectContainerCmd(sandbox.getSandboxId()).exec();
        var hc = inspected.getHostConfig();
        assertThat(hc.getCapDrop()).containsExactly(Capability.ALL);
        List<Capability> whitelist = Arrays.asList(DockerSandboxAdapter.ALLOWED_CAPS);
        assertThat(hc.getCapAdd()).isNotEmpty();
        assertThat(hc.getCapAdd()).allMatch(whitelist::contains); // CapAdd ⊆ 7 白名单
        assertThat(hc.getSecurityOpts()).contains("no-new-privileges");
        assertThat(hc.getMemory()).isEqualTo(ctx.getMemoryBytes());
        assertThat(hc.getMemorySwap()).isEqualTo(hc.getMemory()); // 禁 swap
        assertThat(hc.getInit()).isTrue();
        assertThat(hc.getNanoCPUs()).isEqualTo(1_500_000_000L);
        assertThat(hc.getPidsLimit()).isEqualTo(ctx.getPidsLimit());
        assertThat(hc.getNetworkMode()).isEqualTo("bridge");
        assertThat(inspected.getConfig().getWorkingDir()).isEqualTo("/workspace");
        assertThat(inspected.getConfig().getImage()).isEqualTo(IMAGE);
        assertThat(inspected.getState().getStatus()).isEqualTo("running");
    }

    @Test
    @Order(2)
    void 元数据标记_managed标签与configId_userId_sessionId完整() {
        requireDaemon();
        InspectContainerResponse inspected = inspectClient.inspectContainerCmd(sandbox.getSandboxId()).exec();
        Map<String, String> labels = inspected.getConfig().getLabels();
        assertThat(labels)
                .containsEntry("com.fastrag.sandbox.managed", "true")
                .containsEntry("com.fastrag.sandbox.config-id", ctx.getConfigId())
                .containsEntry("com.fastrag.sandbox.user-id", ctx.getUserId())
                .containsEntry("com.fastrag.sandbox.session-id", ctx.getSessionId())
                .containsEntry("com.fastrag.sandbox.idle-ttl-seconds", "1800");

        assertThat(adapter.list(Map.of("com.fastrag.sandbox.managed", "true"), null, null, null, false))
                .extracting(SandboxHandle::getSandboxId)
                .contains(sandbox.getSandboxId());
        assertThat(adapter.list(Map.of("com.fastrag.sandbox.config-id", ctx.getConfigId()), Set.of("running"), null, null, false))
                .extracting(SandboxHandle::getSandboxId)
                .contains(sandbox.getSandboxId());
        assertThat(adapter.list(Map.of("com.fastrag.sandbox.config-id", "no-such-config"), null, null, null, false)).isEmpty();
    }

    @Test
    @Order(3)
    void create重连幂等_同名容器直接复用() {
        requireDaemon();
        SandboxHandle again = adapter.create(ctx);
        assertThat(again.getSandboxId()).isEqualTo(sandbox.getSandboxId());
        assertThat(again.getState()).isEqualTo("running");
    }

    @Test
    @Order(4)
    void exec往返_echo的stdout与exitCode正确() {
        requireDaemon();
        ExecResult result = adapter.exec(sandbox, ExecRequest.builder().command("echo ok").build());
        assertThat(result.getExitCode()).isZero();
        assertThat(result.isKilled()).isFalse();
        assertThat(result.getStdout().trim()).isEqualTo("ok");

        ExecResult streams = adapter.exec(sandbox, ExecRequest.builder()
                .command("echo err >&2; echo out").build());
        assertThat(streams.getStdout().trim()).isEqualTo("out");
        assertThat(streams.getStderr().trim()).isEqualTo("err");
    }

    @Test
    @Order(5)
    void 空闲标记_两次exec后活动标记mtime递增() throws InterruptedException {
        requireDaemon();
        adapter.exec(sandbox, ExecRequest.builder().command("true").build());
        Instant first = adapter.lastActivity(sandbox);
        Thread.sleep(1100); // 跨过 mtime 粒度
        adapter.exec(sandbox, ExecRequest.builder().command("true").build());
        Instant second = adapter.lastActivity(sandbox);
        assertThat(first).isNotNull();
        assertThat(second).isAfter(first);
    }

    @Test
    @Order(6)
    void 文件面往返_write_read_listDir内容一致() {
        requireDaemon();
        String dir = "/workspace/it-" + unique;
        String path = dir + "/out.txt";
        String content = "héllo fastrag\nline2 with 空格 and 'quotes'\n";
        adapter.writeFile(sandbox, path, content);
        assertThat(adapter.readFile(sandbox, path)).isEqualTo(content);
        assertThat(adapter.exists(sandbox, path)).isTrue();
        assertThat(adapter.exists(sandbox, path + ".missing")).isFalse();

        List<DirEntry> rootEntries = adapter.listDir(sandbox, "/workspace");
        assertThat(rootEntries).extracting(DirEntry::getName).contains("it-" + unique);
        assertThat(rootEntries).allSatisfy(entry -> {
            if (entry.getName().equals("it-" + unique)) {
                assertThat(entry.isDirectory()).isTrue();
            }
        });
        List<DirEntry> dirEntries = adapter.listDir(sandbox, dir);
        assertThat(dirEntries).hasSize(1);
        assertThat(dirEntries.get(0).getName()).isEqualTo("out.txt");
        assertThat(dirEntries.get(0).isDirectory()).isFalse();
        assertThat(dirEntries.get(0).getSize()).isEqualTo(content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);

        // 路径带空格与单引号
        String tricky = dir + "/my file o'ne.txt";
        adapter.writeFile(sandbox, tricky, "tricky");
        assertThat(adapter.readFile(sandbox, tricky)).isEqualTo("tricky");

        // editFile str_replace
        adapter.editFile(sandbox, path, "line2", "LINE2", false);
        assertThat(adapter.readFile(sandbox, path)).contains("LINE2").doesNotContain("line2");
        adapter.editFile(sandbox, path, "with", "WITH", true);
        assertThat(adapter.readFile(sandbox, path)).contains("WITH");

        assertThatThrownBy(() -> adapter.editFile(sandbox, path, "no-such-old", "x", false))
                .isInstanceOf(SandboxProviderException.class)
                .extracting(e -> ((SandboxProviderException) e).getKind())
                .isEqualTo(SandboxProviderErrorKind.INVALID_REQUEST);

        // makeDir / remove
        String nested = dir + "/a/b/c";
        adapter.makeDir(sandbox, nested);
        assertThat(adapter.exists(sandbox, nested)).isTrue();
        adapter.remove(sandbox, nested);
        assertThat(adapter.exists(sandbox, nested)).isFalse();

        // readFile 缺失路径 → NOT_FOUND
        assertThatThrownBy(() -> adapter.readFile(sandbox, dir + "/no-such.txt"))
                .isInstanceOf(SandboxProviderException.class)
                .extracting(e -> ((SandboxProviderException) e).getKind())
                .isEqualTo(SandboxProviderErrorKind.NOT_FOUND);

        adapter.remove(sandbox, dir);
        assertThat(adapter.exists(sandbox, dir)).isFalse();
    }

    @Test
    @Order(7)
    void 超时杀_sleep超timeout时killed为真且及时返回() {
        requireDaemon();
        long start = System.nanoTime();
        ExecResult result = adapter.exec(sandbox, ExecRequest.builder()
                .command("sleep 10")
                .timeout(Duration.ofSeconds(2))
                .build());
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertThat(result.isKilled()).isTrue();
        assertThat(result.getExitCode()).isIn(124, 137);
        assertThat(elapsedMillis).isLessThan(15_000); // 客户端 deadline = 2s + 10s grace，实际 ~2s
    }

    @Test
    @Order(8)
    void 状态自恢复_手动stop后exec先恢复再执行成功() {
        requireDaemon();
        inspectClient.stopContainerCmd(sandbox.getSandboxId()).withTimeout(1).exec();
        assertThat(adapter.connect(sandbox).getState()).isEqualTo("running");
        ExecResult result = adapter.exec(sandbox, ExecRequest.builder().command("echo resumed").build());
        assertThat(result.getExitCode()).isZero();
        assertThat(result.getStdout().trim()).isEqualTo("resumed");
    }

    @Test
    @Order(9)
    void 跨轮真实保持_pip安装后新exec仍可导入() {
        requireDaemon();
        ExecResult install = adapter.exec(sandbox, ExecRequest.builder()
                .command("pip install --quiet --no-input --disable-pip-version-check requests")
                .timeout(Duration.ofSeconds(240))
                .build());
        assertThat(install.getExitCode())
                .as("pip install should succeed: %s", install.getStderr())
                .isZero();
        ExecResult verify = adapter.exec(sandbox, ExecRequest.builder()
                .command("python -c \"import requests; print('import-ok', requests.__version__)\"")
                .build());
        assertThat(verify.getExitCode()).isZero();
        assertThat(verify.getStdout()).contains("import-ok");
    }

    @Test
    @Order(10)
    void 错误分类防误重建_镜像不存在时create判INVALID_REQUEST() {
        requireDaemon();
        CreateContext missing = CreateContext.builder()
                .configId(ctx.getConfigId())
                .sessionKey("it-user:it-image-missing-" + unique)
                .userId("it-user")
                .sessionId("it-image-missing")
                .imageOrTemplate("fastrag-it-no-such-image:latest")
                .build();
        assertThatThrownBy(() -> adapter.create(missing))
                .isInstanceOf(SandboxProviderException.class)
                .extracting(e -> ((SandboxProviderException) e).getKind())
                .isEqualTo(SandboxProviderErrorKind.INVALID_REQUEST); // 不判 NOT_FOUND（契约锚例）
    }

    @Test
    @Order(11)
    void 错误分类_已删容器上exec判NOT_FOUND且delete幂等() {
        requireDaemon();
        CreateContext ephemeral = CreateContext.builder()
                .configId(ctx.getConfigId())
                .sessionKey("it-user:it-ephemeral-" + unique)
                .userId("it-user")
                .sessionId("it-ephemeral")
                .imageOrTemplate(IMAGE)
                .build();
        SandboxHandle handle = adapter.create(ephemeral);
        adapter.delete(handle);
        adapter.delete(handle); // 幂等：已不存在按成功
        assertThatThrownBy(() -> adapter.exec(handle, ExecRequest.builder().command("echo x").build()))
                .isInstanceOf(SandboxProviderException.class)
                .extracting(e -> ((SandboxProviderException) e).getKind())
                .isEqualTo(SandboxProviderErrorKind.NOT_FOUND);
    }
}
