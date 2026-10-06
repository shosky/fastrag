# ADR: 沙箱容器侧协议收敛 Engine API 直连、容器内零服务端（sandbox-engine-api-direct）

**Date:** 2026-09-28
**Status:** Accepted（随 2026-09-28 沙箱实装 grill 裁决；取代 SandboxBackend.java 的容器内 HTTP 调用设计）

## 背景（Context）

fastrag 智能体沙箱现状为降级桩（SandboxService mock 连接，docker-java 不在任何 pom、整条链路惰性）。既有 SandboxBackend.java 设计为调用**容器内 HTTP API**（`/api/files`、`/api/execute`、`/api/directories`、`/api/search/grep|glob` 等 7 动作），前提是沙箱镜像内驻留一个 HTTP 服务端。真容器实装必须在「容器内服务端」与「宿主机直驱」之间选择；该选择难逆转（镜像契约 + 全部文件/执行调用面绑定其上），且现状设计从未验证过，故立 ADR。

## 决策（Decision）

**容器内零服务端，宿主机直驱**，按 WeKnora Docker 后端同型（参照 `internal/sandbox/docker_remote_client.go`）：执行 = Docker Engine API ExecCreate/Attach/Inspect（in-container `timeout` 命令包装 + 客户端 10s grace 实现超时杀进程）；文件读写 = archive API + exec 实现（mkdir/ls/rm/find）；活动标记（空闲回收依据）折叠进 exec 包装器零额外往返。容器内路径布局 `/workspace`（input 只读附件 / output 产物 / skills 只读）。协议差异由 SandboxProviderClient 缝的三适配器各自承担（Docker = Engine API 直连；Cube/E2B = 控制面 REST + 容器内 envd 数据面——后两者容器内有 envd，属 provider 协议而非自建服务端）。

## 被否备选（Alternatives Considered）

**容器内 HTTP 服务端（原 SandboxBackend 设计）** — 否决：镜像内多一个常驻攻击面（HTTP 端口暴露 + 服务端漏洞面）；服务端进程的生命周期/探活/升级与容器管理耦合；该设计无任何已验证实现。代价：SandboxBackend 7 动作 HTTP 调用面作废重写——该链路从未有真实消费方，无迁移损失。

**容器内 SSH/agent 常驻进程（如 sshd）** — 否决：攻击面与维护负担同 HTTP 且更重，另引入密钥分发问题；Engine API exec 已覆盖全部执行/文件需求。

## 后果（Consequences）

- 镜像退化为普通运行时镜像（python3.12 + node20 + uv/pnpm + CLI 工具，无 EXPOSE、无自建服务端），复刻 WeKnora runtime target 内容物。
- 文件操作需自实现路径清洗与 `find -printf` 输出解析（移植 WeKnora dockerCleanPath/parseDockerFindOutput 语义）；路径钳制策略收进 SandboxWorkspaceLayout 单一事实源（spec REQ-H12）。
- contract 门禁③由「沙箱 HTTP API 调用契约」改写为「Engine API 直连 + envd 双适配器契约」。
- 部署约束：backend 容器须挂载 docker.sock（GID 方案）或连远端 daemon（TLS 强制）——socket 即宿主机 root 等价能力，排障文档承接；EndpointDialGuard 管「连哪个 daemon」，本 ADR 管「连上之后怎么驱动」。
