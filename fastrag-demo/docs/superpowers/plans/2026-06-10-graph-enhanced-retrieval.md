# 图谱增强检索实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现图谱增强检索功能，通过查询扩展提升文本检索效果，保持现有UI/UX不变。

**Architecture:** 采用轻量级方案，在现有检索流程中嵌入图谱查询扩展。前端负责调用后端API，后端实现NER、图谱查询和查询扩展逻辑。

**Tech Stack:** Vue 3, TypeScript, Element Plus, Pinia

---

## 文件结构

```
src/
├── types/
│   └── knowledge.ts              # 添加图谱扩展相关类型
├── api/
│   └── index.ts                  # 添加图谱扩展API
├── mock/
│   └── graph-expansion.ts        # 图谱扩展mock数据
├── composables/
│   └── useGraphExpansion.ts      # 图谱扩展逻辑composable
├── views/knowledge/detail/
│   └── components/
│       └── SearchTestPanel.vue   # 修改：集成图谱扩展
```

---

## Task 1: 添加图谱扩展类型定义

**Files:**
- Modify: `src/types/knowledge.ts:58-73`

- [ ] **Step 1: 添加图谱扩展相关类型**

在 `RetrievalSettingConfig` 接口后添加：

```typescript
/** 图谱扩展配置 */
export interface GraphExpansionConfig {
  /** 是否启用图谱扩展 */
  enabled: boolean
  /** 扩展深度（1-2） */
  depth: number
  /** 最大扩展实体数 */
  maxEntities: number
}

/** 图谱实体 */
export interface GraphEntity {
  id: string
  name: string
  type: string
}

/** 图谱关系 */
export interface GraphRelation {
  source: string
  target: string
  label: string
}

/** 图谱扩展结果 */
export interface GraphExpansionResult {
  entities: GraphEntity[]
  relations: GraphRelation[]
  expandedQuery: string
}

/** 扩展后的检索请求 */
export interface ExpandedSearchRequest {
  query: string
  originalQuery: string
  enableGraphExpansion: boolean
  topK: number
  similarityThreshold: number
  mode: 'vector' | 'fulltext' | 'hybrid'
}
```

- [ ] **Step 2: 验证类型定义**

运行TypeScript检查确保类型正确：

```bash
cd "D:\Workspace\java\github\rag\fastrag\fastrag-demo" && npx vue-tsc --noEmit 2>&1 | head -20
```

Expected: 无新增类型错误

- [ ] **Step 3: Commit**

```bash
git add src/types/knowledge.ts
git commit -m "feat: add graph expansion types to knowledge.ts"
```

---

## Task 2: 创建图谱扩展Mock数据

**Files:**
- Create: `src/mock/graph-expansion.ts`

- [ ] **Step 1: 创建mock数据文件**

```typescript
import type { GraphEntity, GraphRelation, GraphExpansionResult } from '@/types/knowledge'

// Mock图谱数据
export const mockGraphData: Record<string, GraphExpansionResult> = {
  '小微ICT': {
    entities: [
      { id: '1', name: '智能组网', type: '服务' },
      { id: '2', name: '机房托管', type: '服务' },
      { id: '3', name: '极速专线', type: '服务' },
      { id: '4', name: '综合布线', type: '服务' },
      { id: '5', name: '视频监控', type: '服务' },
    ],
    relations: [
      { source: '小微ICT', target: '智能组网', label: '提供' },
      { source: '小微ICT', target: '机房托管', label: '提供' },
      { source: '小微ICT', target: '极速专线', label: '提供' },
      { source: '小微ICT', target: '综合布线', label: '提供' },
      { source: '小微ICT', target: '视频监控', label: '提供' },
    ],
    expandedQuery: '小微ICT 智能组网 机房托管 极速专线 综合布线 视频监控',
  },
  'CRM系统': {
    entities: [
      { id: '6', name: '客户管理', type: '功能' },
      { id: '7', name: '销售跟进', type: '功能' },
      { id: '8', name: '数据分析', type: '功能' },
    ],
    relations: [
      { source: 'CRM系统', target: '客户管理', label: '包含' },
      { source: 'CRM系统', target: '销售跟进', label: '支持' },
      { source: 'CRM系统', target: '数据分析', label: '提供' },
    ],
    expandedQuery: 'CRM系统 客户管理 销售跟进 数据分析',
  },
  '政企客户': {
    entities: [
      { id: '9', name: '政府机构', type: '客户类型' },
      { id: '10', name: '大型企业', type: '客户类型' },
      { id: '11', name: '中小企业', type: '客户类型' },
    ],
    relations: [
      { source: '政企客户', target: '政府机构', label: '包括' },
      { source: '政企客户', target: '大型企业', label: '包括' },
      { source: '政企客户', target: '中小企业', label: '包括' },
    ],
    expandedQuery: '政企客户 政府机构 大型企业 中小企业',
  },
}

// 实体识别mock（简单规则匹配）
const knownEntities = ['小微ICT', 'CRM系统', '政企客户', '电信网络', '省客支', '校园客户']

export function mockRecognizeEntities(query: string): string[] {
  const found: string[] = []
  for (const entity of knownEntities) {
    if (query.includes(entity)) {
      found.push(entity)
    }
  }
  return found
}

// 图谱查询mock
export function mockQueryGraph(entity: string): GraphExpansionResult | null {
  return mockGraphData[entity] || null
}
```

