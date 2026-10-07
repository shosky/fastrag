package com.fastrag.module.agent.sandbox.impl;

import com.fastrag.module.agent.sandbox.BindingRecord;
import com.fastrag.module.agent.sandbox.SandboxBindingStore;
import com.fastrag.module.agent.sandbox.SandboxHandle;
import com.fastrag.module.agent.sandbox.SandboxLifecycleService;
import com.fastrag.module.agent.sandbox.config.EffectiveConfig;
import com.fastrag.module.agent.sandbox.config.SandboxConfigService;
import com.fastrag.module.agent.sandbox.memory.InMemorySandboxBindingStore;
import com.fastrag.module.agent.sandbox.provider.CreateContext;
import com.fastrag.module.agent.sandbox.provider.ExecRequest;
import com.fastrag.module.agent.sandbox.provider.ExecResult;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderClient;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderErrorKind;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderException;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderRegistry;
import com.fastrag.module.agent.sandbox.redis.RedisSandboxBindingStore;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SandboxLifecycle 用例（docs/tdd/agent.md S7 SandboxLifecycle 表 11 条，
 * contract agent.SandboxLifecycle）。
 *
 * <p>mock SandboxProviderClient + InMemory 绑定存储 + 注入时钟；
 * [重启可解析] 用例以 mock StringRedisTemplate 验证 Redis 适配器序列化与 key 形态。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SandboxLifecycleServiceTest {

    private static final String USER = "u1";
    private static final String SESSION = "s1";
    private static final Instant T0 = Instant.parse("2026-09-28T08:00:00Z");

    @Mock
    private SandboxProviderClient docker;

    @Mock
    private SandboxConfigService sandboxConfigService;

    private SandboxProviderRegistry registry;
    private InMemorySandboxBindingStore store;
    private SandboxLifecycleServiceImpl service;
    private AtomicReference<Instant> clock;

    @BeforeEach
    void setUp() {
        when(docker.provider()).thenReturn("docker"); // registry 按 provider() 建映射
        registry = new SandboxProviderRegistry(List.of(docker));
        store = new InMemorySandboxBindingStore();
        clock = new AtomicReference<>(T0);
        service = new SandboxLifecycleServiceImpl(registry, List.of(docker), store, sandboxConfigService);
        when(sandboxConfigService.listEnabledDockerEffective()).thenReturn(List.of(config()));
        service.setNow(clock::get);
    }

    // ---------- 构造工具 ----------

    private EffectiveConfig config() {
        return EffectiveConfig.builder()
                .type("docker")
                .configId("cfg-1")
                .userId(USER)
                .enabled(true)
                .identityFingerprint("fp-1")
                .imageOrTemplate("fastrag-sandbox:latest")
                .build();
    }

    private SandboxHandle handle(String sandboxId) {
        return SandboxHandle.builder()
                .sandboxId(sandboxId)
                .provider("docker")
                .configId("cfg-1")
                .state("running")
                .lastUsed(T0.toEpochMilli())
                .build();
    }

    /** create 按 "inst-N" 递增发号，返回 running 句柄（provider 侧非确定 ID 语义） */
    private void createIssuesNewInstances() {
        AtomicInteger seq = new AtomicInteger();
        when(docker.create(any(CreateContext.class)))
                .thenAnswer(inv -> handle("inst-" + seq.incrementAndGet()));
    }

    /** connect 自愈语义：置 running 并按注入时钟 touch（Docker 适配器 ensureRunning 的 mock 面） */
    private void connectRestoresRunning() {
        when(docker.connect(any(SandboxHandle.class))).thenAnswer(inv -> {
            SandboxHandle h = inv.getArgument(0);
            h.setState("running");
            h.setLastUsed(clock.get().toEpochMilli());
            return h;
        });
    }

    // ---------- 1. [绑定复用] 同会话两次 acquire 时 [恒得同一实例且保活] ----------

    @Test
    void 绑定复用_同会话两次acquire时_恒得同一实例且保活() {
        createIssuesNewInstances();
        connectRestoresRunning();

        SandboxHandle first = service.acquire(USER, SESSION, config());
        clock.set(T0.plusSeconds(5)); // 时钟推进（touch 依据）
        SandboxHandle second = service.acquire(USER, SESSION, config());

        assertThat(second.getSandboxId()).isEqualTo(first.getSandboxId());
        assertThat(second.getLastUsed()).isGreaterThan(first.getLastUsed()); // lastUsed 刷新
        verify(docker, times(1)).create(any(CreateContext.class)); // 只建一次
        verify(docker, times(1)).connect(any(SandboxHandle.class)); // 命中路径走 connect 保活
    }

    // ---------- 2. [新会话新箱] 不同 sessionId acquire 时 [得不同实例] ----------

    @Test
    void 新会话新箱_不同sessionId_acquire时_得不同实例() {
        createIssuesNewInstances();

        SandboxHandle first = service.acquire(USER, "s1", config());
        SandboxHandle second = service.acquire(USER, "s2", config());

        assertThat(second.getSandboxId()).isNotEqualTo(first.getSandboxId());
        verify(docker, times(2)).create(any(CreateContext.class));
    }

    // ---------- 3. [exited 自恢复] 绑定实例 inspect 为 exited 时 [acquire 先恢复再返回] ----------

    @Test
    void exited自恢复_绑定实例为exited时_acquire先恢复再返回() {
        createIssuesNewInstances();
        connectRestoresRunning();
        service.acquire(USER, SESSION, config());

        SandboxHandle resumed = service.acquire(USER, SESSION, config());

        assertThat(resumed.getState()).isEqualTo("running");
        assertThat(resumed.getSandboxId()).isEqualTo("inst-1");
        verify(docker, times(1)).connect(any(SandboxHandle.class)); // 自恢复被调
        verify(docker, times(1)).create(any(CreateContext.class));  // 未重建
    }

    // ---------- 4. [身份重建] 绑定指纹≠当前配置时 [销毁重建并 casRebind] ----------

    @Test
    void 身份重建_绑定身份失效时_销毁重建并casRebind() {
        createIssuesNewInstances();
        connectRestoresRunning();
        service.acquire(USER, SESSION, config());

        // 配置身份变更（REQ-H09 上游信号）：批量置 staleAt
        service.onConfigIdentityChanged("cfg-1");
        assertThat(store.get(USER, SESSION).getStaleAt()).isNotNull();

        SandboxHandle rebuilt = service.acquire(USER, SESSION, config());

        assertThat(rebuilt.getSandboxId()).isEqualTo("inst-2"); // 新实例
        verify(docker).delete(any(SandboxHandle.class));        // 销毁旧
        verify(docker, times(2)).create(any(CreateContext.class));
        BindingRecord binding = store.get(USER, SESSION);
        assertThat(binding.getSandboxId()).isEqualTo("inst-2"); // 绑定指向新实例
        assertThat(binding.getStaleAt()).isNull();              // 重建后失效标记清除
    }

    // ---------- 5. [会话删除] onSessionDeleted 时 [绑定清除且实例删除] ----------

    @Test
    void 会话删除_onSessionDeleted时_绑定清除且实例删除() {
        createIssuesNewInstances();
        service.acquire(USER, SESSION, config());

        service.onSessionDeleted(USER, SESSION);

        assertThat(store.get(USER, SESSION)).isNull();
        verify(docker).delete(any(SandboxHandle.class));
    }

    // ---------- 6. [空闲回收] 活动标记超创建时 TTL 时 [删容器清绑定] ----------

    @Test
    void 空闲回收_活动标记超TTL时_删容器清绑定() {
        createIssuesNewInstances();
        SandboxHandle handle = service.acquire(USER, SESSION, config());
        when(docker.list(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(handle));
        when(docker.lastActivity(any(SandboxHandle.class))).thenReturn(T0);
        when(docker.idleTtl(any(SandboxHandle.class))).thenReturn(null); // label 不可读 → 进程内记录兜底

        clock.set(T0.plusSeconds(1801));
        service.reclaimIdle();

        verify(docker).delete(handle);
        assertThat(store.get(USER, SESSION)).isNull(); // 绑定清除
    }

    // ---------- 7. [TTL 固定] 配置 TTL 事后调小时 [在途实例按创建时 TTL] ----------

    @Test
    void TTL固定_配置TTL事后调小时_在途实例按创建时TTL() {
        createIssuesNewInstances();
        SandboxHandle handle = service.acquire(USER, SESSION, config()); // 创建时 idleTtl 固定进容器 label = 1800s
        when(docker.list(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(handle));
        when(docker.lastActivity(any(SandboxHandle.class))).thenReturn(T0);
        // provider 侧 label（创建时固定）
        when(docker.idleTtl(any(SandboxHandle.class))).thenReturn(Duration.ofSeconds(1800));
        // 服务缺省事后被调小为 60s（模拟配置默认值变更）
        ReflectionTestUtils.setField(service, "idleTtlSeconds", 60L);

        clock.set(T0.plusSeconds(100));
        service.reclaimIdle();

        verify(docker, never()).delete(any(SandboxHandle.class)); // 按创建时 1800s 判定，不回收
        assertThat(store.get(USER, SESSION)).isNotNull();
    }

    // ---------- 8. [连接失败] acquire 时 create 失败 [分类上抛且无绑定残留] ----------

    @Test
    void 连接失败_create失败时_分类上抛且无绑定残留() {
        when(docker.create(any(CreateContext.class)))
                .thenThrow(SandboxProviderException.unavailable("daemon unreachable"));

        assertThatThrownBy(() -> service.acquire(USER, SESSION, config()))
                .isInstanceOf(SandboxProviderException.class)
                .satisfies(e -> assertThat(((SandboxProviderException) e).getKind())
                        .isEqualTo(SandboxProviderErrorKind.UNAVAILABLE));

        assertThat(store.get(USER, SESSION)).isNull(); // 失败不留半截绑定
    }

    // ---------- 9. [孤儿对账] 无绑定且超 grace 的实例 [被删除，grace 内保留] ----------

    @Test
    void 孤儿对账_无绑定且超grace的实例_被删除且grace内保留() {
        SandboxHandle aged = handle("orphan-old");
        SandboxHandle fresh = handle("orphan-new");
        when(docker.list(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(aged, fresh));
        when(docker.lastActivity(handle("orphan-old"))).thenReturn(T0.minusSeconds(11 * 60));
        when(docker.lastActivity(handle("orphan-new"))).thenReturn(T0.minusSeconds(60));

        service.reconcileOrphans();

        verify(docker).delete(aged);      // 11min > grace(600s) → 删
        verify(docker, never()).delete(fresh); // 1min < grace → 保留
    }

    // ---------- 10. [孤儿对账保留] 绑定库仍引用的实例 [不删] ----------

    @Test
    void 孤儿对账保留_绑定库仍引用的实例_不删() {
        createIssuesNewInstances();
        SandboxHandle bound = service.acquire(USER, SESSION, config());
        when(docker.list(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(bound));
        when(docker.lastActivity(any(SandboxHandle.class))).thenReturn(T0.minusSeconds(3600));

        service.reconcileOrphans();

        verify(docker, never()).delete(bound); // 绑定仍在 → 保留（无论多旧）
        assertThat(store.get(USER, SESSION)).isNotNull();
    }

    // ---------- 11. [重启可解析] 重建 Service 实例后 [lookup 仍解析同一实例] ----------

    @Test
    void 重启可解析_重建Service实例后_lookup仍解析同一实例() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        RedisSandboxBindingStore redisStore = new RedisSandboxBindingStore(redisTemplate);

        BindingRecord record = BindingRecord.of("docker", "inst-42", "cfg-1", T0);
        redisStore.put(USER, SESSION, record);

        // 验证 key 形态与 JSON 序列化（data.md sandbox 绑定存储节）
        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> valueCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo("sandbox:binding:u1:s1");
        assertThat(valueCaptor.getValue())
                .contains("\"sandboxId\":\"inst-42\"")
                .contains("\"configId\":\"cfg-1\"")
                .contains("\"version\":1");

        // 进程重启语义：新 Service + 同一外部存储 → lookup 仍解析同一实例
        when(valueOps.get("sandbox:binding:u1:s1")).thenReturn(valueCaptor.getValue());
        SandboxLifecycleService restarted = new SandboxLifecycleServiceImpl(
                registry, List.of(docker), redisStore, sandboxConfigService);

        SandboxHandle looked = restarted.lookup(USER, SESSION);

        assertThat(looked).isNotNull();
        assertThat(looked.getSandboxId()).isEqualTo("inst-42");
        assertThat(looked.getProvider()).isEqualTo("docker");
    }

    // ---------- 产物回传兜底（backlog S4 / gap-review P1-8 最小版） ----------

    private static String base64OfTarOneFile() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (TarArchiveOutputStream out = new TarArchiveOutputStream(buf)) {
            byte[] bytes = "hello artifact".getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry entry = new TarArchiveEntry("result.txt");
            entry.setSize(bytes.length);
            out.putArchiveEntry(entry);
            out.write(bytes);
            out.closeArchiveEntry();
            out.finish();
        }
        return Base64.getEncoder().encodeToString(buf.toByteArray());
    }

    private void stubBackupExec(String stdout) {
        when(docker.exec(any(SandboxHandle.class), any(ExecRequest.class))).thenReturn(ExecResult.builder()
                .stdout(stdout).exitCode(0).build());
    }

    @Test
    void 产物回传_release删除前_output非空时_落盘且实例仍删除(@TempDir Path dir) throws Exception {
        createIssuesNewInstances();
        service.acquire(USER, SESSION, config());
        ReflectionTestUtils.setField(service, "outputCollector", new SandboxOutputCollector(dir.toString()));
        stubBackupExec(base64OfTarOneFile());

        service.release(USER, SESSION);

        verify(docker).delete(any(SandboxHandle.class)); // 回传失败也不阻断，成功更不阻断
        assertThat(store.get(USER, SESSION)).isNull();
        try (Stream<Path> entries = Files.list(dir)) {
            Path sessionDir = entries.findFirst().orElseThrow();
            assertThat(sessionDir.getFileName().toString()).startsWith("u1_s1-"); // 会话键消毒
            assertThat(sessionDir.resolve("output.tar")).exists();
        }
    }

    @Test
    void 产物回传_身份变更销毁前_output非空时_落盘后重建(@TempDir Path dir) throws Exception {
        createIssuesNewInstances();
        connectRestoresRunning();
        service.acquire(USER, SESSION, config());
        ReflectionTestUtils.setField(service, "outputCollector", new SandboxOutputCollector(dir.toString()));
        stubBackupExec(base64OfTarOneFile());
        service.onConfigIdentityChanged("cfg-1");

        service.acquire(USER, SESSION, config()); // 路径③：销毁重建

        verify(docker).delete(any(SandboxHandle.class));
        assertThat(fileCount(dir)).isEqualTo(1); // 旧实例产物已落盘
        assertThat(store.get(USER, SESSION).getSandboxId()).isEqualTo("inst-2");
    }

    @Test
    void 产物回传_空闲回收删除前_output非空时_落盘且绑定清除(@TempDir Path dir) throws Exception {
        createIssuesNewInstances();
        SandboxHandle handle = service.acquire(USER, SESSION, config());
        ReflectionTestUtils.setField(service, "outputCollector", new SandboxOutputCollector(dir.toString()));
        when(docker.list(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(handle));
        when(docker.lastActivity(any(SandboxHandle.class))).thenReturn(T0);
        stubBackupExec(base64OfTarOneFile());

        clock.set(T0.plusSeconds(1801));
        service.reclaimIdle();

        verify(docker).delete(handle);
        assertThat(store.get(USER, SESSION)).isNull();
        assertThat(fileCount(dir)).isEqualTo(1); // 按 sandboxId 反查会话键后回传
    }

    @Test
    void 产物回传_exec失败时_仅跳过回传不阻断删除(@TempDir Path dir) throws Exception {
        createIssuesNewInstances();
        service.acquire(USER, SESSION, config());
        ReflectionTestUtils.setField(service, "outputCollector", new SandboxOutputCollector(dir.toString()));
        when(docker.exec(any(SandboxHandle.class), any(ExecRequest.class)))
                .thenThrow(new RuntimeException("container already stopped"));

        assertThatCode(() -> service.release(USER, SESSION)).doesNotThrowAnyException();

        verify(docker).delete(any(SandboxHandle.class));
        assertThat(store.get(USER, SESSION)).isNull();
        assertThat(fileCount(dir)).isZero();
    }

    @Test
    void 产物回传_output为空时_不落盘且正常删除(@TempDir Path dir) throws Exception {
        createIssuesNewInstances();
        service.acquire(USER, SESSION, config());
        ReflectionTestUtils.setField(service, "outputCollector", new SandboxOutputCollector(dir.toString()));
        stubBackupExec("");

        service.release(USER, SESSION);

        verify(docker).delete(any(SandboxHandle.class));
        assertThat(fileCount(dir)).isZero();
    }

    private static int fileCount(Path dir) throws Exception {
        try (Stream<Path> entries = Files.list(dir)) {
            return (int) entries.count();
        }
    }
}
