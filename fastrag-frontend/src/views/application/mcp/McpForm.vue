<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { AUTH_TYPE_OPTIONS } from '@/mock/mcp'
import type { McpService, McpAuthType } from '@/mock/mcp'

const props = withDefaults(
  defineProps<{
    mode: 'create' | 'edit'
    initialData?: McpService | null
  }>(),
  { initialData: null },
)

const emit = defineEmits<{
  (e: 'submit', data: McpService): void
  (e: 'cancel'): void
}>()

// --- 表单数据 ---
function defaultForm(): McpService {
  return {
    id: '',
    name: '',
    mcpUrl: '',
    authType: 'Bearer',
    authValue: '',
    status: 'online',
    enabled: true,
    toolsList: [],
    lastUsed: '-',
    createdAt: '',
    callLogs: [],
  }
}

const form = reactive<McpService>(defaultForm())

function syncFromProps() {
  Object.assign(form, defaultForm())
  if (props.initialData) {
    form.id = props.initialData.id
    form.name = props.initialData.name
    form.mcpUrl = props.initialData.mcpUrl
    form.authType = props.initialData.authType
    form.authValue = props.initialData.authValue
    form.status = props.initialData.status
    form.enabled = props.initialData.enabled
    form.toolsList = props.initialData.toolsList.map((t) => ({ ...t, params: t.params.map((p) => ({ ...p })) }))
  }
}

watch(
  () => props.initialData,
  () => syncFromProps(),
  { immediate: true },
)

// --- Tab ---
const activeTab = ref('basic')

// --- 解析 MCP 地址 ---
const parsing = ref(false)

async function handleParseUrl() {
  if (!form.mcpUrl.trim()) {
    ElMessage.warning('请先输入 MCP 地址')
    return
  }
  // 无后端解析接口时，提示用户手动维护工具列表
  ElMessage.info('暂不支持自动解析 MCP 地址，请手动添加工具列表')
}

// --- 提交 ---
function validate(): boolean {
  if (!form.name.trim()) {
    ElMessage.warning('请输入服务名称')
    activeTab.value = 'basic'
    return false
  }
  if (!form.mcpUrl.trim()) {
    ElMessage.warning('请输入 MCP 地址')
    activeTab.value = 'basic'
    return false
  }
  return true
}

function handleSubmit() {
  if (!validate()) return
  const payload: McpService = {
    ...form,
    toolsList: form.toolsList.map((t) => ({ ...t, params: t.params.map((p) => ({ ...p })) })),
  }
  emit('submit', payload)
}
</script>

<template>
  <div class="mcp-form-wrap">
    <el-tabs v-model="activeTab" class="form-tabs">
      <!-- 基础信息 -->
      <el-tab-pane label="基础信息" name="basic">
        <el-form label-width="100px" label-position="right">
          <el-form-item label="服务名称" required>
            <el-input v-model="form.name" placeholder="请输入服务名称" maxlength="30" show-word-limit />
          </el-form-item>
          <el-form-item label="鉴权类型">
            <el-radio-group v-model="form.authType">
              <el-radio-button v-for="a in AUTH_TYPE_OPTIONS" :key="a.value" :value="a.value">{{ a.label }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.authType !== 'none'" label="鉴权值" required>
            <el-input v-model="form.authValue" placeholder="Token / API Key" />
          </el-form-item>
          <el-form-item label="MCP 地址" required>
            <div class="mcp-url-input">
              <el-input v-model="form.mcpUrl" placeholder="https://mcp.api-inference.modelscope.net/xxx/mcp" />
              <el-button type="primary" plain :loading="parsing" @click="handleParseUrl">
                <el-icon><Search /></el-icon>解析
              </el-button>
            </div>
          </el-form-item>
          <el-form-item label="启用状态">
            <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 工具列表 -->
      <el-tab-pane :label="`工具列表 (${form.toolsList.length})`" name="tools">
        <div class="tab-section-tip">
          解析 MCP 地址后，服务暴露的工具会回填到此处。可点击「基础信息」中的「解析」按钮重新拉取。
        </div>
        <el-table :data="form.toolsList" size="small" border>
          <el-table-column prop="name" label="工具名称" min-width="160" />
          <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip />
          <el-table-column label="参数数" width="80" align="center">
            <template #default="{ row }">
              {{ row.params?.length || 0 }}
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!form.toolsList.length" description="尚未解析出工具，请先在「基础信息」中解析 MCP 地址" :image-size="60" />
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

.mcp-form-wrap {
  background: $bg-white;
  border-radius: $radius-base;
  border: 1px solid $border-lighter;
  padding: $spacing-lg $spacing-xl;
}

.form-tabs {
  :deep(.el-tabs__content) {
    min-height: 320px;
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

.mcp-url-input {
  display: flex;
  gap: $spacing-sm;
  width: 100%;
}

.tab-section-tip {
  margin-bottom: $spacing-base;
  padding: $spacing-sm $spacing-base;
  font-size: 12px;
  color: $text-secondary;
  background: $bg-hover;
  border-radius: $radius-sm;
}
</style>
