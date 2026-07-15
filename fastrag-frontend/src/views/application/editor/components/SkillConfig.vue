<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

const availableSkills = ref<any[]>([])
const bindings = ref<Array<{ id: string; skillId: string; skillName: string; enabled: number }>>([])

// 本地选中状态（保存前不调 API）
const selectedSkillIds = ref<Set<string>>(new Set())

const loading = ref(false)
const saving = ref(false)

const selectedCount = computed(() => selectedSkillIds.value.size)

async function loadData() {
  loading.value = true
  try {
    const [skillsRes, bindingsRes]: any[] = await Promise.all([
      api.getSkills(),
      api.getAppSkillBindings(appId()),
    ])
    availableSkills.value = Array.isArray(skillsRes) ? skillsRes : (skillsRes?.list || skillsRes?.records || [])
    bindings.value = Array.isArray(bindingsRes) ? bindingsRes : (bindingsRes?.list || bindingsRes?.records || [])
    selectedSkillIds.value = new Set(bindings.value.map(b => b.skillId))
  } catch (e) {
    availableSkills.value = []
    bindings.value = []
  } finally {
    loading.value = false
  }
}

function toggleSkill(skillId: string) {
  const id = String(skillId)
  if (selectedSkillIds.value.has(id)) {
    selectedSkillIds.value.delete(id)
  } else {
    selectedSkillIds.value.add(id)
  }
  // 触发响应式
  selectedSkillIds.value = new Set(selectedSkillIds.value)
}

function isSelected(skillId: string): boolean {
  return selectedSkillIds.value.has(String(skillId))
}

async function handleSave() {
  saving.value = true
  try {
    const currentlyBound = new Set(bindings.value.map(b => b.skillId))
    const toAdd = [...selectedSkillIds.value].filter(id => !currentlyBound.has(id))
    const toRemove = bindings.value.filter(b => !selectedSkillIds.value.has(b.skillId))

    for (const b of toRemove) {
      await api.unbindAppSkill(appId(), b.id)
    }
    for (const skillId of toAdd) {
      const skill = availableSkills.value.find(s => String(s.id) === skillId)
      await api.bindAppSkill(appId(), {
        skillId,
        skillName: skill?.name || '',
        enabled: 1,
      })
    }

    bindings.value = bindings.value.filter(b => selectedSkillIds.value.has(b.skillId))
    for (const id of toAdd) {
      bindings.value.push({ id: '', skillId: id, skillName: '', enabled: 1 })
    }

    ElMessage.success(`已保存（新增 ${toAdd.length}，移除 ${toRemove.length}）`)
  } catch (e) {
    ElMessage.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="config-section">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">技能配置</div>
        <span class="selected-count">已选 {{ selectedCount }} 个技能</span>
      </div>
      <p style="font-size:13px;color:var(--el-text-color-secondary);margin-bottom:12px">
        配置应用可用的技能，扩展智能体的能力
      </p>
      <div v-loading="loading" class="option-list">
        <el-empty v-if="!availableSkills.length" description="暂无可用技能" :image-size="60" />
        <div v-for="skill in availableSkills" :key="skill.id" class="option-item">
          <div class="option-info">
            <el-checkbox
              :model-value="isSelected(String(skill.id))"
              @change="toggleSkill(String(skill.id))"
            >
              <span class="option-name">{{ skill.name }}</span>
            </el-checkbox>
            <span class="option-desc">{{ skill.description || '暂无描述' }}</span>
          </div>
          <el-switch :model-value="skill.enabled" size="small" disabled @click.stop />
        </div>
      </div>
    </div>

    <!-- 固定底部保存按钮 -->
    <div class="save-bar">
      <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.config-section { padding-bottom: 72px; }

.card-panel {
  background: var(--el-bg-color-overlay);
  border-radius: $radius-base;
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}

.section-title { font-size: 15px; font-weight: 600; color: $text-primary; }
.selected-count { font-size: 13px; color: $text-secondary; }

.option-list {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: $radius-base;
  overflow: hidden;
}

.option-item {
  padding: $spacing-base;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  display: flex;
  align-items: center;
  justify-content: space-between;

  &:last-child { border-bottom: none; }
  &:hover { background: var(--el-fill-color-light); }
}

.option-info { display: flex; flex-direction: column; gap: $spacing-xs; flex: 1; }
.option-name { font-weight: 500; }
.option-desc { font-size: 12px; color: $text-secondary; margin-left: 24px; }

.save-bar {
  position: sticky;
  bottom: 0;
  margin-top: $spacing-lg;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 4px;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
  z-index: 10;
}
</style>
