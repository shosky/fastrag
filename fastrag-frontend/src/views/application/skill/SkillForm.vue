<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { SKILL_CATEGORIES } from '@/mock/skills'
import type { Skill, SkillDependency, SkillSource, SkillCodeType } from '@/mock/skills'
import * as api from '@/api'

const props = withDefaults(
  defineProps<{
    mode: 'create' | 'edit'
    initialData?: Skill | null
  }>(),
  { initialData: null },
)

const emit = defineEmits<{
  (e: 'submit', data: Skill): void
  (e: 'cancel'): void
}>()

// --- 表单数据 ---
function defaultForm(): Skill {
  return {
    id: '',
    name: '',
    identifier: '',
    description: '',
    icon: '#909399',
    source: 'custom',
    category: 'tool',
    trigger: '',
    content: '',
    codeType: 'python',
    code: `def run(query: str) -> dict:\n    """技能入口函数"""\n    return {"result": query}`,
    inputs: '',
    outputs: '',
    enabled: true,
    recommended: false,
    dependencies: [],
    scopes: [],
    usageCount: 0,
    author: '',
    version: '1.0.0',
    updatedAt: '',
  }
}

const form = reactive<Skill>(defaultForm())

function syncFromProps() {
  Object.assign(form, defaultForm())
  if (props.initialData) {
    form.id = props.initialData.id
    form.name = props.initialData.name
    form.identifier = props.initialData.identifier
    form.description = props.initialData.description
    form.icon = props.initialData.icon
    form.source = props.initialData.source
    form.category = props.initialData.category
    form.trigger = props.initialData.trigger
    form.content = props.initialData.content
    form.codeType = props.initialData.codeType
    form.code = props.initialData.code
    form.inputs = props.initialData.inputs
    form.outputs = props.initialData.outputs
    form.enabled = props.initialData.enabled
    form.recommended = props.initialData.recommended
    form.dependencies = props.initialData.dependencies.map((d) => ({ ...d }))
    form.scopes = props.initialData.scopes.map((sc) => ({ ...sc }))
    form.usageCount = props.initialData.usageCount
    form.author = props.initialData.author
    form.version = props.initialData.version
    form.updatedAt = props.initialData.updatedAt
  }
}

watch(
  () => props.initialData,
  () => syncFromProps(),
  { immediate: true },
)

// --- 常量 ---
const SOURCE_OPTIONS: { label: string; value: SkillSource }[] = [
  { label: '内置技能', value: 'builtin' },
  { label: '自定义技能', value: 'custom' },
  { label: '插件技能', value: 'plugin' },
]
const CODE_TYPE_OPTIONS: { label: string; value: SkillCodeType }[] = [
  { label: 'Python', value: 'python' },
  { label: 'YAML', value: 'yaml' },
  { label: 'JSON', value: 'json' },
  { label: 'Markdown', value: 'markdown' },
]
const DEP_TYPE_LABELS: Record<SkillDependency['type'], string> = {
  tool: '工具',
  model: '模型',
  mcp: 'MCP',
  skill: '技能',
}

// --- Tab ---
const activeTab = ref('basic')

// --- 依赖管理 ---
const depCandidateType = ref<SkillDependency['type']>('tool')
const depCandidateName = ref('')

// 从 API 动态加载候选列表
const dependencyCandidates = ref<{ type: string; id: string; name: string }[]>([])

onMounted(async () => {
  try {
    const [tools, skills, mcps, models] = await Promise.all([
      api.getTools().catch(() => []),
      api.getSkills().catch(() => []),
      api.getMcpServices().catch(() => []),
      api.getModels().catch(() => []),
    ])
    const list: { type: string; id: string; name: string }[] = []
    ;((tools as any)?.list || tools || []).forEach((t: any) => list.push({ type: 'tool', id: t.id, name: t.name }))
    ;((skills as any)?.list || skills || []).forEach((s: any) => list.push({ type: 'skill', id: s.id, name: s.name }))
    ;((mcps as any)?.list || mcps || []).forEach((m: any) => list.push({ type: 'mcp', id: m.id, name: m.name }))
    ;((models as any)?.list || models || []).forEach((m: any) => list.push({ type: 'model', id: m.id, name: m.name }))
    dependencyCandidates.value = list
  } catch {
    // 加载失败保留空列表
  }
})

const candidateOptions = computed(() => {
  if (!depCandidateType.value) return []
  return dependencyCandidates.value.filter((c) => c.type === depCandidateType.value)
})

