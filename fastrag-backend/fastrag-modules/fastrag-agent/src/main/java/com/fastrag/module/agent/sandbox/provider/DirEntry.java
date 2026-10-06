package com.fastrag.module.agent.sandbox.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 目录项（agent.SandboxProviderGateway.listDir 出参元素）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirEntry {

    private String name;

    private String path;

    /** true = 目录 */
    private boolean directory;

    /** 字节数；目录为 -1 */
    private long size;
}
