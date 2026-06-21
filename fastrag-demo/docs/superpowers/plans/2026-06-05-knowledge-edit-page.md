# 知识库编辑页面实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为知识库管理系统添加编辑功能，支持修改已创建知识库的配置信息

**Architecture:** 复用创建页面的表单结构，提取为通用 form.vue 组件，通过 mode 区分创建/编辑模式

**Tech Stack:** Vue 3 + TypeScript + Element Plus + Vite

---

## 文件结构

### 新增文件
- `src/views/knowledge/form.vue` — 通用表单组件（创建和编辑共用）
- `src/views/knowledge/edit.vue` — 编辑页面入口

### 修改文件
- `src/views/knowledge/create.vue` — 改造为调用 form.vue
- `src/router/routes.ts` — 新增编辑路由
- `src/types/knowledge.ts` — 扩展类型定义

---

## Task 1: 扩展类型定义

**Files:**
- Modify: `src/types/knowledge.ts`

- [ ] **Step 1: 添加文件类型配置接口**

```typescript
/** 文件类型配置 */
export interface FileTypeConfig {
  documents: boolean   // PDF、Word、Excel、PPT
  audio: boolean       // MP3、WAV
  video: boolean       // MP4、AVI
  images: boolean      // JPG、PNG（需要 OCR）
}

/** 知识库表单数据 */
export interface KnowledgeBaseForm {
  name: string
  category: string
  description: string
  tags: string[]
  permission: 'private' | 'team' | 'public'
  embeddingModel: string
  parseMode: 'auto' | 'manual'
  splitMode: 'auto' | 'custom'
  fileTypeConfig: FileTypeConfig
}
```

- [ ] **Step 2: 验证类型定义无语法错误**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vue-tsc --noEmit`

Expected: 无错误输出

- [ ] **Step 3: 提交代码**

```bash
git add src/types/knowledge.ts
git commit -m "feat(types): add FileTypeConfig and KnowledgeBaseForm interfaces"
```

---

## Task 2: 创建通用表单组件

**Files:**
- Create: `src/views/knowledge/form.vue`

- [ ] **Step 1: 创建 form.vue 基础结构**

```vue
<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { KnowledgeBase, KnowledgeBaseForm, FileTypeConfig } from '@/types/knowledge'

const props = defineProps<{
  mode: 'create' | 'edit'
  initialData?: KnowledgeBase
}>()

const emit = defineEmits<{
  submit: [data: KnowledgeBaseForm]
  cancel: []
  delete: []
}>()

const formRef = ref<FormInstance>()
const kbType = ref('general')

const form = reactive<KnowledgeBaseForm>({
  name: '',
  category: '',
  description: '',
  tags: [],
  permission: 'private',
  embeddingModel: 'text-embedding-v4',
  parseMode: 'auto',
  splitMode: 'auto',
  fileTypeConfig: {
    documents: true,
    audio: false,
    video: false,
    images: false,
  },
})

// 编辑模式下预填充数据
watch(() => props.initialData, (data) => {
  if (data && props.mode === 'edit') {
    form.name = data.name
    form.category = data.category
    form.description = data.description
    form.tags = [...data.tags]
    form.embeddingModel = data.embeddingModel
  }
}, { immediate: true })

const tagInput = ref('')

const rules: FormRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
}

const isEditMode = computed(() => props.mode === 'edit')

function handleAddTag() {
  if (tagInput.value && !form.tags.includes(tagInput.value)) {
    form.tags.push(tagInput.value)
    tagInput.value = ''
  }
}

function handleRemoveTag(tag: string) {
  form.tags = form.tags.filter(t => t !== tag)
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', { ...form })
}

function handleCancel() {
  emit('cancel')
}

