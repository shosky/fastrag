<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const config = ref({
  lockEnabled: true,
  maxFailures: 5,
  lockDuration: 30,
  maxDevices: 3,
  whitelistEnabled: false,
})

const whitelistUsers = ref<string[]>([])
const searchUser = ref('')
const loading = ref(false)

async function loadConfig() {
  loading.value = true
  try {
    const res: any = await api.getDictionaries({ type: '登录安全' })
    const settings = res?.['登录安全'] || []
    settings.forEach((item: any) => {
      if (item.key === 'lock_enabled') config.value.lockEnabled = item.value === 'true'
      if (item.key === 'max_failures') config.value.maxFailures = parseInt(item.value) || 5
      if (item.key === 'lock_duration') config.value.lockDuration = parseInt(item.value) || 30
      if (item.key === 'max_devices') config.value.maxDevices = parseInt(item.value) || 3
      if (item.key === 'whitelist_enabled') config.value.whitelistEnabled = item.value === 'true'
      if (item.key === 'whitelist_users') whitelistUsers.value = item.value ? item.value.split(',') : []
    })
  } finally {
    loading.value = false
  }
}

onMounted(loadConfig)

function handleAddWhitelist() {
  if (searchUser.value && !whitelistUsers.value.includes(searchUser.value)) {
    whitelistUsers.value.push(searchUser.value)
    searchUser.value = ''
    ElMessage.success('已添加到白名单（待保存）')
  }
}

function handleRemoveWhitelist(user: string) {
  whitelistUsers.value = whitelistUsers.value.filter(u => u !== user)
}

async function handleClearWhitelist() {
  try {
    await ElMessageBox.confirm(`当前白名单有 ${whitelistUsers.value.length} 个用户，清空后将不再绕过设备限制，确认清空？`, '清空白名单', { type: 'warning' })
    whitelistUsers.value = []
    ElMessage.success('已清空')
  } catch {}
}

async function handleRestoreDefault() {
  try {
    await ElMessageBox.confirm('确认恢复默认配置？', '恢复默认', { type: 'warning' })
    config.value = { lockEnabled: true, maxFailures: 5, lockDuration: 30, maxDevices: 3, whitelistEnabled: false }
    whitelistUsers.value = []
    ElMessage.success('已恢复默认')
  } catch {}
}

async function handleSave() {
  try {
    await Promise.all([
      api.createDictionary({ type: '登录安全', key: 'lock_enabled', value: String(config.value.lockEnabled) }),
      api.createDictionary({ type: '登录安全', key: 'max_failures', value: String(config.value.maxFailures) }),
      api.createDictionary({ type: '登录安全', key: 'lock_duration', value: String(config.value.lockDuration) }),
      api.createDictionary({ type: '登录安全', key: 'max_devices', value: String(config.value.maxDevices) }),
      api.createDictionary({ type: '登录安全', key: 'whitelist_enabled', value: String(config.value.whitelistEnabled) }),
      api.createDictionary({ type: '登录安全', key: 'whitelist_users', value: whitelistUsers.value.join(',') }),
    ])
    ElMessage.success('保存成功')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-title">登录安全配置</div>

      <el-form label-width="160px" style="max-width: 600px; margin-top: 24px">
        <el-divider>登录失败锁定</el-divider>
        <el-form-item label="启用登录失败锁定">
          <el-switch v-model="config.lockEnabled" />
        </el-form-item>
        <template v-if="config.lockEnabled">
          <el-form-item label="最大允许失败次数">
            <el-input-number v-model="config.maxFailures" :min="1" :max="20" />
          </el-form-item>
          <el-form-item label="锁定时长（分钟）">
            <el-input-number v-model="config.lockDuration" :min="1" :max="1440" />
          </el-form-item>
          <el-alert :title="`连续 ${config.maxFailures} 次登录失败后，账号将被锁定 ${config.lockDuration} 分钟`" type="info" show-icon :closable="false" style="margin-bottom: 16px" />
        </template>

        <el-divider>设备管控</el-divider>
        <el-form-item label="单账号最大设备数">
          <el-input-number v-model="config.maxDevices" :min="1" :max="10" />
        </el-form-item>

        <el-divider>白名单</el-divider>
        <el-form-item label="启用白名单">
          <el-switch v-model="config.whitelistEnabled" />
        </el-form-item>
        <template v-if="config.whitelistEnabled">
          <el-form-item label="添加用户">
            <div class="whitelist-input">
              <el-input v-model="searchUser" placeholder="输入用户名" style="flex: 1" />
              <el-button type="primary" @click="handleAddWhitelist">添加用户</el-button>
            </div>
          </el-form-item>
          <el-form-item label="白名单列表">
            <div class="whitelist-list">
              <el-tag
                v-for="user in whitelistUsers"
                :key="user"
                closable
                @close="handleRemoveWhitelist(user)"
              >
                {{ user }}
              </el-tag>
              <el-empty v-if="!whitelistUsers.length" description="暂无白名单用户" :image-size="40" />
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="danger" @click="handleClearWhitelist">清空白名单</el-button>
          </el-form-item>
        </template>
      </el-form>

      <div class="form-footer">
        <el-button @click="handleRestoreDefault">恢复默认</el-button>
        <el-button type="primary" @click="handleSave">保存配置</el-button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.whitelist-input {
  display: flex;
  gap: $spacing-sm;
  width: 100%;
}

.whitelist-list {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.form-footer {
  margin-top: $spacing-xl;
  padding-top: $spacing-base;
  border-top: 1px solid $border-lighter;
  display: flex;
  justify-content: flex-end;
  gap: $spacing-sm;
}
</style>
