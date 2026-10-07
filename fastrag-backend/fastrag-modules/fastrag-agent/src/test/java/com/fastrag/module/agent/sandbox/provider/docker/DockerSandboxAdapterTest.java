package com.fastrag.module.agent.sandbox.provider.docker;

import com.fastrag.module.agent.sandbox.SandboxWorkspaceLayout;
import com.fastrag.module.agent.sandbox.provider.CreateContext;
import com.fastrag.module.agent.sandbox.provider.DirEntry;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderErrorKind;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderException;
import com.github.dockerjava.api.exception.BadRequestException;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.UnauthorizedException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DockerSandboxAdapter 纯单元用例（不触 Docker）：命令组装与单引号转义、find 输出解析、
 * 状态映射、exec 包装脚本组装（活动标记 + timeout 段）、加固基线 HostConfig、错误分类映射。
 * 组装/解析/分类已抽为包内可见静态函数，daemon 交互走 DockerSandboxAdapterIntegrationTest。
 */
class DockerSandboxAdapterTest {

    // ---------- 单引号安全包装（对齐 WeKnora shellQuote） ----------

    @Test
    void 单引号包装_普通路径() {
        assertThat(DockerSandboxAdapter.shellQuote("/workspace/out.txt")).isEqualTo("'/workspace/out.txt'");
    }

    @Test
    void 单引号包装_含空格() {
        assertThat(DockerSandboxAdapter.shellQuote("/workspace/my file.txt")).isEqualTo("'/workspace/my file.txt'");
    }

    @Test
    void 单引号包装_含单引号写成背靠背转义() {
        // it's → 'it'\''s'（单引号内的单引号写成 '\''）
        assertThat(DockerSandboxAdapter.shellQuote("it's")).isEqualTo("'it'\\''s'");
        assertThat(DockerSandboxAdapter.shellQuote("/workspace/o'bird.txt")).isEqualTo("'/workspace/o'\\''bird.txt'");
    }

    @Test
    void 单引号包装_shell元字符不逃逸() {
        // 整体单引号包裹后，分号/反引号/变量展开均失去元字符语义
        assertThat(DockerSandboxAdapter.shellQuote("a;rm -rf /")).isEqualTo("'a;rm -rf /'");
        assertThat(DockerSandboxAdapter.shellQuote("$(whoami)")).isEqualTo("'$(whoami)'");
    }

    // ---------- exec 包装脚本（活动标记 + timeout 段） ----------

    @Test
    void exec包装脚本_含活动标记与timeout段() {
        String wrapper = DockerSandboxAdapter.execWrapper("echo ok", 2, "/workspace/.fastrag_activity");
        assertThat(wrapper)
                .startsWith("touch /workspace/.fastrag_activity; ")
                .contains("timeout -k 5 2 ")
                .contains("bash -c ")
                .endsWith("'echo ok'");
    }

    @Test
    void exec包装脚本_命令内单引号被转义() {
        String wrapper = DockerSandboxAdapter.execWrapper("echo 'hi there'", 30, "/workspace/.fastrag_activity");
        // 命令整体包在单引号里，内部单引号转义为 '\''
        assertThat(wrapper).contains("bash -c 'echo '\\''hi there'\\'''");
    }

    @Test
    void exec包装脚本_超时下限1秒() {
        String wrapper = DockerSandboxAdapter.execWrapper("true", 0, "/workspace/.fastrag_activity");
        assertThat(wrapper).contains("timeout -k 5 1 ");
    }

    @Test
    void exec命令数组_包装脚本作为bash_c单参数() {
        String[] argv = DockerSandboxAdapter.execCmdArray("touch /workspace/.fastrag_activity; timeout -k 5 2 bash -c 'echo ok'");
        assertThat(argv).containsExactly("bash", "-c",
                "touch /workspace/.fastrag_activity; timeout -k 5 2 bash -c 'echo ok'");
    }

    // ---------- 文件面命令组装 ----------

    @Test
    void 文件面命令_路径带空格与单引号均被包装() {
        String space = "/workspace/my dir/my file.txt";
        assertThat(DockerSandboxAdapter.commandReadFile(space))
                .isEqualTo("base64 -w0 '/workspace/my dir/my file.txt'");
        assertThat(DockerSandboxAdapter.commandExists(space))
                .isEqualTo("test -e '/workspace/my dir/my file.txt' && echo Y || echo N");
        assertThat(DockerSandboxAdapter.commandMakeDir(space)).isEqualTo("mkdir -p '/workspace/my dir/my file.txt'");
        assertThat(DockerSandboxAdapter.commandRemove(space)).isEqualTo("rm -rf '/workspace/my dir/my file.txt'");
    }