function handleDelete() {
  emit('delete')
}
</script>
```

- [ ] **Step 2: 添加表单模板**

```vue
<template>
  <div class="page-container">
    <div class="create-header">
      <el-button @click="handleCancel">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <h3>{{ isEditMode ? '编辑知识库' : '创建知识库' }}</h3>
    </div>

    <div class="create-layout">
      <div class="form-panel">
        <el-tabs v-model="kbType" v-if="!isEditMode">
          <el-tab-pane label="通用知识库" name="general" />
          <el-tab-pane label="数据库直连" name="database" />
        </el-tabs>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="margin-top: 20px">
          <!-- 基础信息 -->
          <el-form-item label="知识库名称" prop="name">
            <el-input v-model="form.name" placeholder="请输入知识库名称" />
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
              <el-option label="产品文档" value="产品文档" />
              <el-option label="技术文档" value="技术文档" />
              <el-option label="客户案例" value="客户案例" />
              <el-option label="培训资料" value="培训资料" />
            </el-select>
          </el-form-item>
          <el-form-item label="简介">
            <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入知识库简介" />
          </el-form-item>
          <el-form-item label="标签">
            <div class="tag-input-area">
              <el-tag v-for="tag in form.tags" :key="tag" closable @close="handleRemoveTag(tag)">{{ tag }}</el-tag>
              <el-input
                v-model="tagInput"
                size="small"
                style="width: 120px"
                placeholder="输入标签"
                @keyup.enter="handleAddTag"
              />
            </div>
          </el-form-item>
          <el-form-item label="权限配置">
            <el-radio-group v-model="form.permission">
              <el-radio value="private">私有</el-radio>
              <el-radio value="team">团队共享</el-radio>
              <el-radio value="public">公开</el-radio>
            </el-radio-group>
          </el-form-item>

          <!-- 文件配置（仅编辑模式显示） -->
          <template v-if="isEditMode">
            <el-divider>文件配置</el-divider>
            <el-form-item label="支持的文件类型">
              <div class="file-type-config">
                <el-checkbox v-model="form.fileTypeConfig.documents">文档类（PDF、Word、Excel、PPT）</el-checkbox>
                <el-checkbox v-model="form.fileTypeConfig.audio">音频类（MP3、WAV）</el-checkbox>
                <el-checkbox v-model="form.fileTypeConfig.video">视频类（MP4、AVI）</el-checkbox>
                <el-checkbox v-model="form.fileTypeConfig.images">图片类（JPG、PNG）</el-checkbox>
              </div>
            </el-form-item>
          </template>

          <el-divider>高级配置</el-divider>

          <el-form-item label="嵌入模型">
            <el-select v-model="form.embeddingModel" style="width: 100%" :disabled="isEditMode">
              <el-option label="text-embedding-v4" value="text-embedding-v4" />
              <el-option label="text-embedding-v3" value="text-embedding-v3" />
            </el-select>
            <div v-if="isEditMode" class="form-tip">嵌入模型创建后不可修改</div>
          </el-form-item>
          <el-form-item label="解析配置">
            <el-select v-model="form.parseMode" style="width: 100%">
              <el-option label="自动解析" value="auto" />
              <el-option label="手动配置" value="manual" />
            </el-select>
          </el-form-item>
          <el-form-item label="切分模式">
            <el-select v-model="form.splitMode" style="width: 100%">
              <el-option label="自动切分" value="auto" />
              <el-option label="自定义切分" value="custom" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <!-- 右侧面板 -->
      <div class="side-panel">
        <!-- 创建模式：显示进度 -->
        <div v-if="!isEditMode" class="progress-panel">
          <h4>创建进度</h4>
          <div class="progress-items">
            <div class="progress-item" :class="{ done: form.name }">
              <el-icon><CircleCheck /></el-icon>
              <span>填写知识库名称</span>
            </div>
            <div class="progress-item" :class="{ done: form.category }">
              <el-icon><CircleCheck /></el-icon>
              <span>选择分类</span>
            </div>
            <div class="progress-item" :class="{ done: form.description }">
              <el-icon><CircleCheck /></el-icon>
              <span>填写简介</span>
            </div>
            <div class="progress-item" :class="{ done: form.embeddingModel }">
              <el-icon><CircleCheck /></el-icon>
              <span>配置高级参数</span>
            </div>
          </div>
          <el-button type="primary" style="width: 100%; margin-top: 20px" @click="handleSubmit">
            创建知识库
          </el-button>
        </div>

        <!-- 编辑模式：显示操作区 -->
        <template v-else>
          <div class="action-panel">
            <h4>操作</h4>
            <el-button type="primary" style="width: 100%" @click="handleSubmit">
              保存修改
            </el-button>
            <el-button style="width: 100%; margin-top: 10px" @click="handleCancel">
              取消
            </el-button>
          </div>

          <div class="danger-zone">
            <h4>危险操作</h4>
            <el-button type="danger" style="width: 100%" @click="handleDelete">
              删除知识库
            </el-button>
          </div>

          <div class="info-panel">
            <h4>知识库信息</h4>
            <div class="info-items">
              <div class="info-item">
                <span class="label">创建时间</span>
                <span class="value">{{ initialData?.createdAt || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">创建人</span>
                <span class="value">{{ initialData?.creator || '-' }}</span>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 3: 添加样式**

```vue
<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.create-header {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  margin-bottom: $spacing-lg;
  h3 { margin: 0; }
}

.create-layout {
  display: flex;
  gap: $spacing-lg;
}

.form-panel {
  flex: 1;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
}

.tag-input-area {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  align-items: center;
}

.file-type-config {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.form-tip {
  font-size: 12px;
  color: $text-secondary;
  margin-top: 4px;
}

.side-panel {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: $spacing-base;
}

.progress-panel,
.action-panel,
.danger-zone,
.info-panel {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;

  h4 { margin: 0 0 $spacing-lg; }
}

.danger-zone {
  border: 1px solid #f56c6c;

  h4 { color: #f56c6c; }
}

.progress-items {
  display: flex;
  flex-direction: column;
  gap: $spacing-base;
}

.progress-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-size: 13px;
  color: $text-secondary;

  &.done {
    color: $color-success;
  }
}

.info-items {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.info-item {
  display: flex;
  justify-content: space-between;
  font-size: 13px;

  .label { color: $text-secondary; }
  .value { color: $text-primary; }
}
</style>
```

- [ ] **Step 4: 验证组件无语法错误**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vue-tsc --noEmit`

Expected: 无错误输出

- [ ] **Step 5: 提交代码**

```bash
git add src/views/knowledge/form.vue
git commit -m "feat(knowledge): create reusable form component with create/edit modes"
```

---

## Task 3: 创建编辑页面入口

**Files:**
- Create: `src/views/knowledge/edit.vue`

- [ ] **Step 1: 创建 edit.vue**

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import KnowledgeForm from './form.vue'
import type { KnowledgeBase, KnowledgeBaseForm } from '@/types/knowledge'

const route = useRoute()
const router = useRouter()
const kbId = route.params.id as string

// 模拟数据 - 实际应从 API 获取
const kbData = ref<KnowledgeBase>({
  id: kbId,
  name: '产品知识库',
  description: '包含所有产品文档和操作手册',
  category: '产品文档',
  tags: ['产品', '文档'],
  embeddingModel: 'text-embedding-v4',
  dimension: 1024,
  creator: '管理员',
  createdAt: '2026-01-15',
  usedSize: '1.54 MB',
  totalSize: '5 GB',
  type: '团队',
})

const loading = ref(false)

onMounted(() => {
  // TODO: 调用 API 获取知识库数据
  // kbData.value = await fetchKnowledgeBase(kbId)
})

async function handleSubmit(data: KnowledgeBaseForm) {
  loading.value = true
  try {
    // TODO: 调用更新 API
    console.log('更新知识库:', data)
    ElMessage.success('保存成功')
    router.push(`/knowledge/${kbId}`)
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    loading.value = false
  }
}

function handleCancel() {
  router.push(`/knowledge/${kbId}`)
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm(
      `确定要删除知识库「${kbData.value.name}」吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    // TODO: 调用删除 API
    ElMessage.success('删除成功')
    router.push('/knowledge')
  } catch {
    // 用户取消
  }
}
</script>