- [ ] **Step 2: 验证mock数据**

```bash
cd "D:\Workspace\java\github\rag\fastrag\fastrag-demo" && npx vue-tsc --noEmit 2>&1 | grep "graph-expansion"
```

Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add src/mock/graph-expansion.ts
git commit -m "feat: add graph expansion mock data"
```

---

## Task 3: 创建图谱扩展Composable

**Files:**
- Create: `src/composables/useGraphExpansion.ts`

- [ ] **Step 1: 创建composable文件**

```typescript
import { ref } from 'vue'
import type { GraphExpansionResult } from '@/types/knowledge'
import { mockRecognizeEntities, mockQueryGraph } from '@/mock/graph-expansion'

export function useGraphExpansion() {
  const expansionResult = ref<GraphExpansionResult | null>(null)
  const isExpanding = ref(false)
  const expandedQuery = ref('')

  // 扩展缓存
  const cache = new Map<string, GraphExpansionResult>()

  /**
   * 执行图谱扩展
   * @param query 原始查询
   * @returns 扩展后的查询
   */
  async function expandQuery(query: string): Promise<string> {
    // 检查缓存
    if (cache.has(query)) {
      const cached = cache.get(query)!
      expansionResult.value = cached
      expandedQuery.value = cached.expandedQuery
      return cached.expandedQuery
    }

    isExpanding.value = true

    try {
      // 模拟异步处理
      await new Promise(r => setTimeout(r, 100))

      // 识别实体
      const entities = mockRecognizeEntities(query)

      if (entities.length === 0) {
        // 无实体，返回原始查询
        expandedQuery.value = query
        return query
      }

      // 查询图谱获取关联实体
      const allEntities: string[] = []
      const allRelations: GraphExpansionResult['relations'] = []

      for (const entity of entities) {
        const result = mockQueryGraph(entity)
        if (result) {
          allEntities.push(...result.entities.map(e => e.name))
          allRelations.push(...result.relations)
        }
      }

      // 去重
      const uniqueEntities = [...new Set(allEntities)]

      // 构建扩展查询
      const expanded = uniqueEntities.length > 0
        ? `${query} ${uniqueEntities.join(' ')}`
        : query

      const result: GraphExpansionResult = {
        entities: uniqueEntities.map((name, i) => ({
          id: String(i + 1),
          name,
          type: '扩展实体',
        })),
        relations: allRelations,
        expandedQuery: expanded,
      }

      // 缓存结果
      cache.set(query, result)

      expansionResult.value = result
      expandedQuery.value = expanded

      return expanded
    } finally {
      isExpanding.value = false
    }
  }

  /**
   * 清除缓存
   */
  function clearCache() {
    cache.clear()
  }

  return {
    expandQuery,
    expansionResult,
    isExpanding,
    expandedQuery,
    clearCache,
  }
}
```

- [ ] **Step 2: 验证composable**

```bash
cd "D:\Workspace\java\github\rag\fastrag\fastrag-demo" && npx vue-tsc --noEmit 2>&1 | grep "useGraphExpansion"
```

Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add src/composables/useGraphExpansion.ts
git commit -m "feat: add useGraphExpansion composable"
```

---

## Task 4: 修改SearchTestPanel集成图谱扩展

**Files:**
- Modify: `src/views/knowledge/detail/components/SearchTestPanel.vue:1-30`
- Modify: `src/views/knowledge/detail/components/SearchTestPanel.vue:78-95`

- [ ] **Step 1: 添加导入**

在 `<script setup>` 开头添加：

```typescript
import { useGraphExpansion } from '@/composables/useGraphExpansion'
```

- [ ] **Step 2: 初始化composable**

在 `const emit = defineEmits<...>` 后添加：

```typescript
// 图谱扩展
const { expandQuery, isExpanding, expandedQuery } = useGraphExpansion()
```

- [ ] **Step 3: 修改搜索处理函数**

