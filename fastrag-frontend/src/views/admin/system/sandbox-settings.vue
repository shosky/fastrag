<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

/**
 * 沙箱总开关（REQ-H07/REQ-H18）：sys_config 键 sandbox.enabled，
 * 为 SandboxConfigService 三级取值（sys_config > yml > 默认 false）的最高层。
 */
const enabled = ref(false)
const loaded = ref(false)
const saving = ref(false)

async function load() {
  try {
    const res: any = await api.getSysConfigs('sandbox')
    const list: any[] = res?.['sandbox'] || res || []
    const hit = (Array.isArray(list) ? list : []).find((i: any) => i.configKey === 'sandbox.enabled' || i.key === 'sandbox.enabled')
    enabled.value = String(hit?.configValue ?? hit?.value ?? 'false') === 'true'
  } catch (e: any) {
    ElMessage.error(e.message || '加载沙箱设置失败')
  } finally {
    loaded.value = true
  }
}
onMounted(load)

async function save(val: boolean | string | number) {
  const on = String(val) === 'true'
  saving.value = true
  try {
    await api.saveSysConfig({
      configKey: 'sandbox.enabled',
      configValue: String(on),
      configType: 'sandbox',
      description: '沙箱子系统总开关（关闭时不新建实例，对话内代码执行返回降级提示）',
    })
    enabled.value = on
    ElMessage.success(on ? '沙箱已启用' : '沙箱已关闭')
  } catch (e: any) {
    enabled.value = !on
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="section-title">沙箱执行</div>
      <p class="desc">
        应用对话内的文件与命令类工具在隔离沙箱容器中执行。总开关为最高层取值：关闭时所有应用不创建沙箱实例，
        工具调用返回降级提示；各应用的引用与沙箱配置在「工具与服务 → 沙箱配置」及应用配置中维护。
      </p>
      <div class="switch-row" v-loading="!loaded">
        <div>
          <div class="switch-label">启用沙箱执行</div>
          <div class="switch-desc">开启后，引用了沙箱配置的应用对话将在 Docker 容器内执行代码类工具</div>
        </div>
        <el-switch v-model="enabled" :loading="saving" @change="save" />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.section-title {
  font-size: 15px;
  font-weight: 600;
}
.desc {
  margin: 8px 0 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
.switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  .switch-label { font-weight: 600; }
  .switch-desc { margin-top: 4px; font-size: 12px; color: var(--el-text-color-secondary); }
}
</style>
