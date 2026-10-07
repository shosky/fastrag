package com.fastrag.module.agent.sandbox.impl;

import com.fastrag.module.agent.sandbox.SandboxHandle;
import com.fastrag.module.agent.sandbox.SandboxWorkspaceLayout;
import com.fastrag.module.agent.sandbox.provider.ExecRequest;
import com.fastrag.module.agent.sandbox.provider.ExecResult;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * 沙箱产物回传兜底（backlog S4 / gap-review P1-8 最小版）。
 *
 * <p>沙箱实例删除（release / 身份变更销毁 / 空闲回收）前，把 {@code /workspace/output}
 * 下的产物打包回服务端落盘：exec {@code tar cf - -C /workspace/output . | base64 -w0}
 * 单行文本回传，解码后写 {@code {baseDir}/{sessionKey 消毒}-{timestamp}/output.tar}。</p>
 *
 * <p>兜底基调（调用方契约）：本类失败一律上抛，由调用方 warn 后继续删除——
 * <b>产物回传永不阻断沙箱删除</b>。空 output（stdout 空）与超限 payload 静默跳过
 * （超限 warn），均为返回 0 的正常路径。前端展示与消息绑定登记后续批次。</p>
 */
@Slf4j
@Component
public class SandboxOutputCollector {

    /** 单次回传 base64 上限（8M 字符 ≈ 明文 tar 6MB）：超限跳过并 warn，防 exec 结果撑爆内存 */
    static final int MAX_BASE64_CHARS = 8 * 1024 * 1024;
    /** 回传 tar 的 exec 超时（兜底通道，从宽不从严） */
    static final long COLLECT_TIMEOUT_SECONDS = 120;
    /** 落盘目录名时间戳（UTC，毫秒精度防同秒碰撞） */
    static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final Path baseDir;

    public SandboxOutputCollector(
            @Value("${agent.sandbox.output-backup-dir:./data/sandbox-outputs}") String baseDir) {
        this.baseDir = Paths.get(baseDir);
    }

    /**
     * 打包回传 /workspace/output。
     *
     * @return 落盘 tar 字节数；0 = 无产物或超限跳过（超限已 warn）
     * @throws IllegalStateException exec 失败/被杀/非零退出（调用方 warn 兜底，不阻断删除）
     */
    public long collect(SandboxProviderClient client, SandboxHandle handle,
                        String sessionKey, Instant now) {
        if (client == null || handle == null || sessionKey == null || sessionKey.isBlank()) {
            return 0;
        }
        ExecResult result = client.exec(handle, ExecRequest.builder()
                .command(collectCommand())
                .timeout(Duration.ofSeconds(COLLECT_TIMEOUT_SECONDS))
                .build());
        if (result.isKilled()) {
            throw new IllegalStateException("sandbox output tar killed by timeout after "
                    + COLLECT_TIMEOUT_SECONDS + "s");
        }
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("sandbox output tar failed (exit " + result.getExitCode()
                    + "): " + firstLine(result.getStderr()));
        }
        String payload = result.getStdout() == null ? "" : result.getStdout().trim();
        if (payload.isEmpty()) {
            return 0; // output 不存在或为空：容器侧命令直接 exit 0
        }
        if (payload.length() > MAX_BASE64_CHARS) {
            log.warn("[SandboxOutput] backup skipped for session {}: payload {} chars exceeds cap {}",
                    sessionKey, payload.length(), MAX_BASE64_CHARS);
            return 0;
        }
        byte[] tarBytes = Base64.getMimeDecoder().decode(payload);
        Path targetDir = baseDir.resolve(sanitizeSessionKey(sessionKey) + "-" + TS.format(now));
        Path target = targetDir.resolve("output.tar");
        try {
            Files.createDirectories(targetDir);
            Files.write(target, tarBytes);
        } catch (IOException e) {
            throw new IllegalStateException("sandbox output backup write failed: " + e.getMessage(), e);
        }
        log.info("[SandboxOutput] backed up {} file(s), {} bytes, session {} (sandbox {}) -> {}",
                countFiles(tarBytes), tarBytes.length, sessionKey, handle.getSandboxId(), target);
        return tarBytes.length;
    }

    /**
     * 收集命令：output 不存在或为空时 stdout 为空（正常路径），否则 tar 打包经 base64 单行回传。
     * 路径常量取自 {@link SandboxWorkspaceLayout}，整段经 provider 侧 shellQuote 单引号包装。
     */
    static String collectCommand() {
        String out = SandboxWorkspaceLayout.OUTPUT_DIR;
        String q = shellQuote(out);
        return "if [ ! -d " + q + " ] || [ -z \"$(ls -A " + q + " 2>/dev/null)\" ]; then exit 0; fi; "
                + "tar cf - -C " + q + " . | base64 -w0";
    }

    /** 会话键消毒：目录名仅保留文件系统安全字符（Windows 盘符语境冒号非法），防穿越逃逸。 */
    static String sanitizeSessionKey(String sessionKey) {
        String sanitized = sessionKey.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? "unknown" : sanitized;
    }

    private static int countFiles(byte[] tarBytes) {
        int files = 0;
        try (TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(tarBytes))) {
            TarArchiveEntry entry;
            while ((entry = in.getNextTarEntry()) != null) {
                if (!entry.isDirectory()) {
                    files++;
                }
            }
        } catch (IOException e) {
            log.debug("[SandboxOutput] tar entry count failed (backup file itself intact): {}", e.getMessage());
        }
        return files;
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.trim().lines().findFirst().orElse("");
    }

    /** 对齐 DockerSandboxAdapter.shellQuote：单引号安全包装 */
    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
