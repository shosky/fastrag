# 检索与查询增强 — Frontend Architecture Design（retrieval-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/retrieval-frontend.md`。

## 1. Page Map

| PAGE | 路由/宿主 | 权限 | 入口 | 退出 |
|---|---|---|---|---|
| PAGE-001 检索测试 | 宿主 `/knowledge/:id` Tab retrieval | 宿主权限 | 详情 Tab | Tab 切换 |
| PAGE-002 检索设置 | 宿主 `/knowledge/:id/edit` Tab 4 | 随表单 | 表单 Tab | 随表单提交 |
| PAGE-003 检索日志分析 | `/operation/retrieval-analysis` | `menu:operation:retrieval-analysis` | Sidebar 知识库组:108 | 侧边栏导航 |

## 2. Page/Layout/Component Map

### PAGE-001 检索测试
```
┌───────────────────────────────────────────┬──────────────┐
│ SearchTestPanel                            │ RetrievalSidebar│
│ [图片预览条(≤5)] [输入框(Enter)]            │ (320px sticky) │
│ 图谱扩展提示 | 纠错条 | 重写tags | 同义词     │ Header+[保存]  │
│ [📷上传][历史][🔍搜索(loading)]             │ 模式▾ TopK     │
│ ─ 历史面板（按 kbId 隔离 ≤50）              │ 相似度 BM25    │
│ ─ 结果区: 计数头 | loading 文案             │ 权重(hybrid)   │
│   ┌ 结果卡片: #序号 [图谱召回|QA][相似度%]   │ 图谱通道 switch │
│   │ previewSnippet(v-html+mark)            │ 关键词+QA召回数 │
│   │ <details>全文 | 溯源链接·块索引·距离     │               │
│   └ …（列表）                               │               │
│ 空态 el-empty                               │               │
└───────────────────────────────────────────┴──────────────┘
```

### PAGE-002 检索设置（六段受控面板）
```
┌───────────────────────────────────┐
│ ① 检索模式 radio                    │
│ ② 基础: TopK/阈值/分数阈值开关+值/hybrid 语义权重 slider(互补展示) │
│ ③ 预处理: 纠错|重写|图谱扩展+深度+最大实体|同义词|多查询改写+变体数 │
│ ④ 重排序: Rerank 开关+模型▾(失败回退硬编码)|LLM 重排|MMR+lambda  │
│ ⑤ 多路召回: 四通道召回数 + 融合策略(RRF/加权/交错)                │
│ ⑥ 上下文组装: 策略 concat/parent_chunk/parent_document/window    │
│    + 窗口 + 最大 Token + 排序                                    │
└───────────────────────────────────┘
```

### PAGE-003 检索日志分析
```
┌───────────────────────────────────────────┐
│ 5 指标卡: 总查询|无结果数|无结果率|平均耗时|平均命中数 │
│ ┌热门查询 Top10─┐ ┌无结果查询 Top10┐（纯列表非图表库）│
│ │ …            │ │ …             │                 │
│ 筛选: KB▾ 状态▾ [查询][重置]                     │
│ 明细表: query|知识库|命中数|最高分|耗时|结果tag|时间 │
│ 分页 10/20/50/100                               │
└───────────────────────────────────────────┘
```

组件判定：三个面板各自单一/双消费者，不再抽象；`RetrievalConfig` 共享类型是三者的真实 seam（数据契约级，非组件级）。

## 3. State Ownership

| STATE | 所有者 | 说明 |
|---|---|---|
| 检索结果/hasSearched | client（页内） | error 清空结果 |
| 检索配置 | PAGE-001/002 各自页内 ref → 保存至 server（KB.retrievalConfig） | 回填=非空字段合并默认值（detail:73-112） |
| 历史记录 | client（localStorage `search-history:${kbId}` ≤50） | seq 从存量恢复 |
| 图片队列 | client（objectURL，释放管理） | 去重键 name+size |
| 扩展缓存 | client（useGraphExpansion Map 进程内） | clearCache 未接 UI |
| 分析数据 | server（logs + analysis 两端点） | 指标逐字段 ??0 兜底 |

## 4. Data Flow

```
预处理管线（SearchTestPanel:199-237，具名顺序）:
  autoCorrect(mock) → rewriteQuery(mock) → graphExpand(真实+缓存，默认关)
  → synonymExpand(真实，静默回退) → searchRetrieval(真实)
保存流: Sidebar config → updateKnowledgeBase（name 必填随带）
命名转换点: highlightFull（转义+降序 mark）；useSynonyms 拼接 `${query} ${added}`；
          分析页指标兜底 `?? 0`
```

## 5. Visual System

结果卡片 `<mark>` 高亮体系；Sidebar sticky 卡片；分析页无图表库（指标卡+列表形态）。断点无。

## 6. Parent Trace

- 预处理管线双轨（mock vs debug 真实 API）→ spec INFERRED-6
- Sidebar（运行时调参+显式保存）vs SettingPanel（表单受控）职责分工 → spec TASK-004/005（共享 RetrievalConfig 类型）
- 溯源跳转契约 → chunk-management 链入口
