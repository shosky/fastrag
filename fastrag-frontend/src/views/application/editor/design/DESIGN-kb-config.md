# 知识库配置 — 设计文档

## 1. 功能概述

**目的：** 为应用绑定知识库（KB），使 AI 能够基于知识库内容回答问题。

**当前状态：** 现有 `KnowledgeConfig.vue` 有完整 UI（分类筛选、搜索、全选、导入导出），但所有数据为前端 mock，`handleSaveKB()` 仅弹 toast 未对接 API。

**改造目标：** 保留 UI 骨架，将数据源从 mock 替换为真实 API 调用。

---

## 2. 数据模型

### 2.1 API 端点

| 方法 | 端点 | 说明 | 状态 |
|------|------|------|------|
| GET | `/api/knowledge-bases` | 获取全量知识库列表（含分类） | ✅ 已实现 |
| GET | `/api/apps/{appId}/knowledge-bases` | 获取应用已绑定的知识库 | ✅ 已实现 |
| POST | `/api/apps/{appId}/knowledge-bases` | 绑定知识库 | ✅ 已实现 |
| DELETE | `/api/apps/{appId}/knowledge-bases/{id}` | 解绑知识库 | ✅ 已实现 |

### 2.2 请求/响应格式

**GET /api/knowledge-bases 响应：**
```json
[
  {
    "id": "kb_001",
    "name": "产品知识库",
    "category": "product",
    "embeddingModel": "text-embedding-v4",
    "dimension": 1024
  }
]
```

**GET /api/apps/{appId}/knowledge-bases 响应：**
```json
[
  {
    "id": "binding_001",
    "kbId": "kb_001",
    "kbName": "产品知识库",
    "priority": 1,
    "enabled": true
  }
]
```

**POST /api/apps/{appId}/knowledge-bases 请求体：**
```json
{
  "kbId": "kb_001",
  "priority": 1
}
```

### 2.3 本地状态定义

```typescript
// 全量知识库列表（从 API 加载）
const allKbs = ref<KnowledgeBase[]>([])

// 已绑定的知识库 ID 集合（用于勾选状态）
const boundKbIds = ref<Set<string>>(new Set())

// 分类列表（从全量知识库中推导，或独立 API）
const categories = ref<Category[]>([])

// UI 状态
const kbSearchKeyword = ref('')
const categorySearchKeyword = ref('')
const selectedCategory = ref('all')
const bindPersonalKB = ref('no')
const loading = ref(false)
```

### 2.4 数据流

```
onMounted
  ├─ loadAllKbs()        → GET /api/knowledge-bases
  └─ loadBoundKbs()      → GET /api/apps/{id}/knowledge-bases
       └─ 合并 → 标记已绑定知识库的选中状态

handleSaveKB()
  ├─ 遍历当前勾选 vs 已绑定 → 计算差异
  ├─ 新增勾选 → POST /api/apps/{id}/knowledge-bases
  └─ 取消勾选 → DELETE /api/apps/{id}/knowledge-bases/{id}
```

---

## 3. 组件设计

### 3.1 Props

```typescript
defineProps<{ appInfo: { id: string } }>()
```

### 3.2 Emits

无

### 3.3 生命周期

```typescript
onMounted(() => {
  loadAllKbs()
  loadBoundKbs()
})
```

### 3.4 内部方法

| 方法 | 说明 | API 调用 |
|------|------|----------|
| `loadAllKbs()` | 加载全量知识库 | `GET /api/knowledge-bases` |
| `loadBoundKbs()` | 加载已绑定知识库 | `getAppKbBindings` |
| `handleSaveKB()` | 保存绑定变更（差量同步） | `bindAppKb` / `unbindAppKb` |
| `handleExportKB()` | 导出当前绑定为 JSON | 前端构造 |
| `handleImportKB()` | 从 JSON 导入绑定 | 前端解析 + `bindAppKb` |
| `handleSelectAll()` | 全选/取消全选 | 前端操作 |

---

## 4. UI 布局

```
┌─ 知识库配置 ──────────────────────────────────────┐
│  指定知识库检索: [开关]                            │
│  绑定个人知识库: [是] [否]                         │
│                                                    │
│  ┌──────────────┬────────────────────────────────┐ │
│  │ 搜索分类      │ 搜索知识库         [全选] 已选3 │ │
│  │ [_________]  │ [_________________]           │ │
│  │              │                                │ │
│  │ 全部         │ ☑ 产品知识库                    │ │
│  │ 市场运营     │    text-embedding-v4 / 1024    │ │
│  │ 项目管理     │ ☐ 企业资质管理                  │ │
│  │ 产品研发     │    bge-m3 / 1024               │ │
│  │ ...          │ ...                            │ │
│  └──────────────┴────────────────────────────────┘ │
│                                                    │
│  [保 存] [导出配置] [导入配置]                      │
└────────────────────────────────────────────────────┘
```

---

## 5. 实现要点

### 5.1 差量同步策略

`handleSaveKB()` 不应全量替换绑定，而是计算差量：

```
当前勾选: [A, B, C]
已绑定:   [A, D]
需要新增: [B, C]  → POST
需要删除: [D]     → DELETE
```

### 5.2 边界条件
- 全量知识库为空 → 显示空状态提示
- 保存时网络失败 → 回滚勾选状态，提示用户重试
- 批量操作中间失败 → 记录失败项，提示用户

### 5.3 错误处理
- 加载失败 → 显示空列表 + 重试按钮
- 保存失败 → `ElMessage.error('保存失败，请重试')`

### 5.4 性能考虑
- 知识库数量可能较大（100+），差量计算避免不必要 API 调用
- 保存按钮应加 loading 状态，防止重复提交
