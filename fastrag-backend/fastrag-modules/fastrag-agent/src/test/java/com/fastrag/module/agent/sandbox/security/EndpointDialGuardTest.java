package com.fastrag.module.agent.sandbox.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** EndpointDialGuard 用例（REQ-H16：metadata 恒拒、私网须显式放行）。 */
class EndpointDialGuardTest {

    private final EndpointDialGuard guard = new EndpointDialGuard();

    @Test
    void 云metadata地址显式放行也恒拒() {
        assertThatThrownBy(() -> guard.validateEndpoint("http://169.254.169.254:80", true))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.validateEndpoint("tcp://169.254.169.254:2376", true))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void 私网endpoint未放行拒放行后过() {
        assertThatThrownBy(() -> guard.validateEndpoint("tcp://10.0.0.5:2376", false))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.validateEndpoint("http://192.168.1.10:33000", false))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.validateEndpoint("http://127.0.0.1:33000", false))
                .isInstanceOf(SecurityException.class);
        assertThatCode(() -> guard.validateEndpoint("tcp://10.0.0.5:2376", true))
                .doesNotThrowAnyException();
    }

    @Test
    void 公网endpoint默认放行() {
        assertThatCode(() -> guard.validateEndpoint("https://api.e2b.app", false))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.validateEndpoint("tcp://203.0.113.10:2376", false))
                .doesNotThrowAnyException();
    }

    @Test
    void unix绝对路径放行相对路径拒() {
        assertThatCode(() -> guard.validateEndpoint("unix:///var/run/docker.sock", false))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.validateEndpoint("unix://var/run/docker.sock", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 空endpoint视为缺省交由适配器解析() {
        assertThatCode(() -> guard.validateEndpoint(null, false)).doesNotThrowAnyException();
        assertThatCode(() -> guard.validateEndpoint("  ", false)).doesNotThrowAnyException();
    }

    @Test
    void 建连时对解析IP复查() {
        // localhost 回环：未放行拒
        assertThatThrownBy(() -> guard.guardDial("localhost", 2375, false))
                .isInstanceOf(SecurityException.class);
        assertThatCode(() -> guard.guardDial("localhost", 2375, true))
                .doesNotThrowAnyException();
        // 不可解析 hostname 报参数错误
        assertThatThrownBy(() -> guard.guardDial("no-such-host.fastrag.invalid", 80, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