    @Test
    void 末段文件名_容忍尾斜杠() {
        assertThat(DockerSandboxAdapter.baseName("/workspace/printer.py")).isEqualTo("printer.py");
        assertThat(DockerSandboxAdapter.baseName("/workspace/dir/")).isEqualTo("dir");
        assertThat(DockerSandboxAdapter.baseName("main.py")).isEqualTo("main.py");
    }

    @Test
    void 单文件tar_entry名与内容可解包还原() throws Exception {
        byte[] tar = DockerSandboxAdapter.tarBytes("printer.py", "print('hi')\n");
        try (TarArchiveInputStream in = new TarArchiveInputStream(
                new java.io.ByteArrayInputStream(tar))) {
            TarArchiveEntry entry = in.getNextTarEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("printer.py");
            assertThat(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .isEqualTo("print('hi')\n");
            assertThat(in.getNextTarEntry()).isNull();
        }
    }

    @Test
    void 文件面命令_列目录用find_printf带tab分隔() {
        assertThat(DockerSandboxAdapter.commandListDir("/workspace")).isEqualTo(
                "find '/workspace' -maxdepth 1 -mindepth 1 -printf '%y\\t%s\\t%f\\n'");
    }

    // ---------- QF-7（idd-review 快修）：writeFile 前置 exec 复合活动标记 ----------

    @Test
    void writeFile前置命令_mkdir复合活动标记touch() {
        // tar 通道绕过 execWrapper 的 touch 前缀，活动刷新复合进 mkdir 阶段（零额外往返）
        String cmd = DockerSandboxAdapter.commandWriteFilePrepare(
                "/workspace/sub", "/workspace/.fastrag_activity");
        assertThat(cmd).isEqualTo("mkdir -p '/workspace/sub'; touch /workspace/.fastrag_activity");
    }

    @Test
    void writeFile前置命令_父目录带空格与单引号安全() {
        String cmd = DockerSandboxAdapter.commandWriteFilePrepare(
                "/workspace/my dir", "/workspace/.fastrag_activity");
        assertThat(cmd).isEqualTo("mkdir -p '/workspace/my dir'; touch /workspace/.fastrag_activity");
    }

    @Test
    void writeFile前置命令_无额外exec往返() {
        // 单条命令一次 exec 完成 mkdir + touch：以 "; touch " 复合，不再是纯 mkdir
        String cmd = DockerSandboxAdapter.commandWriteFilePrepare(
                "/workspace", "/workspace/.fastrag_activity");
        assertThat(cmd).startsWith("mkdir -p '/workspace'");
        assertThat(cmd).contains("; touch /workspace/.fastrag_activity");
        assertThat(cmd.indexOf("mkdir")).isLessThan(cmd.indexOf("touch"));
    }

    @Test
    void 父目录计算() {
        assertThat(DockerSandboxAdapter.parentDir("/a/b.txt")).isEqualTo("/a");
        assertThat(DockerSandboxAdapter.parentDir("/x")).isEqualTo("/");
        assertThat(DockerSandboxAdapter.parentDir("rel.txt")).isEqualTo(".");
        assertThat(DockerSandboxAdapter.parentDir("/a/b/c.log")).isEqualTo("/a/b");
    }

    // ---------- find -printf 输出解析（对齐 WeKnora parseDockerFindOutput 语义） ----------

    @Test
    void find输出解析_文件与目录() {
        String output = "f\t12\tout.txt\nd\t4096\tdir\n";
        List<DirEntry> entries = DockerSandboxAdapter.parseFindOutput("/workspace", output);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getName()).isEqualTo("out.txt");
        assertThat(entries.get(0).getPath()).isEqualTo("/workspace/out.txt");
        assertThat(entries.get(0).isDirectory()).isFalse();
        assertThat(entries.get(0).getSize()).isEqualTo(12L);
        assertThat(entries.get(1).getName()).isEqualTo("dir");
        assertThat(entries.get(1).getPath()).isEqualTo("/workspace/dir");
        assertThat(entries.get(1).isDirectory()).isTrue();
        assertThat(entries.get(1).getSize()).isEqualTo(-1L); // 目录大小无意义
    }

