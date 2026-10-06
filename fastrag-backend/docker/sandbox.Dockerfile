# FastRAG Sandbox Image (fastrag-sandbox)
# 智能体沙箱容器镜像（REQ-H04/ADR-0006：容器内零服务端，宿主机经 Engine API 直驱）。
# 内容物复刻 WeKnora Dockerfile.sandbox 的 runtime target（2026-09-28 grill 裁决）：
#   Python 3.12 + Node 20 + uv/pnpm + 最小 CLI 工具集 + /workspace 布局。
# 构建：docker build -f docker/sandbox.Dockerfile -t fastrag-sandbox:latest .
#
# 布局约定（SandboxWorkspaceLayout 单一事实源）：
#   /workspace          根（shell 默认工作目录，可读写）
#   /workspace/input    只读附件（工具面拒写）
#   /workspace/output   产物收集（一期布局保留）
#   /workspace/skills   技能只读子树
# 适配器要求（deploy 文档同步）：root 执行、bash、GNU find -printf、coreutils timeout、
# base64（文件面经 exec 实现）；git 为二期 workspace checkpoint 预留。

# Stage 1: Node.js 二进制（避免 NodeSource 安装开销）
FROM --platform=$TARGETPLATFORM node:20-slim AS node-base

FROM --platform=$TARGETPLATFORM ghcr.io/astral-sh/uv:0.12.4 AS uv-base

# Stage 2: runtime（3.12 下限：技能源常见 PEP 701 嵌套引号 f-string，3.11 会拒）
FROM --platform=$TARGETPLATFORM python:3.12-slim AS runtime

COPY --from=node-base /usr/local/bin/node /usr/local/bin/
COPY --from=node-base /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm && \
    ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npm-cli 2>/dev/null; \
    ln -sf /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

COPY --from=uv-base /uv /usr/local/bin/uv
RUN ln -s /usr/local/bin/uv /usr/local/bin/uvx

RUN npm install -g pnpm

# 最小 CLI 工具集：file（模型探类型）、curl（连通性探测/技能取 URL）、压缩工具（技能包解压）、
# git（二期 workspace checkpoint 预留）；bash/grep/sed/coreutils/findutils 镜像自带
RUN apt-get update && apt-get install -y --no-install-recommends \
    jq \
    curl \
    ca-certificates \
    file \
    zip \
    unzip \
    bzip2 \
    xz-utils \
    zstd \
    git \
    && rm -rf /var/lib/apt/lists/* /var/cache/apt/*

# 兼容账号：默认 root 执行（会话独占沙箱，容器内账号非租户边界）；UID 1000 user 供显式按账号执行
RUN groupadd -g 1000 user && \
    useradd -u 1000 -g user -m -s /bin/bash user

# 工作区三目录预建（所有权给兼容账号，供显式按账号执行的工具写入）
RUN mkdir -p /workspace/input /workspace/output /workspace/skills && \
    chown -R user:user /workspace

WORKDIR /workspace
USER root

# 默认 target = runtime：裸 docker build 产出的就是 Docker 后端运行的镜像
FROM runtime AS sandbox
