package com.fastrag.module.agent.sandbox.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;


/**
 * Endpoint 拨号防护（REQ-H16，2026-09-28 grill 裁决）——沙箱后端 endpoint 的 SSRF 防护。
 * <p>
 * 规则（对齐 WeKnora OutboundURLPolicy）：
 * <ul>
 *   <li>云 metadata / link-local（169.254.0.0/16、::1/128 等 link-local）恒拒绝——显式允许私网也拒</li>
 *   <li>私网段（RFC1918 / ULA / loopback）默认拒绝，须 {@code allowPrivateEndpoints} 显式放行</li>
 *   <li>校验发生在两处：配置保存时 {@link #validateEndpoint}；建连时 {@link #guardDial} 对解析 IP 复查
 *       （hostname 可先验为公网、解析时变私网）</li>
 * </ul>
 * 消费方：SandboxConfigModule（保存校验）、Docker TCP daemon 建连、Cube/E2B endpoint 建连。
 */
@Slf4j
@Component
public class EndpointDialGuard {

    /**
     * 配置保存时的 endpoint 校验（REQ-H16）。
     *
     * @throws IllegalArgumentException endpoint 非法（缺 scheme、unix 路径非绝对、目标被策略拒绝）
     */
    public void validateEndpoint(String rawEndpoint, boolean allowPrivateEndpoints) {
        if (rawEndpoint == null || rawEndpoint.isBlank()) {
            return; // 缺省 endpoint（如 Docker 本地 socket）由各适配器自行解析
        }
        String trimmed = rawEndpoint.trim();
        if (trimmed.startsWith("unix://")) {
            String path = trimmed.substring("unix://".length());
            if (!path.startsWith("/")) {
                throw new IllegalArgumentException("Docker unix socket path must be absolute: " + rawEndpoint);
            }
            return;
        }
        URI uri = parseHttpish(trimmed);
        checkAuthority(uri.getHost(), uri.getPort(), allowPrivateEndpoints);
    }

    /**
     * 建连时对解析 IP 复查（REQ-H16）——防 hostname 先验公网、解析变私网。
     * 由各适配器在建连前对已解析地址调用；返回即表示放行，否则抛 {@link SecurityException}。
     */
    public void guardDial(String host, int port, boolean allowPrivateEndpoints) {
        checkAuthority(host, port, allowPrivateEndpoints);
    }

    /**
     * 建连钩子形态：对 socket 目标地址复查（供传输层挂接）。
     */
    public void guardSocketAddress(SocketAddress address, boolean allowPrivateEndpoints) {
        if (address instanceof InetSocketAddress inet && inet.getAddress() != null) {
            checkInetAddress(inet.getAddress(), allowPrivateEndpoints);
        }
    }

    private URI parseHttpish(String raw) {
        String candidate = raw.startsWith("tcp://") ? "http://" + raw.substring("tcp://".length()) : raw;
        try {
            URI uri = URI.create(candidate);
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("Endpoint missing host: " + raw);
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid endpoint: " + raw, e);
        }
    }

    private void checkAuthority(String host, int port, boolean allowPrivateEndpoints) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Endpoint host is blank");
        }
        // 字面量 IP 直接判；hostname 逐个解析 IP 判（先验 + 拨时双查覆盖重解析）
        if (isLiteralIp(host)) {
            checkLiteral(host, allowPrivateEndpoints);
            return;
        }
        try {
            InetAddress[] all = InetAddress.getAllByName(host);
            for (InetAddress addr : all) {
                checkInetAddress(addr, allowPrivateEndpoints);
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve endpoint host: " + host, e);
        }
    }

    private void checkLiteral(String host, boolean allowPrivateEndpoints) {
        try {
            checkInetAddress(InetAddress.getByName(host), allowPrivateEndpoints);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid endpoint address: " + host, e);
        }
    }

    private void checkInetAddress(InetAddress addr, boolean allowPrivateEndpoints) {
        if (addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
            // 云 metadata（169.254.169.254）所在段：显式允许私网也不放行
            throw new SecurityException("Link-local/metadata endpoint is always blocked: " + addr.getHostAddress());
        }
        if (!allowPrivateEndpoints && (addr.isSiteLocalAddress() || addr.isLoopbackAddress()
                || isUniqueLocalIpv6(addr))) {
            throw new SecurityException("Private endpoint requires allow_private_endpoints=true: "
                    + addr.getHostAddress());
        }
    }

    private boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] b = addr.getAddress();
        return b.length == 16 && (b[0] & 0xFF) == 0xFC; // fc00::/7 ULA
    }

    private boolean isLiteralIp(String host) {
        // 含冒号视为 IPv6 字面量；否则按「纯数字与点」判 IPv4 字面量
        if (host.contains(":")) {
            return true;
        }
        for (char c : host.toCharArray()) {
            if (!(Character.isDigit(c) || c == '.')) {
                return false;
            }
        }
        return !host.isEmpty();
    }
}
