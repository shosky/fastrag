# FastRAG 项目宪法

## 项目概述

FastRAG 是一个基于 RAG（Retrieval-Augmented Generation）架构的知识库管理系统。支持文档、图片、音视频等多模态文件的上传、解析、向量化存储和智能检索。

---

## 技术栈

| 领域 | 技术选型 |
|------|----------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2.5 |
| ORM | MyBatis-Plus 3.5.5 |
| 数据库 | MySQL 8.0 |
| 向量数据库 | Milvus v2.3.4（`39.97.60.18:19530`） |
| 缓存 | Redis 7 |
| 消息队列 | RabbitMQ 3 |
| 图数据库 | Neo4j 5 |
| 对象存储 | MinIO（`39.97.60.18:9000`） |
| AI 网关 | Ollama 兼容 API（`http://127.0.0.1:11434`） |

---

## 架构约定

### 模块结构

```
fastrag-backend/
├── fastrag-ai/          # AI 能力层（ASR、OCR、Embedding、LLM）
├── fastrag-bootstrap/   # 启动入口 + application.yml
├── fastrag-common/      # 公共工具、枚举、常量
├── fastrag-infra/       # 基础设施（Milvus、MinIO、消息队列等）
├── fastrag-security/    # 安全认证（JWT）
├── fastrag-modules/     # 业务模块
│   ├── fastrag-knowledge/   # 知识库核心（上传、解析、分块、存储）
│   ├── fastrag-retrieval/   # 检索服务
│   └── fastrag-platform/    # 平台管理（模型管理、用户等）
└── init-scripts/        # 数据库初始化 DDL + DML
```

### 依赖方向

- `fastrag-ai` / `fastrag-infra` 不依赖 `fastrag-platform`（不含数据库表访问）
- `fastrag-knowledge` 依赖 `fastrag-ai` + `fastrag-infra` + `fastrag-platform`
- 模型管理表（`model`）的查询逻辑应放在 caller 侧（通常是 `fastrag-knowledge`），而不是 AI/Infra 服务内部

### 存储选择

- **MySQL** 是首选存储，所有持久化数据落 MySQL
- **Elasticsearch 已移除**，全文检索依赖 MySQL `LIKE` 查询
- **Milvus** 只存向量，用于语义检索，元数据关联通过 MySQL 的 `kb_chunk` 表实现
- 不做 ES 相关的任何开发

---

## 外部依赖

### 中间件地址（部署在 `39.97.60.18`）

| 服务 | 地址 |
|------|------|
| MySQL | `39.97.60.18:3306` |
| Redis | `39.97.60.18:6379` |
| RabbitMQ | `39.97.60.18:5672` |
| Milvus | `39.97.60.18:19530` |
| MinIO | `39.97.60.18:9000` |
| Neo4j | `39.97.60.18:7687` |

### 云端 AI 服务（硅基流动）

| 能力 | 模型 | 地址 |
|------|------|------|
| ASR 语音转文字 | `FunAudioLLM/SenseVoiceSmall` | `https://api.siliconflow.cn/v1/audio/transcriptions` |
| OCR 图片文字识别 | `deepseek-ai/DeepSeek-OCR` | `https://api.siliconflow.cn/v1/chat/completions` |
| Embedding 向量化 | 通过模型管理表（`model`）配置，动态路由 |

鉴权：`Bearer Token`，通过 `ASR_API_KEY` 环境变量注入。

### 本地工具

- **FFmpeg**：视频音频提取 + 关键帧采样，路径配置为 `D:\Servers\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe`

---

## 编码规范

### 命名

- 类名：大驼峰，名词性（`DocumentParserImpl`, `MediaExtractor`）
- 方法名：小驼峰，动词开头（`extractKeyframes`, `mergeSegmentsWithKeyframes`）
- 包名：全小写，按模块分层（`com.fastrag.module.knowledge.parser`）
- 数据库表：`kb_` 前缀（知识库相关）、小写 + 下划线
- 环境变量：全大写 + 下划线

### 异常处理原则

- **优雅降级**：所有外部依赖调用都 try-catch，失败时不阻断主流程
- 日志级别：正常流程用 `info` / `debug`，异常降级用 `warn`，真正不可恢复的错误用 `error`
- 不要抛出未捕获的运行时异常让事务回滚，除非业务上必须原子性

### 配置原则

- 所有可变的参数（采样间隔、阈值、API 地址等）优先从 `KbParseStrategy.advanced` JSON 读取
- 基础设施地址从 `application.yml` 读取，用 `@Value` 注入
- 模型相关的 API 地址/密钥从 `model` 表查询，不在配置文件硬编码

---

## 多模态处理管线

### 视频上传后处理流程

```
视频上传 → IngestionConsumer
  ├── 音频路: FFmpeg 提取音频 → SenseVoiceSmall ASR → 时间戳分段
  └── 视觉路: FFmpeg fps=1/N 均匀采样 → pHash 去重 → DeepSeek-OCR
  → 合并: 以 ASR 分段对齐 → "【画面内容】\n...\n【语音内容】\n..."
  → 联合 chunk → Embedding 向量 → MySQL + Milvus
```

### 关键帧配置参数（`KbParseStrategy.advanced` JSON）

```json
{
  "keyframeIntervalSeconds": 10,
  "keyframeHashThreshold": 10
}
```

### 降级策略

- FFmpeg 不可用 → 跳过关键帧，纯 ASR 模式
- ASR 失败 → 跳过音频，纯关键帧 OCR 模式
- OCR 失败 → 跳过当前帧，继续处理后续帧
- Embedding 未配置或失败 → 纯文本存储，不阻塞流程

---

## 对话约定

### 核心宪法

**若指令不明确，不得立即执行。** 必须使用苏格拉底式提问（一次一问，给出可选项）逐层澄清需求，确保完全理解后再动手编码。

### 每次对话归档

每轮对话的需求和回答，必须写入 `docs/sessions/` 目录，以当前时间戳命名文件：
- 文件名格式：`docs/sessions/YYYY-MM-DD-HHmmss.md`
- 内容包含：用户提出的需求、苏格拉底式问答过程、最终确认的方案、实施要点

### 宪法自我进化

如果对话中发现了有价值的通用规则、最佳实践或架构决策，应主动将其加入 `AGENT.md` 对应章节，无需等待用户提出。

### 需求确认方式

### 需求确认方式

在启动非 trivial 任务之前，请先通过**苏格拉底式问答**来澄清需求：
- 每次提出 1 个问题，逐个确认
- 给出具体、可选择的选项，而不是开放式提问
- 确认需求后再制定计划执行

### 计划模式

对于涉及多文件修改的功能开发：
1. 先探索代码库，理解现有模式
2. 制定实施计划（修改哪些文件、做什么改动）
3. 用户审批后再开始编码

### 不采纳的实践

- Elasticsearch 相关的任何开发不做
- 不在 AI/Infra 层直接查询业务数据库
- 不硬编码外部依赖地址

---

## 文档体系

项目根目录的 `AGENT.md` 为本宪法。
IDD 相关的文档按需生成（spec → design → contract → tdd → implement），遵循 IDD 技能工作流。
