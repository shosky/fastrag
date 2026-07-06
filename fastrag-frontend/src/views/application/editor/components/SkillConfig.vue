<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

// ===========================================================================
// 技能配置
// ===========================================================================

const availableSkills = ref<any[]>([])
const selectedSkillIds = ref<string[]>([])
const loading = ref(false)

async function loadSkills() {
  loading.value = true
  try {
    const res: any = await api.getSkills()
    availableSkills.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
  } catch (e) {
    availableSkills.value = []
  } finally {
    loading.value = false
  }
}

async function loadAppConfig() {
  try {
    const config: any = await api.getAppConfig(appId())
    const toolIds = config?.toolIds || ''
    if (toolIds) {
      const ids = String(toolIds).split(',').filter(Boolean)
      selectedSkillIds.value = ids
    }
  } catch (e) {
    // 静默处理
  }
}

async function handleSave() {
  loading.value = true
  try {
    const config: any = await api.getAppConfig(appId())
    const toolIds = String(config?.toolIds || '').split(',').filter(Boolean)
    const nonSkillIds = toolIds.filter((id: string) => !id.startsWith('skill_') && !id.startsWith('skill:'))
    const merged = [...nonSkillIds, ...selectedSkillIds.value].join(',')
    await api.saveAppConfig(appId(), { toolIds: merged })
    ElMessage.success('技能配置已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    loading.value = false
  }
}

function handleToggleSkill(skill: any) {
  const id = String(skill.id)
  const idx = selectedSkillIds.value.indexOf(id)
  if (idx >= 0) {
    selectedSkillIds.value.splice(idx, 1)
  } else {
    selectedSkillIds.value.push(id)
  }
}

onMounted(async () => {
  await Promise.all([loadSkills(), loadAppConfig()])
})
</script>

<template>
  <div class="config-section">
    <h3>技能配置</h3>
    <p class="desc">配置应用可用的技能，扩展智能体的能力。</p>

    <div v-loading="loading" class="skill-list">
      <el-empty v-if="!availableSkills.length" description="暂无可用技能" :image-size="60" />

      <div v-for="skill in availableSkills" :key="skill.id" class="skill-option">
        <div class="skill-info">
          <el-checkbox
            :model-value="selectedSkillIds.includes(String(skill.id))"
            @change="handleToggleSkill(skill)"
          >
            <span class="skill-name">{{ skill.name }}</span>
          </el-checkbox>
          <span class="skill-desc">{{ skill.description || '暂无描述' }}</span>
        </div>
        <el-switch
          :model-value="skill.enabled"
          size="small"
          disabled
          @click.stop
        />
      </div>
    </div>

    <div v-if="availableSkills.length" class="selected-count">
      已选 {{ selectedSkillIds.length }} 个技能
      <el-button type="primary" size="small" style="margin-left: 16px" :loading="loading" @click="handleSave">
        保 存
      </el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.config-section {
  h3 { margin: 0 0 $spacing-lg; }
  .desc { color: $text-secondary; margin-bottom: $spacing-base; }
}

.skill-list {
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  overflow: hidden;
}

.skill-option {
  padding: $spacing-base;
  border-bottom: 1px solid $border-extra-light;
  display: flex;
  align-items: center;
  justify-content: space-between;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: $bg-hover;
  }
}

.skill-info {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
  flex: 1;
}

.skill-name {
  font-weight: 500;
}

.skill-desc {
  font-size: 12px;
  color: $text-secondary;
  margin-left: 24px;
}

.selected-count {
  margin-top: $spacing-base;
  color: $text-secondary;
  font-size: 13px;
  display: flex;
  align-items: center;
}
</style>