替换 `handleSearch` 函数：

```typescript
// --- Search handler ---
async function handleSearch() {
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入检索内容')
    return
  }
  searchLoading.value = true
  hasSearched.value = true

  // 执行图谱扩展
  const finalQuery = await expandQuery(searchQuery.value)

  // Simulate API call with expanded query
  await new Promise((r) => setTimeout(r, 800))

  searchResults.value = mockResults.map((r, i) => ({
    ...r,
    index: i + 1,
    // 如果有扩展，稍微提高相关结果的相似度
    similarity: finalQuery !== searchQuery.value
      ? r.similarity + Math.random() * 2
      : r.similarity,
  }))
  searchLoading.value = false
}
```

- [ ] **Step 4: 在搜索输入框显示扩展状态**

在 `<div class="search-test__input-box">` 后添加：

```typescript
// 在template中搜索输入框下方添加扩展状态提示
```

在模板中 `search-test__input-hint` div 后添加：

```html
<div v-if="isExpanding" class="search-test__expanding">
  <el-icon class="is-loading"><Loading /></el-icon>
  <span>正在利用知识图谱扩展查询...</span>
</div>
<div v-else-if="expandedQuery && expandedQuery !== searchQuery" class="search-test__expanded">
  <span class="search-test__expanded-label">扩展查询:</span>
  <span class="search-test__expanded-query">{{ expandedQuery }}</span>
</div>
```

- [ ] **Step 5: 添加扩展状态样式**

在 `<style>` 中 `.search-test__input-hint` 后添加：

```scss
&__expanding {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: 12px;
  color: $color-primary;
  margin-top: $spacing-xs;
}

&__expanded {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: 12px;
  color: $text-secondary;
  margin-top: $spacing-xs;
}

&__expanded-label {
  color: $color-success;
  font-weight: 500;
}

&__expanded-query {
  color: $text-primary;
}
```

- [ ] **Step 6: 验证修改**

```bash
cd "D:\Workspace\java\github\rag\fastrag\fastrag-demo" && npm run build 2>&1 | head -20
```

Expected: 构建成功（仅有预先存在的错误）

- [ ] **Step 7: Commit**

```bash
git add src/views/knowledge/detail/components/SearchTestPanel.vue
git commit -m "feat: integrate graph expansion into SearchTestPanel"
```

---

## Task 5: 添加图谱扩展API接口（前端部分）

**Files:**
- Modify: `src/api/index.ts`

- [ ] **Step 1: 添加图谱扩展API**

```typescript
import { createMockAxios } from '@/mock/interceptor'
import type { GraphExpansionResult } from '@/types/knowledge'
import { mockRecognizeEntities, mockQueryGraph } from '@/mock/graph-expansion'

const request = createMockAxios()

// 图谱扩展API（mock实现）
export async function expandQueryWithGraph(
  knowledgeId: string,
  query: string
): Promise<GraphExpansionResult> {
  // 模拟API延迟
  await new Promise(r => setTimeout(r, 100))

  // 识别实体
  const entities = mockRecognizeEntities(query)

  if (entities.length === 0) {
    return {
      entities: [],
      relations: [],
      expandedQuery: query,
    }
  }

  // 查询图谱
  const allEntities: GraphExpansionResult['entities'] = []
  const allRelations: GraphExpansionResult['relations'] = []

  for (const entity of entities) {
    const result = mockQueryGraph(entity)
    if (result) {
      allEntities.push(...result.entities)
      allRelations.push(...result.relations)
    }
  }

  const uniqueEntities = [...new Set(allEntities.map(e => e.name))]

  return {
    entities: uniqueEntities.map((name, i) => ({
      id: String(i + 1),
      name,
      type: '扩展实体',
    })),
    relations: allRelations,
    expandedQuery: uniqueEntities.length > 0
      ? `${query} ${uniqueEntities.join(' ')}`
      : query,
  }
}

export default request
```

- [ ] **Step 2: 验证API**

```bash
cd "D:\Workspace\java\github\rag\fastrag\fastrag-demo" && npx vue-tsc --noEmit 2>&1 | grep "api/index"
```

Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add src/api/index.ts
git commit -m "feat: add graph expansion API mock"
```

---

## Task 6: 更新useGraphExpansion使用API

**Files:**
- Modify: `src/composables/useGraphExpansion.ts`

- [ ] **Step 1: 更新composable使用API**

替换 `src/composables/useGraphExpansion.ts` 内容：

```typescript
import { ref } from 'vue'
import type { GraphExpansionResult } from '@/types/knowledge'
import { expandQueryWithGraph } from '@/api'

