<script setup lang="ts">
import { Plus, Delete, WarningFilled, RefreshRight, CopyDocument } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'
import type { ApiToken } from '@/data/api-docs/types'

// --- State ---
const tokens = ref<ApiToken[]>([])
const loading = ref(false)
const creating = ref(false)

// Create form
const showCreateDialog = ref(false)
const createForm = ref({
  name: '',
  permission: 'read' as 'read' | 'write',
  expiresIn: 86400,
  expiresEnabled: true,
})
const newTokenValue = ref('')
const showNewToken = ref(false)

// --- Load tokens ---
async function loadTokens() {
  loading.value = true
  try {
    const res = await api.getApiTokens()
    tokens.value = Array.isArray(res) ? res : (res as any)?.records || (res as any)?.list || []
  } catch {
    // Fallback: show empty
    tokens.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadTokens)

// --- Create token ---
async function handleCreate() {
  if (!createForm.value.name.trim()) {
    ElMessage.warning('请输入 Token 名称')
    return
  }
  creating.value = true
  try {
    const payload: Record<string, any> = {
      name: createForm.value.name,
      permission: createForm.value.permission,
    }
    if (createForm.value.expiresEnabled) {
      payload.expiresIn = createForm.value.expiresIn
    } else {
      payload.expiresIn = null
    }
    const res = await api.createApiToken(payload)
    newTokenValue.value = (res as any)?.token || ''
    showNewToken.value = true
    showCreateDialog.value = false
    createForm.value = { name: '', permission: 'read', expiresIn: 86400, expiresEnabled: true }
    ElMessage.success('Token 创建成功')
    loadTokens()
  } catch {
    ElMessage.error('Token 创建失败')
  } finally {
    creating.value = false
  }
}

// --- Delete token ---
async function handleDelete(token: ApiToken) {
  try {
    await ElMessageBox.confirm(
      `确定要撤销 Token "${token.name}" 吗？撤销后使用该 Token 的请求将返回 401 错误。`,
      '撤销确认',
      { type: 'warning', confirmButtonText: '确定撤销', cancelButtonText: '取消' }
    )
    await api.deleteApiToken(token.id)
    ElMessage.success('Token 已撤销')
    loadTokens()
  } catch {
    // User cancelled
  }
}

// --- Copy new token ---
async function copyNewToken() {
  try {
    await navigator.clipboard.writeText(newTokenValue.value)
    ElMessage.success('Token 已复制，请妥善保管')
  } catch {
    ElMessage.error('复制失败')
  }
}

// --- Format date ---
function formatDate(dateStr: string | null): string {
  if (!dateStr) return '永不过期'
  try {
    return new Date(dateStr).toLocaleString('zh-CN')
  } catch {
    return dateStr
  }
}

function expiresInText(): string {
  if (!createForm.value.expiresEnabled) return '永不过期'
  const sec = createForm.value.expiresIn
  if (sec >= 86400 * 30) return `${Math.round(sec / 86400 / 30)} 个月`
  if (sec >= 86400) return `${Math.round(sec / 86400)} 天`
  return `${Math.round(sec / 3600)} 小时`
}

// Emit token for parent usage
defineEmits<{
  'token-update': [token: string]
}>()
</script>

<template>
  <!-- 仅 API Token 管理权限（kb:manage，kb_admin 及以上）可见 -->
  <div v-permission="'kb:manage'" class="api-token-manager">
    <!-- Header -->
    <div class="api-token-manager__header">
      <div class="api-token-manager__header-left">
        <span class="api-token-manager__title">API Token</span>
        <span class="api-token-manager__subtitle">用于程序化访问知识库接口</span>
      </div>
      <el-button type="primary" :icon="Plus" @click="showCreateDialog = true">
        创建 Token
      </el-button>
    </div>

    <!-- Usage hint -->
    <div class="api-token-manager__hint">
      <el-icon :size="14"><WarningFilled /></el-icon>
      <span>请在请求头中添加 <code>Authorization: Bearer &lt;your-api-token&gt;</code> 进行认证</span>
    </div>

    <!-- Token list -->
    <div v-loading="loading" class="api-token-manager__list">
      <el-empty v-if="tokens.length === 0 && !loading" description="暂无 API Token，请点击上方按钮创建">
        <template #image>
          <el-icon :size="48" color="#D1D5DB"><WarningFilled /></el-icon>
        </template>
      </el-empty>

      <div v-for="token in tokens" :key="token.id" class="api-token-manager__item">
        <div class="api-token-manager__item-info">
          <div class="api-token-manager__item-name">
            {{ token.name }}
            <el-tag v-if="token.expired" type="info" size="small" style="margin-left: 8px">已过期</el-tag>
            <el-tag v-else :type="token.permission === 'write' ? 'warning' : 'success'" size="small" style="margin-left: 8px">
              {{ token.permission === 'write' ? '读写' : '只读' }}
            </el-tag>
          </div>
          <div class="api-token-manager__item-meta">
            <span>创建时间：{{ formatDate(token.createdAt) }}</span>
            <span style="margin-left: 12px">过期时间：{{ formatDate(token.expiresAt) }}</span>
          </div>
        </div>
        <div class="api-token-manager__item-actions">
          <el-button
            :icon="Delete"
            type="danger"
            size="small"
            link
            @click="handleDelete(token)"
          >
            撤销
          </el-button>
        </div>
      </div>
    </div>

    <!-- Create dialog -->
    <el-dialog
      v-model="showCreateDialog"
      title="创建 API Token"
      width="460px"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px" label-position="right">
        <el-form-item label="Token 名称" required>
          <el-input
            v-model="createForm.name"
            placeholder="例如：生产系统集成"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="权限范围">
          <el-radio-group v-model="createForm.permission">
            <el-radio value="read">只读（仅检索）</el-radio>
            <el-radio value="write">读写（检索 + 管理）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="有效期">
          <div style="display: flex; align-items: center; gap: 12px; width: 100%">
            <el-switch v-model="createForm.expiresEnabled" />
            <el-input-number
              v-if="createForm.expiresEnabled"
              v-model="createForm.expiresIn"
              :min="3600"
              :max="365 * 86400"
              :step="86400"
              :disabled="!createForm.expiresEnabled"
              style="width: 160px"
            />
            <span v-if="createForm.expiresEnabled" style="color: var(--el-text-color-secondary); font-size: 13px">
              ({{ expiresInText() }})
            </span>
            <span v-else style="color: var(--el-text-color-secondary); font-size: 13px">
              永不过期
            </span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- New token display dialog -->
    <el-dialog
      v-model="showNewToken"
      title="Token 创建成功"
      width="520px"
      :close-on-click-modal="false"
    >
      <div class="api-token-manager__new-token">
        <el-icon :size="48" color="#10B981" style="margin-bottom: 12px"><RefreshRight /></el-icon>
        <p style="margin: 0 0 12px; color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.6">
          Token 已生成，请立即复制并妥善保管。关闭此窗口后将无法再次查看完整 Token 值。
        </p>
        <el-input
          :model-value="newTokenValue"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 5 }"
          readonly
          style="font-family: monospace"
        />
        <el-button
          type="primary"
          :icon="CopyDocument"
          style="margin-top: 12px; width: 100%"
          @click="copyNewToken"
        >
          复制 Token
        </el-button>
      </div>
      <template #footer>
        <el-button @click="showNewToken = false">我已复制，关闭窗口</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.api-token-manager {
  display: flex;
  flex-direction: column;
  gap: $spacing-base;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__header-left {
    display: flex;
    align-items: baseline;
    gap: $spacing-sm;
  }

  &__title {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
  }

  &__subtitle {
    font-size: 13px;
    color: $text-secondary;
  }

  &__hint {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-base;
    background: $color-primary-light;
    border-radius: $radius-sm;
    font-size: 13px;
    color: $color-primary;

    code {
      padding: 2px 6px;
      background: rgba(37, 99, 235, 0.1);
      border-radius: 3px;
      font-family: 'JetBrains Mono', 'Fira Code', monospace;
      font-size: 12px;
    }
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
    min-height: 60px;
  }

  &__item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-sm $spacing-base;
    border: 1px solid $border-base;
    border-radius: $radius-sm;
    transition: background 0.15s;

    &:hover {
      background: $bg-hover;
    }
  }

  &__item-info {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  &__item-name {
    display: flex;
    align-items: center;
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
  }

  &__item-meta {
    font-size: 12px;
    color: $text-secondary;
  }

  &__item-actions {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
  }

  &__new-token {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
}
</style>
