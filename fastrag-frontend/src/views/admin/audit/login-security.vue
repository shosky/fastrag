<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const config = ref({
  lockEnabled: true,
  maxFailures: 5,
  lockDuration: 30,
  maxDevices: 3,
  whitelistEnabled: false,
})

const whitelistUsers = ref<string[]>(['admin', 'zgf'])
const searchUser = ref('')

const candidateUsers = ['16666666666', '谢三妹', 'zgf', 'huchenghao', 'lanhaichen', 'xiaoymin', 'liuyang']

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
    ElMessage.success('已恢复默认')
  } catch {}
}

function handleSave() {
  ElMessage.success('保存成功')
}
</script>

<template>
  <div class="page-container">
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
              <el-select
                v-model="searchUser"
                filterable
                placeholder="搜索用户名/姓名/手机号"
                style="flex: 1"
              >
                <el-option v-for="u in candidateUsers" :key="u" :label="u" :value="u" />
              </el-select>
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
