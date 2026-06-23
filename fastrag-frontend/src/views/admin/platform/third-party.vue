<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const platforms = ref([
  { id: '1', name: '钉钉', status: '未绑定', icon: '#0089FF', config: { corpId: '', clientId: '', clientSecret: '' } },
  { id: '2', name: '企业微信', status: '未绑定', icon: '#07C160', config: { corpId: '', agentId: '', corpSecret: '' } },
  { id: '3', name: '飞书', status: '未绑定', icon: '#3370FF', config: { appId: '', appSecret: '' } },
  { id: '4', name: 'OAuth2', status: '未绑定', icon: '#666666', config: { clientId: '', clientSecret: '', scope: '' } },
])

const activePlatform = ref('1')
const loading = ref(false)

async function loadPlatforms() {
  loading.value = true
  try {
    const res: any = await api.getDictionaries({ type: '三方平台' })
    const settings = res?.['三方平台'] || []
    settings.forEach((item: any) => {
      const platform = platforms.value.find(p => p.name === item.key)
      if (platform) {
        platform.status = item.value ? '已绑定' : '未绑定'
      }
    })
  } finally {
    loading.value = false
  }
}

onMounted(loadPlatforms)

async function handleSave() {
  const platform = platforms.value.find(p => p.id === activePlatform.value)
  if (!platform) return
  try {
    await api.createDictionary({ type: '三方平台', key: platform.name, value: JSON.stringify(platform.config) })
    platform.status = '已绑定'
    ElMessage.success('保存成功')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function handleUnbind(platform: any) {
  try {
    await ElMessageBox.confirm(`确认解绑${platform.name}平台吗？`, '解绑确认', { type: 'warning' })
    await api.createDictionary({ type: '三方平台', key: platform.name, value: '' })
    platform.status = '未绑定'
    ElMessage.success('已解绑')
  } catch {}
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-title">三方平台</div>
      <p class="desc">维护免登平台配置，用于外部组织源、外部账号免密登录和平台联通。</p>

      <div class="platform-grid">
        <div
          v-for="p in platforms"
          :key="p.id"
          class="platform-card"
          :class="{ active: activePlatform === p.id }"
          @click="activePlatform = p.id"
        >
          <div class="platform-icon" :style="{ background: p.icon }">
            <el-icon :size="24" color="#fff"><Connection /></el-icon>
          </div>
          <div class="platform-info">
            <h4>{{ p.name }}</h4>
            <el-tag :type="p.status === '已绑定' ? 'success' : 'info'" size="small">{{ p.status }}</el-tag>
          </div>
        </div>
      </div>

      <div v-for="p in platforms" :key="p.id" v-show="activePlatform === p.id" class="config-section">
        <h3>{{ p.name }} 配置</h3>
        <el-form label-width="120px" style="max-width: 600px">
          <template v-if="p.name === '钉钉'">
            <el-form-item label="CorpId">
              <el-input v-model="p.config.corpId" placeholder="请输入 CorpId" />
            </el-form-item>
            <el-form-item label="ClientId">
              <el-input v-model="p.config.clientId" placeholder="请输入 ClientId" />
            </el-form-item>
            <el-form-item label="ClientSecret">
              <el-input v-model="p.config.clientSecret" type="password" placeholder="请输入 ClientSecret" show-password />
            </el-form-item>
          </template>
          <template v-else-if="p.name === '企业微信'">
            <el-form-item label="企业CorpId">
              <el-input v-model="p.config.corpId" placeholder="请输入企业 CorpId" />
            </el-form-item>
            <el-form-item label="应用AgentId">
              <el-input v-model="p.config.agentId" placeholder="请输入应用 AgentId" />
            </el-form-item>
            <el-form-item label="应用Secret">
              <el-input v-model="p.config.corpSecret" type="password" placeholder="请输入应用 Secret" show-password />
            </el-form-item>
          </template>
          <template v-else-if="p.name === '飞书'">
            <el-form-item label="App ID">
              <el-input v-model="p.config.appId" placeholder="请输入 App ID" />
            </el-form-item>
            <el-form-item label="App Secret">
              <el-input v-model="p.config.appSecret" type="password" placeholder="请输入 App Secret" show-password />
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="ClientId">
              <el-input v-model="p.config.clientId" placeholder="请输入 ClientId" />
            </el-form-item>
            <el-form-item label="ClientSecret">
              <el-input v-model="p.config.clientSecret" type="password" placeholder="请输入 ClientSecret" show-password />
            </el-form-item>
            <el-form-item label="Scope">
              <el-input v-model="p.config.scope" placeholder="请输入 Scope" />
            </el-form-item>
          </template>
          <el-form-item>
            <el-button type="primary" @click="handleSave">保存</el-button>
            <el-button v-if="p.status === '已绑定'" type="danger" @click="handleUnbind(p)">解绑</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.desc { color: $text-secondary; font-size: 13px; margin-bottom: $spacing-lg; }

.platform-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-base;
  margin-bottom: $spacing-xl;
}

.platform-card {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  padding: $spacing-base;
  border: 2px solid $border-lighter;
  border-radius: $radius-base;
  cursor: pointer;
  transition: all 0.2s;
  &:hover { border-color: $color-primary; }
  &.active { border-color: $color-primary; background: #ecf5ff; }
}

.platform-icon {
  width: 48px;
  height: 48px;
  border-radius: $radius-base;
  display: flex;
  align-items: center;
  justify-content: center;
}

.platform-info {
  h4 { margin: 0 0 $spacing-xs; }
}

.config-section {
  padding-top: $spacing-lg;
  border-top: 1px solid $border-lighter;
  h3 { margin: 0 0 $spacing-lg; }
}
</style>
