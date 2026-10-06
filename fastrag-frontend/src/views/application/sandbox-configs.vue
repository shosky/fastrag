<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

// ==================== 列表 ====================
interface SandboxConfigRow {
  id: string
  name: string
  sandboxType: string
  enabled: boolean
  identityFingerprint?: string
  updatedAt?: string
  dockerConfig?: any
  cubeConfig?: any
  e2bConfig?: any
  defaultTimeoutSec?: number
  allowPrivateEndpoints?: boolean
  networkPolicy?: any
  envVars?: any
}

const configs = ref<SandboxConfigRow[]>([])
const loading = ref(false)
const searchKeyword = ref('')

const TYPE_LABELS: Record<string, string> = {
  docker: 'Docker',
  cube: 'CubeSandbox',
  e2b: 'E2B',
  disabled: '已禁用',
}

const filteredConfigs = computed(() => {
  let list = configs.value
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter((c) => c.name.toLowerCase().includes(kw))
  }
  return list
})

async function loadConfigs() {
  loading.value = true
  try {
    const raw: any[] = (await api.getSandboxConfigs()) as any || []
    configs.value = raw.map((c) => ({ ...c, enabled: c.enabled === 1 || c.enabled === true }))
  } catch (e: any) {
    ElMessage.error(e.message || '加载沙箱配置失败')
  } finally {
    loading.value = false
  }
}
onMounted(loadConfigs)

// ==================== 新建/编辑抽屉 ====================
const drawerVisible = ref(false)
const editingId = ref<string | null>(null)
const saving = ref(false)

const form = ref({
  name: '',
  sandboxType: 'docker',
  enabled: true,
  defaultTimeoutSec: 0,
  allowPrivateEndpoints: false,
  docker: { image: 'fastrag-sandbox:latest', host: '', tlsCertPath: '', cpuLimit: 2, memoryLimitMb: 2048, pidsLimit: 512, networkMode: 'bridge', idleTtlSec: 1800 },
  cube: { apiUrl: '', proxyUrl: '', sandboxDomain: '', apiKeyRef: '', templateId: '', ttlSec: 1800 },
  e2b: { apiUrl: '', sandboxDomain: '', apiKeyRef: '', templateId: '', proxyUrl: '', ttlSec: 1800 },
})

function openCreate() {
  editingId.value = null
  form.value = {
    name: '',
    sandboxType: 'docker',
    enabled: true,
    defaultTimeoutSec: 0,
    allowPrivateEndpoints: false,
    docker: { image: 'fastrag-sandbox:latest', host: '', tlsCertPath: '', cpuLimit: 2, memoryLimitMb: 2048, pidsLimit: 512, networkMode: 'bridge', idleTtlSec: 1800 },
    cube: { apiUrl: '', proxyUrl: '', sandboxDomain: '', apiKeyRef: '', templateId: '', ttlSec: 1800 },
    e2b: { apiUrl: '', sandboxDomain: '', apiKeyRef: '', templateId: '', proxyUrl: '', ttlSec: 1800 },
  }
  drawerVisible.value = true
}

