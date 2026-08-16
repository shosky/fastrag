## 应用运行时：回答末尾输出"知识来源"

### 现状分析

应用运行时实际生效的 RAG 通路是 `AppServiceImpl.retrieveContext()`（第 651 行）：
- 应用绑定 KB → `retrievalService.search()` 检索 → **只取 content 拼进 system prompt**
- 来源信息（文件名/相似度/内容预览）被完全丢弃，SSE 流里没有任何来源数据 → 前端无法展示

`SearchResultItem` 已有 `fileId`/`similarity`/`previewSnippet`，缺 `fileName`；`RetrievalServiceImpl` 已注入 `KbFileMapper`（第 91 行），查文件名无阻碍。

### 一、后端改动（3 个文件）

#### 1. `SearchResultItem.java` — 新增 fileName 字段
```java
private String fileName;  // 来源文件名（检索时从 kb_file 查得）
```

#### 2. `RetrievalServiceImpl.java` — 填充 fileName
- 在最终合并/组装 SearchResultItem 的地方（约第 1044/1081/1163 行 `item.setFileId(...)` 附近）补 `item.setFileName(文件名)`；第 1212 行已有 `fileMapper.selectById(fileId)` 查文件的先例，抽一个 `resolveFileName(fileId)` 辅助方法批量填充。

#### 3. `AppServiceImpl.java` — retrieveContext 返回结构化结果 + 发 sources 事件
- 新增内部类 `RetrievalContext { String ragContext; List<Map<String,Object>> sources; }`
- `retrieveContext` 返回 `RetrievalContext`，sources 每项：`{ fileName, score, content(截断~200字), fileId }`
- `runStream` 中检索完成后（executeStream 前）发送 SSE 事件：
```java
Map<String, Object> srcData = new LinkedHashMap<>();
srcData.put("sources", ctx.getSources());
emitter.send(SseEmitter.event().name("sources").data(srcData));
```
- 无绑定 KB / 检索失败时 `sources` 为空数组，事件照发（前端忽略空数组）

### 二、前端改动（2 个文件）

#### 1. `useAppChatStream.ts` — 新增 sources 事件
- `ChatMessage` 无关，此处加：
```typescript
export interface SourceItem { fileName?: string; score?: number; content?: string; fileId?: string }
// AppChatCallbacks 加 onSources?: (sources: SourceItem[]) => void
```
- SSE 解析 switch 加 `case 'sources'`，解析 data.sources 数组回调

#### 2. `runtime.vue` — 渲染知识来源
- `ChatMessage` 接口加 `sources?: SourceItem[]`
- `handleSend` 的 `sendMessage` 调用传 `onSources: (s) => { session.messages[mi].sources = s }`
- 模板：assistant 消息内容下方，`v-if="msg.sources?.length"` 渲染折叠面板：
  - 标题"知识来源（N）"
  - 每条：文件名 + 相似度百分比 + 内容预览（2 行截断）
  - 纯 CSS 折叠（默认展开，或收起），不依赖额外组件

### 效果
- 回答结束后（实际在流式生成开始前收到 sources 事件）显示本次回答引用的知识来源
- 文件名 + 相关度 + 片段预览，类似 Dify/FastGPT 的引用展示

### 附注（本次不做，提醒用）
- 工具级 `query_kb` 通路（`KbOperationServiceImpl.queryKnowledgeBase`）目前是占位实现（TODO 未接 RetrievalService），如后续要启用工具检索需另行接入，且存在模块依赖问题（knowledge 不依赖 retrieval），需单独设计。
