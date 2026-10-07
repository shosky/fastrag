package com.fastrag.module.agent.sandbox.impl;

import com.fastrag.module.agent.sandbox.SandboxHandle;
import com.fastrag.module.agent.sandbox.provider.ExecRequest;
import com.fastrag.module.agent.sandbox.provider.ExecResult;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderClient;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 沙箱产物回传兜底用例（backlog S4 / gap-review P1-8 最小版）。
 *
 * <p>删除前对 /workspace/output 打包（exec tar|base64）落盘
 * {@code {baseDir}/{sessionKey 消毒}-{timestamp}/output.tar}；
 * 空 output 不落盘、超限跳过 warn、exec 失败上抛（由调用方 warn 兜底，绝不阻断删除）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SandboxOutputCollectorTest {

    private static final Instant NOW = Instant.parse("2026-09-28T08:00:00Z");

    @Mock
    private SandboxProviderClient docker;

    private SandboxHandle handle() {
        return SandboxHandle.builder().sandboxId("inst-1").provider("docker").state("running").build();
    }

    /** 两文件 + 一目录的 tar → base64（与容器侧 `tar cf - | base64 -w0` 输出同构） */
    private static String base64OfTarWithTwoFiles() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (TarArchiveOutputStream out = new TarArchiveOutputStream(buf)) {
            TarArchiveEntry dir = new TarArchiveEntry("sub/");
            out.putArchiveEntry(dir);
            out.closeArchiveEntry();
            for (String name : new String[]{"a.txt", "sub/b.txt"}) {
                byte[] bytes = ("content-of-" + name).getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry entry = new TarArchiveEntry(name);
                entry.setSize(bytes.length);
                out.putArchiveEntry(entry);
                out.write(bytes);
                out.closeArchiveEntry();
            }
            out.finish();
        }
        return Base64.getEncoder().encodeToString(buf.toByteArray());
    }

    @Test
    void 收集_output非空时_base64解码落盘并返回字节数(@TempDir Path dir) throws Exception {
        String payload = base64OfTarWithTwoFiles();
        when(docker.exec(any(SandboxHandle.class), any(ExecRequest.class))).thenReturn(ExecResult.builder()
                .stdout(payload).exitCode(0).build());

        SandboxOutputCollector collector = new SandboxOutputCollector(dir.toString());
        long bytes = collector.collect(docker, handle(), "u1:s1", NOW);

        assertThat(bytes).isGreaterThan(0);
        try (Stream<Path> entries = Files.list(dir)) {
            Path sessionDir = entries.findFirst().orElseThrow();
            // 会话键消毒：冒号替换为下划线 + 时间戳后缀
            assertThat(sessionDir.getFileName().toString()).startsWith("u1_s1-");
            Path tar = sessionDir.resolve("output.tar");
            assertThat(tar).exists();
            assertThat(Files.readAllBytes(tar))
                    .isEqualTo(Base64.getDecoder().decode(payload));
        }
    }

    @Test
    void 收集_output为空时_返回零且不落盘(@TempDir Path dir) throws Exception {
        when(docker.exec(any(SandboxHandle.class), any(ExecRequest.class))).thenReturn(ExecResult.builder()
                .stdout("").exitCode(0).build());

        SandboxOutputCollector collector = new SandboxOutputCollector(dir.toString());
        long bytes = collector.collect(docker, handle(), "u1:s1", NOW);

        assertThat(bytes).isZero();
        assertThat(fileCount(dir)).as("空 output 不落盘").isZero();
    }

    @Test
    void 收集_超限payload时_跳过并warn不落盘(@TempDir Path dir) throws Exception {
        String oversized = "A".repeat(SandboxOutputCollector.MAX_BASE64_CHARS + 1);
        when(docker.exec(any(SandboxHandle.class), any(ExecRequest.class))).thenReturn(ExecResult.builder()
                .stdout(oversized).exitCode(0).build());

        SandboxOutputCollector collector = new SandboxOutputCollector(dir.toString());
        long bytes = collector.collect(docker, handle(), "u1:s1", NOW);

        assertThat(bytes).isZero();
        assertThat(fileCount(dir)).as("超限跳过不落盘").isZero();
    }

    @Test
    void 收集_exec非零退出或超时被杀时_上抛由调用方兜底(@TempDir Path dir) {
        when(docker.exec(any(SandboxHandle.class), any(ExecRequest.class))).thenReturn(ExecResult.builder()
                .stdout("").stderr("tar: cannot stat").exitCode(1).build());

        SandboxOutputCollector collector = new SandboxOutputCollector(dir.toString());
        assertThatThrownBy(() -> collector.collect(docker, handle(), "u1:s1", NOW))
                .isInstanceOf(IllegalStateException.class);

        when(docker.exec(any(SandboxHandle.class), any(ExecRequest.class))).thenReturn(ExecResult.builder()
                .stdout("xx").exitCode(0).killed(true).build());
        assertThatThrownBy(() -> collector.collect(docker, handle(), "u1:s1", NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 收集_会话键含路径非法片段时_消毒不逃出baseDir(@TempDir Path dir) throws Exception {
        String payload = base64OfTarWithTwoFiles();
        when(docker.exec(any(SandboxHandle.class), any(ExecRequest.class))).thenReturn(ExecResult.builder()
                .stdout(payload).exitCode(0).build());

        SandboxOutputCollector collector = new SandboxOutputCollector(dir.toString());
        collector.collect(docker, handle(), "../../evil:/u1:s1", NOW);

        try (Stream<Path> entries = Files.list(dir)) {
            assertThat(entries.count()).as("消毒后仅落一个目录，无穿越逃逸").isEqualTo(1);
        }
    }

    @Test
    void 收集命令_指向workspace_output且经base64回传() {
        String cmd = SandboxOutputCollector.collectCommand();
        assertThat(cmd).contains("/workspace/output").contains("tar cf -").contains("base64");
    }

    private static int fileCount(Path dir) throws Exception {
        try (Stream<Path> entries = Files.list(dir)) {
            return (int) entries.count();
        }
    }
}