    @Test
    void find输出解析_残行跳过不整体失败() {
        String output = "f\t12\tgood.txt\ngarbage-no-tab\nf\tnot-a-number\todd.txt\n\nl\t33\tlink\n";
        List<DirEntry> entries = DockerSandboxAdapter.parseFindOutput("/workspace", output);
        assertThat(entries).extracting(DirEntry::getName)
                .containsExactly("good.txt", "odd.txt", "link");
        assertThat(entries.get(1).getSize()).isEqualTo(-1L); // 大小解析失败保留条目
        assertThat(entries.get(2).isDirectory()).isFalse();  // y∉{f,d} 按非目录保留
    }

    @Test
    void find输出解析_名字含tab不截断() {
        // %f 为最后一个字段：split 限段 3，名字中的 tab 保留
        List<DirEntry> entries = DockerSandboxAdapter.parseFindOutput("/w", "f\t5\ta\tb\n");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getName()).isEqualTo("a\tb");
    }

    @Test
    void find输出解析_空输出为空列表() {
        assertThat(DockerSandboxAdapter.parseFindOutput("/workspace", "")).isEmpty();
        assertThat(DockerSandboxAdapter.parseFindOutput("/workspace", null)).isEmpty();
    }

    // ---------- 状态映射 ----------

    @Test
    void 状态映射_docker七态归一() {
        assertThat(DockerSandboxAdapter.mapDockerState("running")).isEqualTo("running");
        assertThat(DockerSandboxAdapter.mapDockerState("paused")).isEqualTo("paused");
        assertThat(DockerSandboxAdapter.mapDockerState("exited")).isEqualTo("paused");
        assertThat(DockerSandboxAdapter.mapDockerState("created")).isEqualTo("paused");
        assertThat(DockerSandboxAdapter.mapDockerState("restarting")).isEqualTo("transitioning");
        assertThat(DockerSandboxAdapter.mapDockerState("removing")).isEqualTo("transitioning");
        assertThat(DockerSandboxAdapter.mapDockerState("dead")).isEqualTo("terminal");
        assertThat(DockerSandboxAdapter.mapDockerState("")).isEqualTo("unknown");
        assertThat(DockerSandboxAdapter.mapDockerState(null)).isEqualTo("unknown");
        assertThat(DockerSandboxAdapter.mapDockerState("  Running ")).isEqualTo("running");
    }

    // ---------- 错误分类映射（契约六分类锚例） ----------

    @Test
    void 错误分类_运行中实例消失判NOT_FOUND() {
        assertThat(DockerSandboxAdapter.classifyError("exec", new NotFoundException("No such container")))
                .isEqualTo(SandboxProviderErrorKind.NOT_FOUND);
        assertThat(DockerSandboxAdapter.classifyError("connect", new NotFoundException("No such container")))
                .isEqualTo(SandboxProviderErrorKind.NOT_FOUND);
    }

    @Test
    void 错误分类_create时镜像缺失判INVALID_REQUEST而非NOT_FOUND() {
        // 契约锚例：防生命周期把「镜像写错」当「实例消失」误重建
        assertThat(DockerSandboxAdapter.classifyError("create", new NotFoundException("no such image")))
                .isEqualTo(SandboxProviderErrorKind.INVALID_REQUEST);
    }

    @Test
    void 错误分类_其余映射() {
        assertThat(DockerSandboxAdapter.classifyError("exec", new UnauthorizedException("unauthorized")))
                .isEqualTo(SandboxProviderErrorKind.AUTH);
        assertThat(DockerSandboxAdapter.classifyError("exec", new BadRequestException("bad request")))
                .isEqualTo(SandboxProviderErrorKind.INVALID_REQUEST);
        assertThat(DockerSandboxAdapter.classifyError("exec", new ConflictException("conflict")))
                .isEqualTo(SandboxProviderErrorKind.INVALID_REQUEST);
        assertThat(DockerSandboxAdapter.classifyError("exec", new RuntimeException("Read timed out")))
                .isEqualTo(SandboxProviderErrorKind.TIMEOUT);
        assertThat(DockerSandboxAdapter.classifyError("exec", new RuntimeException("Connection refused")))
                .isEqualTo(SandboxProviderErrorKind.UNAVAILABLE);
        assertThat(DockerSandboxAdapter.classifyError("exec", new RuntimeException("boom")))
                .isEqualTo(SandboxProviderErrorKind.UNAVAILABLE);
        // Windows 本地开发对 unix socket 缺原生支持的传输层失败（UnsatisfiedLinkError 是 Error 非 RuntimeException）
        assertThat(DockerSandboxAdapter.classifyError("exec", new UnsatisfiedLinkError("write")))
                .isEqualTo(SandboxProviderErrorKind.UNAVAILABLE);
    }

    @Test
    void 错误分类_wrapOp透传已归一异常() {
        SandboxProviderException original = SandboxProviderException.timeout("already normalized");
        SandboxProviderException wrapped = DockerSandboxAdapter.wrapOp("exec", original);
        assertThat(wrapped).isSameAs(original);
        SandboxProviderException classified = DockerSandboxAdapter.wrapOp("exec", new RuntimeException("x"));
        assertThat(classified.getKind()).isEqualTo(SandboxProviderErrorKind.UNAVAILABLE);
    }

    @Test
    void 错误分类_文件面缺失判NOT_FOUND其余INVALID_REQUEST() {
        assertThat(DockerSandboxAdapter.fileOpError("readFile", "/w/a.txt", "cat: /w/a.txt: No such file or directory").getKind())
                .isEqualTo(SandboxProviderErrorKind.NOT_FOUND);
        assertThat(DockerSandboxAdapter.fileOpError("writeFile", "/w/a.txt", "permission denied").getKind())
                .isEqualTo(SandboxProviderErrorKind.INVALID_REQUEST);
    }

    @Test
    void 超时杀_退出码137与124判killed() {
        assertThat(DockerSandboxAdapter.execWasKilled(137)).isTrue();
        assertThat(DockerSandboxAdapter.execWasKilled(124)).isTrue();
        assertThat(DockerSandboxAdapter.execWasKilled(0)).isFalse();
        assertThat(DockerSandboxAdapter.execWasKilled(1)).isFalse();
        assertThat(DockerSandboxAdapter.execWasKilled(143)).isFalse();
    }

    // ---------- 加固基线 HostConfig（REQ-H14 create Pre 不变量） ----------

    @Test
    void 加固基线_CapDrop_ALL且CapAdd恰为白名单7项() {
        HostConfig hc = DockerSandboxAdapter.buildHostConfig(CreateContext.builder().build());
        assertThat(hc.getCapDrop()).containsExactly(Capability.ALL);
        assertThat(hc.getCapAdd())
                .containsExactlyInAnyOrder(Capability.CHOWN, Capability.DAC_OVERRIDE, Capability.FOWNER,
                        Capability.FSETID, Capability.SETGID, Capability.SETUID, Capability.KILL);
    }

    @Test
    void 加固基线_禁swap与安全选项与Init() {
        HostConfig hc = DockerSandboxAdapter.buildHostConfig(CreateContext.builder().build());
        assertThat(hc.getMemory()).isPositive();
        assertThat(hc.getMemorySwap()).isEqualTo(hc.getMemory()); // memory == memorySwap → 禁 swap
        assertThat(hc.getSecurityOpts()).containsExactly("no-new-privileges");
        assertThat(hc.getInit()).isTrue();
        assertThat(hc.getNetworkMode()).isEqualTo("bridge");
        assertThat(hc.getPidsLimit()).isEqualTo(512L);
        assertThat(hc.getNanoCPUs()).isEqualTo(2_000_000_000L);
    }

    @Test
    void 加固基线_配置覆盖资源三上限() {
        HostConfig hc = DockerSandboxAdapter.buildHostConfig(CreateContext.builder()
                .cpuLimit(1.5)
                .memoryBytes(1024L * 1024 * 1024)
                .pidsLimit(64)
                .networkMode("none")
                .build());
        assertThat(hc.getNanoCPUs()).isEqualTo(1_500_000_000L);
        assertThat(hc.getMemory()).isEqualTo(1024L * 1024 * 1024);
        assertThat(hc.getMemorySwap()).isEqualTo(1024L * 1024 * 1024);
        assertThat(hc.getPidsLimit()).isEqualTo(64L);
        assertThat(hc.getNetworkMode()).isEqualTo("none");
    }

    // ---------- 确定性容器名 ----------

    @Test
    void 容器名_前缀加sha256前12位且确定() {
        String name = DockerSandboxAdapter.containerName("cfg-1", "user-1:session-1");
        assertThat(name).startsWith("fastrag-sbx-").hasSize("fastrag-sbx-".length() + 12);
        assertThat(name.substring("fastrag-sbx-".length())).matches("[0-9a-f]{12}");
        assertThat(DockerSandboxAdapter.containerName("cfg-1", "user-1:session-1")).isEqualTo(name);
        assertThat(DockerSandboxAdapter.containerName("cfg-2", "user-1:session-1")).isNotEqualTo(name);
        assertThat(DockerSandboxAdapter.containerName(null, null)).startsWith("fastrag-sbx-");
    }

    // ---------- 镜像引用与 endpoint ----------

    @Test
    void 镜像引用解析_仓库与标签() {
        assertThat(DockerSandboxAdapter.repoTag("alpine")).containsExactly("alpine", "latest");
        assertThat(DockerSandboxAdapter.repoTag("alpine:3.19")).containsExactly("alpine", "3.19");
        assertThat(DockerSandboxAdapter.repoTag("localhost:5000/img:v1"))
                .containsExactly("localhost:5000/img", "v1");
        assertThat(DockerSandboxAdapter.repoTag("ghcr.io/org/img")).containsExactly("ghcr.io/org/img", "latest");
    }

    @Test
    void 拨号守护_unix不需要tcp需要() {
        assertThat(DockerSandboxAdapter.needsDialGuard("unix:///var/run/docker.sock")).isFalse();
        assertThat(DockerSandboxAdapter.needsDialGuard("tcp://daemon:2375")).isTrue();
        assertThat(DockerSandboxAdapter.needsDialGuard("http://daemon:2375")).isTrue();
        assertThat(DockerSandboxAdapter.needsDialGuard("https://daemon:2376")).isTrue();
        assertThat(DockerSandboxAdapter.needsDialGuard("npipe:////./pipe/docker_engine")).isFalse();
        assertThat(DockerSandboxAdapter.needsDialGuard(null)).isFalse();
    }

    @Test
    void 缺省endpoint解析_显式优先环境变量次之缺省unix() {
        assertThat(DockerSandboxAdapter.resolveDockerHost("tcp://explicit:2375")).isEqualTo("tcp://explicit:2375");
        // 环境变量分支依赖宿主环境，只断言缺省兜底形态
        String resolved = DockerSandboxAdapter.resolveDockerHost("  ");
        assertThat(resolved).isIn("unix:///var/run/docker.sock", System.getenv("DOCKER_HOST") == null
                ? "unix:///var/run/docker.sock" : System.getenv("DOCKER_HOST").trim());
    }

    // ---------- 时间与杂项 ----------

    @Test
    void stat输出解析_epoch秒() {
        Instant t = Instant.ofEpochSecond(1_700_000_000);
        assertThat(DockerSandboxAdapter.parseEpochSeconds("1700000000\n")).isEqualTo(t);
        assertThat(DockerSandboxAdapter.parseEpochSeconds("not-a-number")).isNull();
        assertThat(DockerSandboxAdapter.parseEpochSeconds("")).isNull();
    }

    @Test
    void rfc3339解析_零值回退EPOCH() {
        assertThat(DockerSandboxAdapter.parseRfc3339("0001-01-01T00:00:00Z")).isEqualTo(Instant.EPOCH);
        assertThat(DockerSandboxAdapter.parseRfc3339("2026-09-28T08:00:00.123456789Z"))
                .isEqualTo(Instant.parse("2026-09-28T08:00:00.123456789Z"));
        assertThat(DockerSandboxAdapter.parseRfc3339(null)).isEqualTo(Instant.EPOCH);
        assertThat(DockerSandboxAdapter.parseRfc3339("garbage")).isEqualTo(Instant.EPOCH);
    }

    @Test
    void env切片_KEY_VALUE形态() {
        assertThat(DockerSandboxAdapter.envSlice(Map.of("A", "1"))).containsExactly("A=1");
        assertThat(DockerSandboxAdapter.envSlice(Map.of())).isEmpty();
        assertThat(DockerSandboxAdapter.envSlice(null)).isEmpty();
    }

    @Test
    void env注入_经ExecCreateWithEnv通道_值不拼进包装命令() {
        // F4（skill-feature-batch.md F4）：exec() 把 request.env 经 envSlice 喂给 ExecCreate.withEnv；
        // 包装命令（execWrapper）签名不接收 env——结构上保证凭据值不进命令行字符串。
        // env → withEnv 的管道单元断言：KEY=VALUE 形态 + null 值健壮性。
        Map<String, String> env = new java.util.LinkedHashMap<>();
        env.put("APP_TOKEN", "t-1");
        env.put("BROKEN", null);
        assertThat(DockerSandboxAdapter.envSlice(env)).containsExactly("APP_TOKEN=t-1", "BROKEN=");

        String wrapper = DockerSandboxAdapter.execWrapper("echo $APP_TOKEN", 5,
                "/workspace/.fastrag_activity");
        assertThat(wrapper).contains("echo $APP_TOKEN");
        assertThat(wrapper).doesNotContain("t-1");
    }

    @Test
    void stderr首行截断() {
        assertThat(DockerSandboxAdapter.firstNonEmptyLine("\n  boom: detail \nnext")).isEqualTo("boom: detail");
        assertThat(DockerSandboxAdapter.firstNonEmptyLine("x".repeat(300))).hasSize(201); // 200 + 省略号
        assertThat(DockerSandboxAdapter.firstNonEmptyLine(null)).isEmpty();
    }

    @Test
    void 容器未运行信号识别() {
        assertThat(DockerSandboxAdapter.containerNotRunning(
                new ConflictException("Container f00 is not running"))).isTrue();
        assertThat(DockerSandboxAdapter.containerNotRunning(
                new ConflictException("name already in use"))).isFalse();
        assertThat(DockerSandboxAdapter.containerNotRunning(null)).isFalse();
    }

    @Test
    void execGrace预算_文件面超时常量为对齐WeKnora取值() {
        assertThat(DockerSandboxAdapter.EXEC_GRACE).isEqualTo(Duration.ofSeconds(10));
        assertThat(DockerSandboxAdapter.FS_OP_TIMEOUT).isEqualTo(Duration.ofSeconds(30));
        assertThat(DockerSandboxAdapter.START_READY_TIMEOUT).isEqualTo(Duration.ofSeconds(15));
    }

    // ---------- 活动标记防御（backlog S3 / gap-review P1-7） ----------

    private static final Instant S3_NOW = Instant.parse("2026-09-28T08:00:00Z");

    @Test
    void 活动标记防御_标记在未来超2分钟时_判脏返回null触发StartedAt回退() {
        // 标记比 now+2min 还晚 = 脏数据（残留/时钟错乱/伪造），不得采信（否则空闲回收永不触发）
        assertThat(DockerSandboxAdapter.defensiveMarker(S3_NOW.plusSeconds(121), S3_NOW)).isNull();
        assertThat(DockerSandboxAdapter.defensiveMarker(S3_NOW.plusSeconds(3600), S3_NOW)).isNull();
    }

    @Test
    void 活动标记防御_未来漂移在2分钟容差内_照常采信() {
        // 容器与宿主的小幅时钟漂移属正常，容差内不误杀
        assertThat(DockerSandboxAdapter.defensiveMarker(S3_NOW.plusSeconds(119), S3_NOW))
                .isEqualTo(S3_NOW.plusSeconds(119));
        assertThat(DockerSandboxAdapter.defensiveMarker(S3_NOW, S3_NOW)).isEqualTo(S3_NOW);
    }

    @Test
    void 活动标记防御_过去时间或不可解析_行为不变() {
        assertThat(DockerSandboxAdapter.defensiveMarker(S3_NOW.minusSeconds(3600), S3_NOW))
                .isEqualTo(S3_NOW.minusSeconds(3600));
        assertThat(DockerSandboxAdapter.defensiveMarker(null, S3_NOW)).isNull(); // null 仍由调用方回退 StartedAt
    }

    @Test
    void 活动标记路径_常量统一引用布局类() {
        // S3：标记路径单一事实源收敛到 SandboxWorkspaceLayout（保留路径与 adapter touch 同源）
        assertThat(DockerSandboxAdapter.ACTIVITY_MARKER)
                .isEqualTo(SandboxWorkspaceLayout.ACTIVITY_MARKER)
                .isEqualTo("/workspace/.fastrag_activity");
    }
}
