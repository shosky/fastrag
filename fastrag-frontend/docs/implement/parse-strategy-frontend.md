# 解析策略 — Implementation Document（parse-strategy-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/knowledge/detail/
├── parse-strategy.vue                      — PAGE-001（1079 行：列表 + 创建/编辑弹窗 + 五类参数面板）
└── components/StrategyChangeDialog.vue     — PAGE-002 换绑重切对话框（361 行，FileManager 宿主）
src/composables/useParseStrategy.ts         — 列表封装（loading/list/CRUD 转发，87 行）
src/views/knowledge/detail/components/ChunkLengthSelect.vue — 长度预设+自定义混合控件（3+ 消费者）
src/types/knowledge.ts                      — ParseStrategyAdvanced{parse,chunk,index?} + DEFAULT_ADVANCED + 预设常量
src/views/knowledge/detail/components/{FileManager.vue,FileTable.vue,FileUploader.vue} — 宿主/入口（file-ingestion 链）
```

## 2. Interface Implementations

### advanced 双格式归一（contract: Data Mapping 第一行）

**Logic Flow:**
```
normalizeAdvanced(raw):
1. IF raw 含 {parse,chunk,index} 分组 THEN 按组合并
2. ELSE 旧平铺字段映射（chunkLength/overlap/delimiter 单复数/strategy…）
3. RETURN 归一结构（此后全站只消费归一结构）
```

### 表单双向绑定（adv Proxy）

**Logic Flow:**
```
adv = new Proxy({}, {
  get(_, key): advanced.chunk[key]
  set(_, key, val): advanced.chunk[key] = val    // 顶层模板绑定透明落到 chunk 子对象
})
切换策略卡片 → resetChunkParams(): 全部 chunk 参数 = DEFAULT_ADVANCED
提交 → buildPayload(): 剥离 advanced.index（二期占位，后端兜底）→ create/update
```

### 冲突检测双拦截

**Logic Flow:**
```
watch(extensions) → 防抖 300ms → POST conflicts{extensions, excludeId=编辑id} → 内联警告
handleSubmit: 复检 → IF 命中 THEN ElMessageBox 强确认（返回修改 | 仍然保存）
```

### 换绑保存（StrategyChangeDialog）

**Logic Flow:**
```
打开: 基线 = 文件当前生效 parseStrategyAdvanced.chunk（未暴露字段原样继承）
保存: 合并表单 → saveFileStrategy(kbId, fileId, {…chunk, parseMethod:'default', strategyType:'file_id'})
  → toast「已保存分片策略并提交重新分片任务」→ FileManager.load() + startPolling([fileId])
守卫: QA 文件 → 提示不受影响 + 保存禁用；processing → 「正在处理中…」锁死
```

### 页面登记表

| 能力 | 状态归属 | 要点 | 证据 |
|---|---|---|---|
| 列表 | useParseStrategy | 无 catch 静默；无空态；无分页 | useParseStrategy.ts:9-17 |
| meta/模型 | 页内 ref | 失败静默降级 | parse-strategy.vue:63-90 |
| ChunkLengthSelect | 组件内 | 0 值自定义 + `Number(v)\|\|1000` 兜底 | ChunkLengthSelect:22-34 |
| 上传向导策略 | FileUploader | 空串不转发；预览三 Map | FileUploader:385-400,459-493 |

## 3. Decisions & Deviations

- **特征化偏差记录**：创建/更新无 catch（INFERRED-2）；刷新假成功（INFERRED-3）；列表无空态/分页——均按现状入契约，未经裁决不擅改。
- **口径漂移（INFERRED-4）**：StrategyChangeDialog 自带 STRATEGY_OPTIONS（「固定长度」）vs 主页面 CHUNK_STRATEGY_OPTIONS（「固定大小切分」）——契约以主页面为准，对话框文案属待统一项。
- **handleCreate 重置遗漏 llmModel 键**（:253，轻微不一致，clearable 下无实际影响）——登记不修。
- 死代码（INFERRED-8）：`useParseStrategy.detail()`、FileManager `resolveByExtension` 死解构、`previewFileChunks` customConfig 无消费者。

## 4. Test Evidence

tdd 全局 blocked。解阻后优先切片：normalizeAdvanced 双格式、adv Proxy 绑定、冲突防抖+强确认流、换绑基线合并、ChunkLengthSelect 兜底。

## 5. Implementation Trace

```
TASK-001 → PAGE-001 列表 → fetchStrategies → parse-strategy.vue:400-508
TASK-002/002b → 弹窗五面板 → adv Proxy + normalizeAdvanced → :150-208,619-826
TASK-003 → 冲突双拦截 → detectConflicts 防抖 + 提交复检 → :126-139,337-349
TASK-004 → 设默认/删除（fileCount 提示）→ :285-328
TASK-005 → 换绑对话框 → saveFileStrategy 基线合并 → StrategyChangeDialog:141-295
TASK-006 → 上传策略+预览 → FileUploader:385-493
TASK-007 → 恢复默认 → DEFAULT_ADVANCED → :260-265
```
