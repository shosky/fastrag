# 解析策略 — Frontend Architecture Design（parse-strategy-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/parse-strategy-frontend.md`。

## 1. Page Map

| PAGE | 路由/宿主 | 权限 | 入口 | 退出 |
|---|---|---|---|---|
| PAGE-001 策略管理 | `/knowledge/:id/parse-strategy` | 入口钮 `kb:manage_strategy`（路由无守卫，INFERRED-1） | 详情页按钮；form.vue:602 | 返回 → 知识库详情 |
| PAGE-002 换绑对话框 | FileManager 宿主 | 策略 tag 可点性 gating（QA 不可点） | FileTable 策略 tag | 关闭/保存后随轮询刷新 |
| PAGE-003 上传向导策略步骤 | FileUploader 宿主 | 随上传权限 | 上传向导 | 向导流内 |

## 2. Page/Layout/Component Map

### PAGE-001 策略管理页
```
┌────────────────────────────────────────────────────────┐
│ [←] 解析策略管理                       [创建策略]         │
│ 🔍 名称搜索（客户端过滤）                        (刷新↻)  │
├────────────────────────────────────────────────────────┤
│ 表格: 名称[默认tag]|描述|扩展名tags|文档类型|分片策略|更新时间│
│       操作: 编辑 [设默认][删除](非默认行)                  │
│ 表头 tooltip: 覆盖链 上传参数 > 选中策略 > KB默认 > 系统默认│
├────────────────────────────────────────────────────────┤
│ 创建/编辑 el-dialog(800px)                               │
│  名称* 描述                                              │
│  文档类型▾（meta 驱动）  扩展名多选（联动+冲突内联警告）      │
│  ┌──────────────────────────────────────────────┐      │
│  │ 分片策略 5 卡片（固定/递归/结构/语义/父子）        │      │
│  │ 参数面板（随策略切换）                           │      │
│  │  fixed: 长度预设+分隔符+重叠(0-500)              │      │
│  │  recursive: 最大块+递归分隔符+重叠                │      │
│  │  structure: 单块最大+titlePrefix+headingPath     │      │
│  │  semantic: Embedding▾+阈值滑条+单块最大           │      │
│  │  parent_child: 子片长度[100,200,500]+父片最大     │      │
│  │    (500-10000)+聚合层级 H1/H2/H3/auto            │      │
│  └──────────────────────────────────────────────┘      │
│  ▸ 高级参数: LLM▾ | 表格模式(pdf/doc/docx/xlsx)           │
│    | 视频关键帧(video) | 图片提示 | [恢复默认]             │
│  footer: [取消] [创建|更新]                               │
└────────────────────────────────────────────────────────┘
```

### PAGE-002 StrategyChangeDialog（FileManager 宿主）
```
┌──────────────────────────────────────────┐
│ ⚠ el-alert 常驻: 保存后将按新配置重新分片    │
│   （删除分片与向量、重新向量化；原文件与     │
│    QA 对保留）                             │
│ 处理中文件: 「正在处理中…」锁死              │
│ QA 文件: 「不受分片策略影响」保存禁用        │
│ 策略卡片 + 参数面板（基线=当前生效 chunk）   │
│ footer: [取消] [保存并重新分片](loading)    │
└──────────────────────────────────────────┘
```

组件判定：`ChunkLengthSelect`（3+ 消费者：策略页 ×5 处、换绑对话框，成立）；`adv` Proxy 与 `normalizeAdvanced` 为页内具名转换（单消费者不抽象，implement 承接）。

## 3. State Ownership

| STATE | 所有者 | 说明 |
|---|---|---|
| 策略列表 | server → useParseStrategy（loading/list 封装） | 全量无分页 |
| meta/模型/模板 | server → 页内 ref（失败静默降级） | 选项源 |
| 表单模型 + advanced | form（el-form + `adv` Proxy 双向绑定 chunk 子对象） | 切卡片重置 DEFAULT |
| 冲突结果 | client（防抖 300ms 异步） | 提交前复检 |
| 高级折叠 | client（advancedCollapsed） | — |
| 换绑表单 | dialog form（基线合并自文件当前生效参数） | 提交 → 重切轮询归 FileManager |
| 预览三 Map | client（per-file loading/error/data） | FileUploader |

## 4. Data Flow

```
api strategy 组 → request 解包 → useParseStrategy（列表封装）→ 页面
页面内具名转换：
  normalizeAdvanced(:178-208)     — 新旧 advanced 双格式归一（唯一入口）
  adv Proxy(:150-170)             — chunk 子对象 ↔ 表单顶层绑定
  handleMethodChange(:105-108)    — 类型 → 扩展名集重置
  resetChunkParams(:229-242)      — 换卡片 → DEFAULT
  buildPayload(:351-360)          — 剥离 advanced.index 后提交
StrategyChangeDialog：
  基线合并（当前生效 advanced.chunk ↔ 表单暴露字段）→ saveFileStrategy(file_id 专属)
```

规则：advanced 双格式兼容只存在于 normalizeAdvanced 一处；其余代码只消费归一后结构。

## 5. Visual System

- 策略卡片选择器（5 卡，末卡跨两列）；参数面板条件渲染；扩展名 tag 流式布局 + 内联警告条。
- 换绑对话框常驻 el-alert（warning 级）为重切告知的**唯一**UI 形态（无二次 confirm——与列表删除的 ElMessageBox 风格不同，属有意选择）。
- token/断点：全站一致（无响应式）。

## 6. Parent Trace

- 覆盖链 tooltip → spec TASK-001（绑定优先级领域规则的可视化）
- 换绑常驻警告 + 隐藏专属记录 → spec TASK-005（ADR-0001 前端落点；与 chunk-management 链 reChunk confirm 文案互补）
- 策略卡片切换重置 → spec TASK-002（避免跨策略脏参数）
- 死代码/口径漂移 → spec INFERRED-4/8
