<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

// ===========================================================================
// 虚拟机配置
// ===========================================================================

const vmConfig = ref({
  enabled: false,
  image: 'ubuntu-22.04',
  cpu: 2,
  memory: 4,
  disk: 20,
  networkEnabled: true,
  autoShutdown: true,
  shutdownTimeout: 30,
  preInstalledPackages: [] as string[],
  environmentVars: [] as Array<{ key: string; value: string }>,
})

const vmImages = [
  { label: 'Ubuntu 22.04 LTS', value: 'ubuntu-22.04' },
  { label: 'Ubuntu 20.04 LTS', value: 'ubuntu-20.04' },
  { label: 'CentOS 7', value: 'centos-7' },
  { label: 'Debian 11', value: 'debian-11' },
  { label: 'Windows Server 2022', value: 'windows-2022' },
]

const newPackage = ref('')
const saving = ref(false)

function addEnvVar() {
  vmConfig.value.environmentVars.push({ key: '', value: '' })
}

function removeEnvVar(index: number) {
  vmConfig.value.environmentVars.splice(index, 1)
}

async function loadVmConfig() {
  try {
    const res: any = await api.getAppBasicConfig(appId())
    const advanced = res?.advancedOptions || res?.advanced || {}
    if (advanced?.vm) {
      vmConfig.value = { ...vmConfig.value, ...advanced.vm }
      // 确保环境变量是数组
      if (!Array.isArray(vmConfig.value.environmentVars)) {
        vmConfig.value.environmentVars = []
      }
      if (!Array.isArray(vmConfig.value.preInstalledPackages)) {
        vmConfig.value.preInstalledPackages = []
      }
    }
  } catch (e) {
    // 静默处理，使用默认值
  }
}

async function handleSave() {
  saving.value = true
  try {
    // 过滤空的环境变量行
    const envVars = vmConfig.value.environmentVars.filter((v: any) => v.key && v.value)
    await api.saveAppAdvanced(appId(), {
      vm: {
        ...vmConfig.value,
        environmentVars: envVars,
      },
    })
    ElMessage.success('虚拟机配置已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

function addPackage() {
  if (newPackage.value.trim()) {
    vmConfig.value.preInstalledPackages.push(newPackage.value.trim())
    newPackage.value = ''
  }
}

function removePackage(index: number) {
  vmConfig.value.preInstalledPackages.splice(index, 1)
}

onMounted(() => {
  loadVmConfig()
})
</script>

<template>
  <div class="config-section">
    <h3>虚拟机配置</h3>
    <p class="desc">配置应用运行时的虚拟机环境，用于执行代码和运行服务。</p>

    <div class="vm-config-card">
      <div class="vm-header">
        <span>启用虚拟机</span>
        <el-switch v-model="vmConfig.enabled" />
      </div>

      <template v-if="vmConfig.enabled">
        <el-divider />

        <el-form label-width="120px" style="max-width: 600px">
          <el-form-item label="镜像系统">
            <el-select v-model="vmConfig.image" style="width: 100%">
              <el-option v-for="img in vmImages" :key="img.value" :label="img.label" :value="img.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="CPU (核)">
            <el-input-number v-model="vmConfig.cpu" :min="1" :max="16" :step="1" />
          </el-form-item>

          <el-form-item label="内存 (GB)">
            <el-input-number v-model="vmConfig.memory" :min="1" :max="64" :step="1" />
          </el-form-item>

          <el-form-item label="磁盘 (GB)">
            <el-input-number v-model="vmConfig.disk" :min="10" :max="500" :step="10" />
          </el-form-item>

          <el-form-item label="网络访问">
            <el-switch v-model="vmConfig.networkEnabled" />
            <span class="vm-tip">允许虚拟机访问外部网络</span>
          </el-form-item>

          <el-form-item label="自动关机">
            <el-switch v-model="vmConfig.autoShutdown" />
          </el-form-item>

          <el-form-item v-if="vmConfig.autoShutdown" label="超时时间">
            <el-input-number v-model="vmConfig.shutdownTimeout" :min="5" :max="120" :step="5" />
            <span class="vm-unit">分钟</span>
          </el-form-item>
        </el-form>

        <el-divider content-position="left">预装软件包</el-divider>
        <div class="vm-packages">
          <el-tag
            v-for="(pkg, index) in vmConfig.preInstalledPackages"
            :key="index"
            closable
            @close="removePackage(index)"
          >
            {{ pkg }}
          </el-tag>
          <el-input
            v-model="newPackage"
            size="small"
            style="width: 150px"
            placeholder="添加包名"
            @keyup.enter="addPackage"
          >
            <template #append>
              <el-button @click="addPackage">
                <el-icon><Plus /></el-icon>
              </el-button>
            </template>
          </el-input>
        </div>

        <el-divider content-position="left">环境变量</el-divider>
        <el-table :data="vmConfig.environmentVars" size="small" border style="width: 500px">
          <el-table-column label="变量名" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.key" size="small" placeholder="KEY" />
            </template>
          </el-table-column>
          <el-table-column label="变量值" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.value" size="small" placeholder="VALUE" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="removeEnvVar($index)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button size="small" link @click="addEnvVar" style="margin-top: 8px">
          <el-icon><Plus /></el-icon>添加环境变量
        </el-button>
      </template>
    </div>

    <div class="vm-actions">
      <el-button type="primary" :loading="saving" @click="handleSave">保 存</el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.config-section {
  h3 { margin: 0 0 $spacing-lg; }
  .desc { color: $text-secondary; margin-bottom: $spacing-base; }
}

.vm-config-card {
  max-width: 700px;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  padding: $spacing-base;
}

.vm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 500;
}

.vm-tip {
  margin-left: $spacing-sm;
  font-size: 12px;
  color: $text-secondary;
}

.vm-unit {
  margin-left: $spacing-sm;
  font-size: 12px;
  color: $text-secondary;
}

.vm-packages {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  align-items: center;
}

.vm-actions {
  margin-top: $spacing-lg;
  padding-top: $spacing-base;
  border-top: 1px solid $border-lighter;
}
</style>