// 私网/回环地址自动放行（REQ-H16：回环/私网 endpoint 须显式允许，否则保存被拒）
const PRIVATE_HOST_RE = /(localhost|127\.\d|10\.\d|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/i
watch(() => form.value.docker.host, (h) => {
  if (h && PRIVATE_HOST_RE.test(h)) form.value.allowPrivateEndpoints = true
})
watch(() => form.value.cube.apiUrl, (h) => {
  if (h && PRIVATE_HOST_RE.test(h)) form.value.allowPrivateEndpoints = true
})
watch(() => form.value.e2b.apiUrl, (h) => {
  if (h && PRIVATE_HOST_RE.test(h)) form.value.allowPrivateEndpoints = true
})

function openEdit(row: SandboxConfigRow) {
  editingId.value = row.id
  form.value = {
    name: row.name,
    sandboxType: row.sandboxType,
    enabled: row.enabled,
    defaultTimeoutSec: row.defaultTimeoutSec || 0,
    allowPrivateEndpoints: !!row.allowPrivateEndpoints,
    docker: { image: '', host: '', tlsCertPath: '', cpuLimit: 2, memoryLimitMb: 2048, pidsLimit: 512, networkMode: 'bridge', idleTtlSec: 1800, ...(row.dockerConfig || {}) },
    cube: { apiUrl: '', proxyUrl: '', sandboxDomain: '', apiKeyRef: '', templateId: '', ttlSec: 1800, ...(row.cubeConfig || {}) },
    e2b: { apiUrl: '', sandboxDomain: '', apiKeyRef: '', templateId: '', proxyUrl: '', ttlSec: 1800, ...(row.e2bConfig || {}) },
  }
  drawerVisible.value = true
}

function buildPayload() {
  const p: Record<string, unknown> = {
    name: form.value.name,
    sandboxType: form.value.sandboxType,
    enabled: form.value.enabled,
    defaultTimeoutSec: form.value.defaultTimeoutSec,
    allowPrivateEndpoints: form.value.allowPrivateEndpoints,
  }
  if (form.value.sandboxType === 'docker') p.dockerConfig = { ...form.value.docker }
  if (form.value.sandboxType === 'cube') p.cubeConfig = { ...form.value.cube }
  if (form.value.sandboxType === 'e2b') p.e2bConfig = { ...form.value.e2b }
  return p
}

async function save() {
  if (!form.value.name.trim()) { ElMessage.warning('请填写配置名'); return }
  saving.value = true
  try {
    if (editingId.value) {
      await api.updateSandboxConfig(editingId.value, buildPayload())
      ElMessage.success('已保存')
    } else {
      await api.createSandboxConfig(buildPayload())
      ElMessage.success('已创建')
    }
    drawerVisible.value = false
    loadConfigs()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ==================== 行操作 ====================
async function toggleEnabled(row: SandboxConfigRow) {
  try {
    await api.updateSandboxConfig(row.id, { enabled: row.enabled })
    ElMessage.success(row.enabled ? '已启用' : '已禁用')
  } catch (e: any) {
    row.enabled = !row.enabled
    ElMessage.error(e.message || '操作失败')
  }
}

async function verify(row: SandboxConfigRow) {
  try {
    await api.verifySandboxConfig(row.id)
    ElMessage.success(`「${row.name}」连接验证通过`)
  } catch (e: any) {
    ElMessage.error(e.message || '验证失败')
  }
}

async function showInstances(row: SandboxConfigRow) {
  try {
    const list: any[] = (await api.getSandboxInstances(row.id)) as any || []
    if (!list.length) { ElMessage.info(`「${row.name}」名下暂无沙箱实例——实例在对话中实际使用沙箱时才按需自动创建，空闲超时（默认30分钟）或后端重启后会自动回收，属正常现象`); return }
    const lines = list.map((i) => `${(i.sandboxId || '').slice(0, 12)}　${i.state || 'unknown'}`)
    ElMessageBox.alert(lines.join('\n'), `「${row.name}」实例库存（${list.length}）`, { customStyle: { whiteSpace: 'pre' } })
  } catch (e: any) {
    ElMessage.error(e.message || '查询失败')
  }
}

async function remove(row: SandboxConfigRow) {
  try {
    await ElMessageBox.confirm(`确认删除沙箱配置「${row.name}」吗？名下有实例时将被拒绝。`, '删除确认', { type: 'warning' })
    await api.deleteSandboxConfig(row.id)
    ElMessage.success('已删除')
    loadConfigs()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input v-model="searchKeyword" placeholder="搜索配置名" clearable style="width: 220px" />
      </div>
      <div class="toolbar-right">
        <el-button type="primary" @click="openCreate">新建沙箱配置</el-button>
      </div>
    </div>

    <div class="config-grid">
      <el-card v-for="c in filteredConfigs" :key="c.id" class="config-card" shadow="hover">
        <div class="card-head">
          <span class="config-name">{{ c.name }}</span>
          <el-tag size="small" :type="c.sandboxType === 'docker' ? 'primary' : 'warning'">{{ TYPE_LABELS[c.sandboxType] || c.sandboxType }}</el-tag>
          <el-switch v-model="c.enabled" size="small" @change="toggleEnabled(c)" />
        </div>
        <div class="card-meta">
          <span v-if="c.sandboxType === 'docker'">镜像：{{ c.dockerConfig?.image || '-' }}</span>
          <span v-else-if="c.sandboxType === 'cube'">API：{{ c.cubeConfig?.apiUrl || '-' }}</span>
          <span v-else-if="c.sandboxType === 'e2b'">API：{{ c.e2bConfig?.apiUrl || '-' }}</span>
          <span v-else>已禁用配置</span>
        </div>
        <div class="card-actions">
          <el-button size="small" type="primary" link :disabled="c.sandboxType === 'disabled'" @click="verify(c)">验证</el-button>
          <el-button size="small" type="primary" link :disabled="c.sandboxType === 'disabled'" @click="showInstances(c)">实例</el-button>
          <el-button size="small" type="primary" link @click="openEdit(c)">编辑</el-button>
          <el-button size="small" type="danger" link @click="remove(c)">删除</el-button>
        </div>
      </el-card>
      <el-empty v-if="!filteredConfigs.length && !loading" description="暂无沙箱配置，点击右上角新建" style="grid-column: 1 / -1" />
    </div>

    <!-- 新建/编辑抽屉 -->
    <el-drawer v-model="drawerVisible" :title="editingId ? '编辑沙箱配置' : '新建沙箱配置'" size="520px">
      <el-form label-width="130px">
        <el-form-item label="配置名" required><el-input v-model="form.name" maxlength="128" /></el-form-item>
        <el-form-item label="后端类型">
          <el-radio-group v-model="form.sandboxType" :disabled="!!editingId">
            <el-radio-button value="docker">Docker</el-radio-button>
            <el-radio-button value="cube">CubeSandbox</el-radio-button>
            <el-radio-button value="e2b">E2B</el-radio-button>
            <el-radio-button value="disabled">禁用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        <el-form-item label="私网 endpoint">
          <el-switch v-model="form.allowPrivateEndpoints" />
          <span class="hint">允许连接私网/回环地址（localhost、127.0.0.1、内网 IP）；云 metadata 恒拒</span>
        </el-form-item>
        <el-form-item label="执行超时秒"><el-input-number v-model="form.defaultTimeoutSec" :min="0" :max="600" /> <span class="hint">0 = 内置默认</span></el-form-item>

        <template v-if="form.sandboxType === 'docker'">
          <el-divider content-position="left">Docker</el-divider>
          <el-form-item label="镜像"><el-input v-model="form.docker.image" /></el-form-item>
          <el-form-item label="Daemon 地址"><el-input v-model="form.docker.host" placeholder="留空 = 本机 socket / DOCKER_HOST" /></el-form-item>
          <el-form-item label="TLS 证书目录"><el-input v-model="form.docker.tlsCertPath" placeholder="远端 TCP 必填：含 ca/cert/key.pem" /></el-form-item>
          <el-form-item label="网络模式">
            <el-select v-model="form.docker.networkMode" style="width: 160px">
              <el-option label="bridge（可出网）" value="bridge" />
              <el-option label="none（不出网）" value="none" />
            </el-select>
          </el-form-item>
          <el-form-item label="CPU 核数"><el-input-number v-model="form.docker.cpuLimit" :min="0.5" :max="16" :step="0.5" /></el-form-item>
          <el-form-item label="内存 MB"><el-input-number v-model="form.docker.memoryLimitMb" :min="128" :max="32768" :step="128" /></el-form-item>
          <el-form-item label="进程数上限"><el-input-number v-model="form.docker.pidsLimit" :min="32" :max="4096" :step="32" /></el-form-item>
          <el-form-item label="空闲回收秒"><el-input-number v-model="form.docker.idleTtlSec" :min="300" :max="86400" :step="60" /></el-form-item>
        </template>

        <template v-else-if="form.sandboxType === 'cube'">
          <el-divider content-position="left">CubeSandbox</el-divider>
          <el-form-item label="API URL" required><el-input v-model="form.cube.apiUrl" /></el-form-item>
          <el-form-item label="Proxy URL"><el-input v-model="form.cube.proxyUrl" /></el-form-item>
          <el-form-item label="沙箱域名"><el-input v-model="form.cube.sandboxDomain" /></el-form-item>
          <el-form-item label="API Key 引用" required><el-input v-model="form.cube.apiKeyRef" placeholder="环境变量名（存引用不存值）" /></el-form-item>
          <el-form-item label="模板 ID" required><el-input v-model="form.cube.templateId" /></el-form-item>
        </template>

        <template v-else-if="form.sandboxType === 'e2b'">
          <el-divider content-position="left">E2B</el-divider>
          <el-form-item label="API URL"><el-input v-model="form.e2b.apiUrl" placeholder="留空 = E2B Cloud" /></el-form-item>
          <el-form-item label="沙箱域名"><el-input v-model="form.e2b.sandboxDomain" placeholder="留空 = e2b.app" /></el-form-item>
          <el-form-item label="API Key 引用" required><el-input v-model="form.e2b.apiKeyRef" placeholder="环境变量名（存引用不存值）" /></el-form-item>
          <el-form-item label="模板 ID" required><el-input v-model="form.e2b.templateId" /></el-form-item>
          <el-form-item label="Proxy URL"><el-input v-model="form.e2b.proxyUrl" placeholder="自托管数据面网关" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
.config-card {
  .card-head {
    display: flex;
    align-items: center;
    gap: 8px;
    .config-name { font-weight: 600; flex: 1; overflow: hidden; text-overflow: ellipsis; }
  }
  .card-meta {
    margin: 10px 0;
    font-size: 13px;
    color: #909399;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .card-actions { display: flex; gap: 4px; }
}
.hint { margin-left: 8px; font-size: 12px; color: #909399; }
</style>