<template>
  <KnowledgeForm
    mode="edit"
    :initial-data="kbData"
    @submit="handleSubmit"
    @cancel="handleCancel"
    @delete="handleDelete"
  />
</template>
```

- [ ] **Step 2: 验证组件无语法错误**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vue-tsc --noEmit`

Expected: 无错误输出

- [ ] **Step 3: 提交代码**

```bash
git add src/views/knowledge/edit.vue
git commit -m "feat(knowledge): add edit page entry component"
```

---

## Task 4: 改造创建页面

**Files:**
- Modify: `src/views/knowledge/create.vue`

- [ ] **Step 1: 重构 create.vue 使用 form 组件**

```vue
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import KnowledgeForm from './form.vue'
import type { KnowledgeBaseForm } from '@/types/knowledge'

const router = useRouter()

async function handleSubmit(data: KnowledgeBaseForm) {
  // TODO: 调用创建 API
  console.log('创建知识库:', data)
  ElMessage.success('创建成功')
  router.push('/knowledge')
}

function handleCancel() {
  router.push('/knowledge')
}
</script>

<template>
  <KnowledgeForm
    mode="create"
    @submit="handleSubmit"
    @cancel="handleCancel"
  />
</template>
```

- [ ] **Step 2: 验证组件无语法错误**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vue-tsc --noEmit`

Expected: 无错误输出

- [ ] **Step 3: 提交代码**

```bash
git add src/views/knowledge/create.vue
git commit -m "refactor(knowledge): simplify create page by reusing form component"
```

---

## Task 5: 添加编辑路由

**Files:**
- Modify: `src/router/routes.ts`

- [ ] **Step 1: 在知识库路由区域添加编辑路由**

在 `knowledge/:id` 路由后面添加：

```typescript
{
  path: 'knowledge/:id/edit',
  name: 'KnowledgeEdit',
  component: () => import('@/views/knowledge/edit.vue'),
  meta: { title: '编辑知识库', hidden: true },
},
```

- [ ] **Step 2: 验证路由配置无语法错误**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vue-tsc --noEmit`

