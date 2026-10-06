package com.fastrag.module.agent.sandbox.provider;

import lombok.Getter;

/**
 * Provider 侧受检错误：一律携带 {@link SandboxProviderErrorKind} 归一分类。
 * 网关自身不降级——恢复策略（降级文本/销毁重建）由上层决定（agent.SandboxProviderGateway 契约）。
 */
@Getter
public class SandboxProviderException extends RuntimeException {

    private final SandboxProviderErrorKind kind;
    /** provider 侧原始原因（透传给验证端点错误体） */
    private final String providerMessage;

    public SandboxProviderException(SandboxProviderErrorKind kind, String message) {
        super(message);
        this.kind = kind;
        this.providerMessage = message;
    }

    public SandboxProviderException(SandboxProviderErrorKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.providerMessage = message;
    }

    public static SandboxProviderException timeout(String message) {
        return new SandboxProviderException(SandboxProviderErrorKind.TIMEOUT, message);
    }

    public static SandboxProviderException notFound(String message) {
        return new SandboxProviderException(SandboxProviderErrorKind.NOT_FOUND, message);
    }

    public static SandboxProviderException unavailable(String message) {
        return new SandboxProviderException(SandboxProviderErrorKind.UNAVAILABLE, message);
    }

    public static SandboxProviderException invalidRequest(String message) {
        return new SandboxProviderException(SandboxProviderErrorKind.INVALID_REQUEST, message);
    }

    public static SandboxProviderException unsupported(String message) {
        return new SandboxProviderException(SandboxProviderErrorKind.UNSUPPORTED, message);
    }
}