export function useGraphExpansion(knowledgeId: string = '1') {
  const expansionResult = ref<GraphExpansionResult | null>(null)
  const isExpanding = ref(false)
  const expandedQuery = ref('')

  // 扩展缓存
  const cache = new Map<string, GraphExpansionResult>()

  /**
   * 执行图谱扩展
   * @param query 原始查询
   * @returns 扩展后的查询
   */
  async function expandQuery(query: string): Promise<string> {
    // 检查缓存
    if (cache.has(query)) {
      const cached = cache.get(query)!
      expansionResult.value = cached
      expandedQuery.value = cached.expandedQuery
      return cached.expandedQuery
    }

    isExpanding.value = true

    try {
      // 调用API
      const result = await expandQueryWithGraph(knowledgeId, query)

      // 缓存结果
      cache.set(query, result)

      expansionResult.value = result
      expandedQuery.value = result.expandedQuery

      return result.expandedQuery
    } finally {
      isExpanding.value = false
    }
  }

  /**
   * 清除缓存
   */
  function clearCache() {
    cache.clear()
  }

  return {
    expandQuery,
    expansionResult,
    isExpanding,
    expandedQuery,
    clearCache,
  }
}
```

- [ ] **Step 2: 更新SearchTestPanel中的调用**

在 `SearchTestPanel.vue` 中修改初始化：

```typescript
// 图谱扩展
const { expandQuery, isExpanding, expandedQuery } = useGraphExpansion('1') // 使用mock知识库ID
```

- [ ] **Step 3: 验证修改**

```bash
cd "D:\Workspace\java\github\rag\fastrag\fastrag-demo" && npm run build 2>&1 | head -20
```

Expected: 构建成功

- [ ] **Step 4: Commit**

```bash
git add src/composables/useGraphExpansion.ts src/views/knowledge/detail/components/SearchTestPanel.vue
git commit -m "feat: update useGraphExpansion to use API"
```

---

## Task 7: 测试图谱扩展功能

**Files:**
- None (测试步骤)

- [ ] **Step 1: 启动开发服务器**

```bash
cd "D:\Workspace\java\github\rag\fastrag\fastrag-demo" && npm run dev
```

- [ ] **Step 2: 访问检索测试页面**

打开浏览器访问: http://localhost:3000/knowledge/1

点击"检索测试"标签页

- [ ] **Step 3: 测试查询扩展**

在搜索框输入: "小微ICT服务有哪些"

预期结果:
1. 显示"正在利用知识图谱扩展查询..."提示
2. 显示扩展查询: "小微ICT服务有哪些 智能组网 机房托管 极速专线 综合布线 视频监控"
3. 返回增强后的检索结果

- [ ] **Step 4: 测试无实体查询**

在搜索框输入: "如何办理业务"

预期结果:
1. 无扩展提示
2. 返回原始检索结果

- [ ] **Step 5: 测试缓存**

再次输入: "小微ICT服务有哪些"

预期结果:
1. 立即返回结果（无加载提示）
2. 使用缓存的扩展结果

- [ ] **Step 6: 记录测试结果**

记录以下测试结果：
- [ ] 实体识别准确性
- [ ] 图谱扩展效果
- [ ] 缓存命中率
- [ ] 用户体验

---

## Task 8: 优化和完善

**Files:**
- Modify: `src/views/knowledge/detail/components/SearchTestPanel.vue`

- [ ] **Step 1: 优化扩展提示样式**

根据测试结果调整样式，确保用户体验良好。

- [ ] **Step 2: 添加扩展来源显示（可选）**

在搜索结果中显示扩展来源信息：

```html
<div v-if="expansionResult && expansionResult.entities.length > 0" class="search-test__expansion-source">
  <el-tag size="small" type="success">图谱扩展</el-tag>
  <span>基于实体: {{ expansionResult.entities.map(e => e.name).join(', ') }}</span>
</div>
```

- [ ] **Step 3: Commit**

```bash
git add src/views/knowledge/detail/components/SearchTestPanel.vue
git commit -m "feat: optimize graph expansion UI"
```

---

## 完成检查

- [ ] 所有Task已完成
- [ ] TypeScript无新增错误
- [ ] 构建成功
- [ ] 功能测试通过
- [ ] 代码已提交

---

## 后续优化建议

1. **性能优化**
   - 实现真正的NER模型（如使用transformers.js）
   - 优化图谱查询性能
   - 添加请求去重

2. **功能增强**
   - 支持多跳扩展
   - 添加实体类型筛选
   - 支持用户手动选择扩展实体

3. **监控和调试**
   - 添加扩展过程日志
   - 实现扩展效果统计
   - 添加A/B测试支持
