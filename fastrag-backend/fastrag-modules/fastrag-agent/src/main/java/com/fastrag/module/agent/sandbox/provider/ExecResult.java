package com.fastrag.module.agent.sandbox.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** exec 结果（agent.SandboxProviderGateway.exec 出参）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecResult {

    private String stdout;

    private String stderr;

    private int exitCode;

    /** true = 超时被容器内 timeout 包装杀死 */
    private boolean killed;

    public boolean isSuccess() {
        return exitCode == 0 && !killed;
    }
}
