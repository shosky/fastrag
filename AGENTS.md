# FastRAG（AIS 智能知识服务平台）

## IDD 文档体系

本项目使用 IDD（Inventory-Driven Documentation）管理文档，文档位于 `docs/`：

| 阶段 | 位置 | 用途 |
|------|------|------|
| 总览 | `docs/README.md` | 项目概览 + 文档索引 |
| 特性地图 | `docs/feature-map.md` | 18 项特性聚类、证据包、链状态（链进度以 `Chain status` 字段为准） |
| 规范 | `docs/conventions.md` | 开发约定（命名、模式、反模式） |
| 术语 | `CONTEXT.md` | 领域术语表 + IDD 元数据（Role: backend+frontend） |
| Spec | `docs/spec/` | 需求（Given-When-Then） |
| Design | `docs/design/` | 架构 + ADR |
| Contract | `docs/contract/` | 模块接口 + 数据模式 |
| TDD | `tests/`（代码） | 红-绿-重构，可追溯到 contract |
| Implement | `docs/implement/` | 实现记录 |
| Legacy | `docs/legacy/` | IDD 之前的原始文档（ADR、设计、研究、手册，2026-09-16 归档） |

项目宪法另见 `AGENT.md`（架构约定、存储选型、多模态管线、对话约定），与本章互为补充、两者都须遵守。

## 关键技能

- **`/idd-grill`** — 对齐需求、维护领域术语表；任何新工作先做这个。
- **`/idd-autopilot`** — 「下一步做什么」：扫描文档并推荐下一步。
- **`/idd-spec`** — 以 GWT 格式编写/更新需求。
- **`/idd-design`** — 定义模块拓扑、接缝与 ADR。
- **`/idd-contract`** — 定义接口签名与数据模式；实现之前必须完成。
- **`/idd-tdd`** — 以红-绿-重构驱动 contract 接口的测试。
- **`/idd-implement`** — 编写实现文档，描述 contract 接口如何落地。
- **`/idd-diagnose`** — 有反馈回路的纪律化调试。
- **`/idd-review`** — 文档一致性抽查。
- **`/idd-onboard`** — 重新运行以从代码刷新文档链。

## 变更如何流转

需求变更时，先更新 spec，再向下重新 grill 各层；不要机械级联——同一设计可能同时满足新旧需求。

- 新特性：`/idd-grill → /idd-spec → /idd-design → /idd-contract → /idd-tdd → /idd-implement`
- 需求变更：更新 spec → 逐层向下重新 grill
- 缺陷：`/idd-diagnose` → 若属架构根因则 `/idd-design` → `/idd-tdd` 补回归测试

## 领域语言

命名任何东西之前先读 `CONTEXT.md`。类名、函数名、文件名、测试名必须使用术语表中的词；术语表中标注「避免」的同义词不得使用。注意后端统一返回是 `ApiResponse`（不是 Result/R），分页是 `PageResult{list,total,page,pageSize}`。

## 回答置信度分层（IDD 链工作约定）

从代码回答问题时使用三层标注：**CERTAIN**（代码直接可观测，引 file:line）、**INFERRED**（结构强推断，阶段末需用户确认，否决则降级 GAP）、**GAP**（代码外信息，文档内以内联 `[GAP: ...]` 占位，绝不臆造）。

---

# FastRAG 前端规范

## 分页组件（el-pagination）统一风格

### 1. 统一模板

所有带服务端分页的列表页面必须使用以下统一模板：

```vue
<div class="[block]__pagination">   
  <el-pagination
    v-model:current-page="currentPage"
    v-model:page-size="pageSize"
    :total="total"
    :page-sizes="[10, 20, 50, 100]"
    layout="total, sizes, prev, pager, next, jumper"
    @current-change="handleCurrentChange"
    @size-change="handleSizeChange"
  />
</div>
```

**要点：**
- `page-sizes` **始终**使用 `[10, 20, 50, 100]`
- `layout` **始终**使用 `"total, sizes, prev, pager, next, jumper"`
- 外层 `<div>` 使用 **BEM 风格** 类名：`${block}__pagination`
- 外层 `<div>` 必须带样式：`display: flex; justify-content: flex-end; margin-top: 16px;`

### 2. 统一样式

所有分页容器共享以下 SCSS：

```scss
// 直接从此处复制，不要单独写不同实现
&__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
```

如果组件是 `.vue` 单文件，写在 `<style scoped lang="scss">` 块内。

### 3. 统一状态管理

必须使用共享 composable `@/composables/usePagination`：

```typescript
import { usePagination } from '@/composables/usePagination'

const {
  currentPage,  // Ref<number>，当前页码
  pageSize,     // Ref<number>，每页条数（默认 10）
  total,        // Ref<number>，总条数
  handleCurrentChange,  // (page: number) => void
  handleSizeChange,     // (size: number) => void
  reset,        // () => void，重置到第一页
} = usePagination(10)
```

**禁止：**
- ❌ 禁止在页面内手写 `ref(1)` 定义 `currentPage` / `pageSize`
- ❌ 禁止直接写 `:page-size="pageSize"` + `:current-page="page"` 非 v-model 方式（除非 Element Plus 版本限制）

### 4. 约定：列表数据加载

```typescript
// 页码变化时重新拉取
function handleCurrentChange(page: number) {
  currentPage.value = page
  fetchList()
}

// 每页条数变化时重置到第一页并拉取
function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  fetchList()
}
```

### 5. 前端分页 vs 服务端分页

| 场景 | 使用方式 | 条件判断 |
|------|----------|----------|
| **服务端分页**（大部分） | 完整模板，传入后端返回的 total | 直接显示，`total` 由接口返回 |
| **前端分页**（少量） | 完整模板，`total` 设为 `dataList.length` | 加 `v-if="dataList.length > pageSize"` |

### 6. 示例对比（正确 vs 错误）

```vue
<!-- ✅ 正确：统一风格 -->
<div class="user-table__pagination">
  <el-pagination
    v-model:current-page="currentPage"
    v-model:page-size="pageSize"
    :total="total"
    :page-sizes="[10, 20, 50, 100]"
    layout="total, sizes, prev, pager, next, jumper"
    @current-change="handleCurrentChange"
    @size-change="handleSizeChange"
  />
</div>

<!-- ❌ 错误：缺少 jumper -->
<el-pagination layout="total, sizes, prev, pager, next" ... />

<!-- ❌ 错误：page-sizes 缺 100 -->
<el-pagination :page-sizes="[10, 20, 50]" ... />

<!-- ❌ 错误：缺少外层 BEM div -->
<el-pagination ... />

<!-- ❌ 错误：手写 ref 而非用 composable -->
const currentPage = ref(1)
const pageSize = ref(10)
```