function addDependency() {
  if (!depCandidateName.value) {
    ElMessage.warning('请选择依赖项')
    return
  }
  const exists = form.dependencies.some((d) => d.name === depCandidateName.value && d.type === depCandidateType.value)
  if (exists) {
    ElMessage.warning('该依赖已存在')
    return
  }
  form.dependencies.push({
    id: String(Date.now()),
    type: depCandidateType.value,
    name: depCandidateName.value,
    required: false,
  })
  depCandidateName.value = ''
}

function removeDependency(id: string) {
  const idx = form.dependencies.findIndex((d) => d.id === id)
  if (idx > -1) form.dependencies.splice(idx, 1)
}

// --- 生效范围 ---
const scopeAddId = ref('')

// 从 API 加载范围候选（应用列表）
const scopeCandidates = ref<{ id: string; name: string }[]>([])

onMounted(async () => {
  try {
    const apps: any = await api.getApps()
    scopeCandidates.value = ((apps?.list || apps || []) as any[]).map((a) => ({ id: a.id, name: a.name }))
  } catch {
    scopeCandidates.value = []
  }
})

const availableScopeCandidates = computed(() => {
  return scopeCandidates.value.filter((c) => !form.scopes.some((s) => s.id === c.id))
})

function addScopeBinding(id: string) {
  if (!id) return
  if (form.scopes.some((s) => s.id === id)) {
    ElMessage.warning('该范围已添加')
    return
  }
  const candidate = scopeCandidates.value.find((c) => c.id === id)
  if (!candidate) return
  form.scopes.push({ id: candidate.id, name: candidate.name, enabled: true })
}

function removeScopeBinding(id: string) {
  const idx = form.scopes.findIndex((s) => s.id === id)
  if (idx > -1) form.scopes.splice(idx, 1)
}

// --- 提交 ---
function validate(): boolean {
  if (!form.name.trim()) {
    ElMessage.warning('请输入技能名称')
    activeTab.value = 'basic'
    return false
  }
  if (!form.identifier.trim()) {
    ElMessage.warning('请输入技能标识')
    activeTab.value = 'basic'
    return false
  }
  if (!form.description.trim()) {
    ElMessage.warning('请输入技能描述')
    activeTab.value = 'basic'
    return false
  }
  return true
}

function handleSubmit() {
  if (!validate()) return
  const payload: Skill = {
    ...form,
    dependencies: form.dependencies.map((d) => ({ ...d })),
    scopes: form.scopes.map((sc) => ({ ...sc })),
  }
  emit('submit', payload)
}
</script>

