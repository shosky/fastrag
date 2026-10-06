package com.fastrag.module.agent.sandbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SandboxWorkspaceLayout 用例（REQ-H12：读写根与路径钳制）。 */
class SandboxWorkspaceLayoutTest {

    @Test
    void 可读根覆盖workspace全部() {
        assertThat(SandboxWorkspaceLayout.isReadable("/workspace")).isTrue();
        assertThat(SandboxWorkspaceLayout.isReadable("/workspace/a/b.txt")).isTrue();
        assertThat(SandboxWorkspaceLayout.isReadable("/workspace/input/a.pdf")).isTrue();
        assertThat(SandboxWorkspaceLayout.isReadable("/workspace/skills/demo/SKILL.md")).isTrue();
        assertThat(SandboxWorkspaceLayout.isReadable("/etc/passwd")).isFalse();
        assertThat(SandboxWorkspaceLayout.isReadable("/home/gem/user-data")).isFalse();
    }

    @Test
    void 可写排除input与skills子树() {
        assertThat(SandboxWorkspaceLayout.isWritable("/workspace/app.txt")).isTrue();
        assertThat(SandboxWorkspaceLayout.isWritable("/workspace/output/out.png")).isTrue();
        assertThat(SandboxWorkspaceLayout.isWritable("/workspace/input/a.pdf")).isFalse();
        assertThat(SandboxWorkspaceLayout.isWritable("/workspace/skills/demo/SKILL.md")).isFalse();
        assertThat(SandboxWorkspaceLayout.isWritable("/workspace/skills")).isFalse();
    }

    @Test
    void 钳制拒绝穿越与越界() {
        assertThatThrownBy(() -> SandboxWorkspaceLayout.clamp("/workspace/../etc/passwd"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> SandboxWorkspaceLayout.clamp("/etc/passwd"))
                .isInstanceOf(SecurityException.class);
        assertThat(SandboxWorkspaceLayout.clamp("/workspace/a//b\\c.txt")).isEqualTo("/workspace/a/b/c.txt");
    }

    @Test
    void 规范化折叠斜杠与空值() {
        assertThat(SandboxWorkspaceLayout.normalize(null)).isEqualTo("/workspace");
        assertThat(SandboxWorkspaceLayout.normalize("")).isEqualTo("/workspace");
        assertThat(SandboxWorkspaceLayout.normalize("/workspace///out/")).isEqualTo("/workspace/out");
    }
}
