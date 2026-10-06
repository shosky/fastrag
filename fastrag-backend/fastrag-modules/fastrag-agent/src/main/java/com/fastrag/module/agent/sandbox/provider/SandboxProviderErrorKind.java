package com.fastrag.module.agent.sandbox.provider;

/**
 * Provider 错误归一分类（agent.SandboxProviderGateway 契约）——跨适配器恒定，
 * 上层（Lifecycle/Execution）只认分类不认协议报文。
 * <p>
 * 判定锚例（契约登记）：exec 超时 = TIMEOUT；运行中实例消失 = NOT_FOUND；
 * <b>create 时镜像缺失 = INVALID_REQUEST（不判 NOT_FOUND，防生命周期误重建）</b>；
 * daemon/控制面不可达 = UNAVAILABLE。
 */
public enum SandboxProviderErrorKind {
    TIMEOUT,
    NOT_FOUND,
    UNAVAILABLE,
    AUTH,
    CAPACITY,
    INVALID_REQUEST,
    /** 适配器已识别但后端协议未接线（Cube/E2B 预留期，design §4 实装次序） */
    UNSUPPORTED
}