<template>
  <div class="skill-form-wrap">
    <el-tabs v-model="activeTab" class="form-tabs">
      <!-- 基础信息 -->
      <el-tab-pane label="基础信息" name="basic">
        <el-form label-width="100px" label-position="right">
          <el-form-item label="技能名称" required>
            <el-input v-model="form.name" placeholder="请输入技能名称" maxlength="30" show-word-limit />
          </el-form-item>
          <el-form-item label="技能标识" required>
            <el-input v-model="form.identifier" placeholder="如 web_search" />
          </el-form-item>
          <el-form-item label="技能描述" required>
            <el-input v-model="form.description" type="textarea" :rows="2" placeholder="简要描述技能用途" />
          </el-form-item>
          <el-form-item label="技能来源">
            <el-radio-group v-model="form.source">
              <el-radio-button v-for="s in SOURCE_OPTIONS" :key="s.value" :value="s.value">{{ s.label }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="技能分类">
            <el-select v-model="form.category" style="width: 100%">
              <el-option
                v-for="c in SKILL_CATEGORIES.filter((x) => x.value)"
                :key="c.value"
                :label="c.label"
                :value="c.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="触发场景">
            <el-input v-model="form.trigger" type="textarea" :rows="2" placeholder="描述何时触发此技能" />
          </el-form-item>
          <el-form-item label="技能说明">
            <el-input v-model="form.content" type="textarea" :rows="3" placeholder="技能执行的具体说明 / 提示词" />
          </el-form-item>
          <el-form-item label="入参示例">
            <el-input v-model="form.inputs" placeholder='{"query": "string"}' />
          </el-form-item>
          <el-form-item label="出参示例">
            <el-input v-model="form.outputs" placeholder='{"result": "string"}' />
          </el-form-item>
          <el-form-item label="作者">
            <el-input v-model="form.author" placeholder="请输入作者" />
          </el-form-item>
          <el-form-item label="版本号">
            <el-input v-model="form.version" placeholder="如 1.0.0" style="width: 200px" />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 代码管理 -->
      <el-tab-pane label="代码管理" name="code">
        <el-form label-width="100px" label-position="right">
          <el-form-item label="代码类型">
            <el-select v-model="form.codeType" style="width: 200px">
              <el-option v-for="t in CODE_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="技能代码">
            <el-input
              v-model="form.code"
              type="textarea"
              :rows="16"
              placeholder="编写技能的实现代码"
              class="code-textarea"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 生效范围 -->
      <el-tab-pane label="生效范围" name="scope">
        <div class="tab-section-tip">
          配置技能在哪些应用 / 知识库下生效。空表示仅全局配置可见，需在应用中显式启用。
        </div>
        <div class="scope-add">
          <el-select
            v-model="scopeAddId"
            placeholder="选择要添加的应用 / 知识库"
            style="width: 320px"
            :disabled="!availableScopeCandidates.length"
          >
            <el-option
              v-for="c in availableScopeCandidates"
              :key="c.id"
              :label="c.name + '（应用）'"
              :value="c.id"
            />
          </el-select>
          <el-button type="primary" plain :disabled="!scopeAddId" @click="addScopeBinding(scopeAddId); scopeAddId = ''">
            <el-icon><Plus /></el-icon>添加
          </el-button>
        </div>
        <el-table :data="form.scopes" size="small" border>
          <el-table-column prop="name" label="应用 / 知识库" />
          <el-table-column label="启用" width="100" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="removeScopeBinding(row.id)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!form.scopes.length" description="尚未添加生效范围" :image-size="60" />
      </el-tab-pane>

      <!-- 依赖管理 -->
      <el-tab-pane label="依赖管理" name="deps">
        <div class="tab-section-tip">
          配置技能运行所依赖的工具、模型、MCP 或其他技能。
        </div>
        <div class="dep-add">
          <el-select v-model="depCandidateType" style="width: 120px">
            <el-option label="工具" value="tool" />
            <el-option label="模型" value="model" />
            <el-option label="MCP" value="mcp" />
            <el-option label="技能" value="skill" />
          </el-select>
          <el-select
            v-model="depCandidateName"
            placeholder="选择依赖项"
            style="width: 240px"
            filterable
          >
            <el-option v-for="c in candidateOptions" :key="c.name" :label="c.name" :value="c.name" />
          </el-select>
          <el-button type="primary" plain :disabled="!depCandidateName" @click="addDependency">
            <el-icon><Plus /></el-icon>添加
          </el-button>
        </div>
        <el-table :data="form.dependencies" size="small" border>
          <el-table-column prop="name" label="依赖名称" />
          <el-table-column label="类型" width="100" align="center">
            <template #default="{ row }">
              <span class="dep-type-text">{{ DEP_TYPE_LABELS[row.type as SkillDependency['type']] }}</span>
            </template>
          </el-table-column>
          <el-table-column label="是否必选" width="120" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.required" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="removeDependency(row.id)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!form.dependencies.length" description="尚未添加依赖" :image-size="60" />
      </el-tab-pane>
    </el-tabs>

    <div class="form-footer">
      <el-button @click="emit('cancel')">取消</el-button>
      <el-button type="primary" @click="handleSubmit">
        {{ mode === 'create' ? '创建' : '保存' }}
      </el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.skill-form-wrap {
  background: $bg-white;
  border-radius: $radius-base;
  border: 1px solid $border-lighter;
  padding: $spacing-lg $spacing-xl;
}

.form-tabs {
  :deep(.el-tabs__content) {
    min-height: 360px;
  }
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: $spacing-sm;
  margin-top: $spacing-lg;
  padding-top: $spacing-base;
  border-top: 1px solid $border-lighter;
}

.tab-section-tip {
  margin-bottom: $spacing-base;
  padding: $spacing-sm $spacing-base;
  font-size: 12px;
  color: $text-secondary;
  background: $bg-hover;
  border-radius: $radius-sm;
}

.scope-add,
.dep-add {
  display: flex;
  gap: $spacing-sm;
  margin-bottom: $spacing-base;
  align-items: center;
}

.dep-type-text {
  font-size: 12px;
  color: $text-secondary;
}

:deep(.code-textarea .el-textarea__inner) {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
}
</style>
