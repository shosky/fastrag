package com.fastrag.module.agent.sandbox.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** exec 请求（agent.SandboxProviderGateway.exec 入参）。超时由适配器以 in-container 包装实现。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecRequest {

    /** shell 命令（经容器内 bash -c 执行） */
    private String command;

    /** 超时（适配器以容器内 timeout 包装 + 客户端 grace 实现，killed=true 表超时被杀） */
    @Builder.Default
    private Duration timeout = Duration.ofSeconds(120);

    /** 工作目录（null = /workspace） */
    private String workDir;

    /** 附加环境变量 */
    @Builder.Default
    private Map<String, String> env = new LinkedHashMap<>();

    /** stdin 内容（可空） */
    private String stdin;
}
