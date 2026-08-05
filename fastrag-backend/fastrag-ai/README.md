# fastrag-ai

> AI/LLM 集成模块 — 封装 LLM 对话、文本向量化、重排序、语音识别（ASR）、OCR 五种 AI 能力，统一通过 AI 网关调用。

## 模块职责

- **LLM 对话**：同步/流式（SSE）文本生成，支持 Tool Calling（Function Calling）和思考模式（DeepSeek/Qwen3）
- **文本向量化**：将文本转为向量，支持自定义 API 路由
- **重排序**：对检索候选文档按相关性重新排序（Cohere/Jina 格式）
- **语音识别（ASR）**：音频文件转文字，支持时间分段
- **OCR**：图片文字识别，通过 VLM 模型实现

## 对外 REST 端点

无。本模块不暴露任何 HTTP 端点，所有能力通过 Service 接口供其他模块调用。

## 模块依赖

| 方向 | 模块 | 说明 |
|------|------|------|
| 依赖 | fastrag-common | 使用 `BusinessException`、ObjectMapper 配置 |
| 被依赖 | knowledge, retrieval, graph-eval, tools, agent, platform, bootstrap（7 个模块） |

## 关键类说明

### 核心服务

| 类 | 说明 |
|----|------|
| `LlmService` | 核心对话服务。提供 `chat()`（同步）、`streamChat()`（Flux<ChatChunk>）、`streamChatWithTools()`（Flux<StreamEvent>，支持 thinking/tool_calls）等方法。所有方法均支持自定义 `apiUrl`/`apiKey` 实现动态路由 |
| `EmbeddingService` | 向量化服务。`embed(model, texts)` 返回 `List<List<Float>>`，支持动态 API 路由 |
| `RerankService` | 重排序服务。`rerank(model, query, documents, topK)` 返回含 `index`/`relevance_score` 的排序列表 |
| `AsrService` | 语音转文字服务。`transcribe(audioBytes, filename)` 返回含分段信息的 `AsrResult` |
| `OcrService` | 图片文字识别。`recognize(imageBytes, extension)` 返回识别文本 |

### 配置

| 类 | 说明 |
|----|------|
| `AiGatewayConfig` | 创建 `WebClient` Bean，base URL 来自 `ai.gateway.url`，缓冲区限制 20MB |

### 数据模型

| 类 | 说明 |
|----|------|
| `ChatRequest` | LLM 请求：model, messages, temperature, maxTokens, stream, enableThinking, tools, toolChoice |
| `ChatResponse` | LLM 响应：content, toolCalls, finishReason。支持从 OpenAI JSON 解析 |
| `ChatMessage` | 对话消息：role, content, toolCallId, toolCalls。含内部类 `ToolCall`/`FunctionCall` |
| `ChatChunk` | 流式增量：content, finishReason, toolCalls |
| `StreamEvent` | 统一流事件：THINKING / CONTENT / TOOL_CALL_DELTA / FINISH |
| `EmbeddingRequest` | 向量化请求：model, input |
| `AsrResult` | ASR 结果：text, segments（含时间戳） |

## 配置项

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `ai.gateway.url` | `http://localhost:11434` | AI 网关基础 URL |
| `ai.gateway.timeout` | 30 | 请求超时（秒） |
| `ai.asr.url` | `https://api.siliconflow.cn` | ASR 服务地址 |
| `ai.asr.key` | (空) | ASR API Key |
| `ai.asr.model` | `FunAudioLLM/SenseVoiceSmall` | ASR 模型 |
| `ai.ocr.url` | `https://api.siliconflow.cn` | OCR 服务地址 |
| `ai.ocr.key` | (空) | OCR API Key |
| `ai.ocr.model` | `deepseek-ai/DeepSeek-OCR` | OCR 模型 |