# 开发约定（fastrag-frontend）

## Parent Trace

[CONTEXT.md](../CONTEXT.md)（领域术语）
[README.md](./README.md)（项目概览 + 技术栈）

## 1. 命名约定

- **组件文件：** PascalCase 为主（`AiChunkPanel.vue`、`FileTable.vue`）；views 页面混用 kebab-case（`role-permissions.vue`、`my-tools.vue`）与 PascalCase（`SkillCreate.vue`）——**同一目录内大小写不一致是已知坑**：技能路由指向 `skill/SkillCreate.vue`，而小写 `skill/create.vue` 是孤儿（CERTAIN）。
- **Composables：** `useXxx.ts`（14 个，全部符合）。
- **Stores：** `useXxxStore`（pinia defineStore）。
- **类型：** `src/types/<domain>.ts` 按域组织（knowledge 771 行 / auth 692 行双大头）。
- **样式类：** BEM——分页容器统一 `${block}__pagination`（18 个视图在用，src/components 中 0 个）。
- **测试：** [GAP] 无测试资产，无命名约定可检。

## 2. 文件与目录组织

```
src/
├── api/index.ts        全部 API 单文件（2074 行，440 次调用）——新增接口加这里，不新建 api 模块
├── views/<domain>/     页面 + 页面私有组件（如 knowledge/detail/components/ 36 件）
├── components/         仅跨域共享组件（布局 4 件 + AI 对话 2 件）——页面私有组件不放这里
├── composables/        可复用逻辑（14 个）
├── stores/             仅全局状态（user/system 2 个）；页面态留在页内 ref
├── types/<domain>.ts   领域类型
├── mock/               历史模拟层（16/31 模块已死代码——不要新增消费）
└── utils/              request/format/storage/constants/chatActions
```

## 3. 正确模式（从现有代码提取）

```typescript
// GOOD: 分页列表统一用 usePagination composable
const { currentPage, pageSize, total, handleCurrentChange, handleSizeChange, reset }
  = usePagination(10)
```
```vue
<!-- GOOD: 分页容器 BEM 命名 + 统一 layout -->
<div class="user-table__pagination">
  <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize"
    :total="total" :page-sizes="[10, 20, 50, 100]"
    layout="total, sizes, prev, pager, next, jumper"
    @current-change="handleCurrentChange" @size-change="handleSizeChange" />
</div>
```
```typescript
// GOOD: SSE 用 fetch 直连并显式注明绕过 axios 的原因（api/index.ts:249-263 先例）
export const aiChunkStreamUrl = (kbId: number, fileId: number) =>
  `/api/kb/${kbId}/files/${fileId}/ai-chunk/stream` // fetch 不走 axios baseURL
```
```vue
<!-- GOOD: 按钮级权限用 v-permission 指令（无权限移除 DOM） -->
<el-button v-permission="'kb:delete'" @click="del(row)">删除</el-button>
```

## 4. 反模式 / 避免

```typescript
// AVOID: 页面内手写 currentPage/pageSize ref（违反根 AGENTS.md 前端分页规范）
const currentPage = ref(1)
const pageSize = ref(10)
```
```typescript
// AVOID: 新代码消费 src/mock/ ——16/31 模块已是零引用死代码；
// 运行时真消费仅剩 3 处（answer-kb/templates、QaPanel getFiles、query-preprocess），属待清理债
import * as api from '@/mock/answer-kb'
```
```vue
<!-- AVOID: SFC 内直接 @use variables.scss ——设计 token 仅经 global.scss 全局注入生效，
     SFC 自行引入不会得到预期变量（当前 0 个 .vue 引入，保持现状，走 global.scss 公共类） -->
<style scoped lang="scss">@use '@/assets/styles/variables' as *;</style>
```
```typescript
// AVOID: 硬编码后端地址——一律走 /api 代理（vite.config.ts:33-38）与 VITE_API_BASE_URL
axios.create({ baseURL: 'http://localhost:8081' })
```

## 5. 错误处理

- 后端业务错误 = **HTTP 200 + body.code**；`utils/request.ts` 响应拦截器统一解包 `{code,data,message}`（:30-59）。
- `code === 401` → 清 storage 跳 `/login`；`403` → 仅提示不登出（:41-54）。
- SSE 流（ai-chunk / app chat）不走 axios，自行处理错误分支（`useAppChatStream` 378 行为先例）。
- 表单校验用 Element Plus rules；确认弹窗统一 `useConfirm`（ElMessageBox Promise 包装）。

## 6. Lint / Format / Typecheck

```bash
npx vue-tsc -b        # 类型检查（npm run build 的第一步；无独立 typecheck script）
# lint: [GAP] 无 ESLint/Prettier 配置
# test: [GAP] 无单测/E2E（playwright-core 仅用于 scripts/oo-plugin-*.mjs 探针）
```

## 验收

- 新代码类型检查通过（`vue-tsc -b` 零错误）。
- 新分页列表遵循第 3 节模式；无新增 mock 消费；无新增孤儿文件。
