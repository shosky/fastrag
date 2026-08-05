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