Expected: 无错误输出

- [ ] **Step 3: 提交代码**

```bash
git add src/router/routes.ts
git commit -m "feat(router): add knowledge edit route"
```

---

## Task 6: 添加编辑入口按钮

**Files:**
- Modify: `src/views/knowledge/detail/index.vue`

- [ ] **Step 1: 在详情页添加编辑按钮**

在 `.info-right` 区域添加编辑按钮：

```vue
<div class="info-right">
  <el-tag>ID: {{ kbInfo.id }}</el-tag>
  <el-button type="primary" plain @click="goToEdit">
    <el-icon><Edit /></el-icon>编辑
  </el-button>
  <el-button type="primary" plain @click="handleFollow">
    <el-icon><Star /></el-icon>加关注
  </el-button>
</div>
```

- [ ] **Step 2: 添加跳转方法**

在 `<script setup>` 中添加：

```typescript
function goToEdit() {
  router.push(`/knowledge/${kbId}/edit`)
}
```

- [ ] **Step 3: 验证组件无语法错误**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vue-tsc --noEmit`

Expected: 无错误输出

- [ ] **Step 4: 提交代码**

```bash
git add src/views/knowledge/detail/index.vue
git commit -m "feat(knowledge): add edit button to detail page"
```

---

## Task 7: 最终验证

- [ ] **Step 1: 运行 TypeScript 类型检查**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npx vue-tsc --noEmit`

Expected: 无错误输出

- [ ] **Step 2: 运行构建**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npm run build`

Expected: 构建成功

- [ ] **Step 3: 启动开发服务器验证功能**

Run: `cd D:/Workspace/java/github/rag/fastrag/fastrag-demo && npm run dev`

验证：
1. 访问知识库列表页
2. 点击某个知识库进入详情页
3. 点击「编辑」按钮进入编辑页面
4. 验证表单数据正确预填充
5. 修改部分字段并保存
6. 验证跳转回详情页

- [ ] **Step 4: 最终提交**

```bash
git add -A
git commit -m "feat: complete knowledge base edit page implementation"
```

---

## 完成

实现计划已完成。所有任务执行完毕后，知识库编辑功能将可用。

**执行方式选择：**

**1. Subagent-Driven（推荐）** - 每个任务分派独立子代理执行，任务间进行审查，快速迭代

**2. Inline Execution** - 在当前会话中执行任务，批量执行并设置检查点

选择哪种方式？
