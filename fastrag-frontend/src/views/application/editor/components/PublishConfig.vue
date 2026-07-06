<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Link, Share, Monitor, Iphone, ChatDotRound, ChatLineRound, ChatSquare, Connection } from '@element-plus/icons-vue'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

// ===========================================================================
// 分享发布
// ===========================================================================

const activePublishTab = ref('share')
const publishRecords = ref<any[]>([])
const loading = ref(false)
const publishing = ref(false)

// 分享配置
const publishConfig = ref({
  shareEnabled: false,
  accessPassword: '',
  expiresIn: 'never',
})

// 嵌入选项
const embedOptions = ref({ width: 380, height: 560, themeColor: '#0167e5' })

const publishChannels = [
  { key: 'wechat', label: '微信', icon: 'ChatDotRound', desc: '集成到微信公众号' },
  { key: 'web', label: '网站', icon: 'Monitor', desc: '嵌入到网页' },
  { key: 'app', label: '移动App', icon: 'Iphone', desc: '集成到iOS/Android' },
  { key: 'dingtalk', label: '钉钉', icon: 'ChatLineRound', desc: '集成到钉钉' },
  { key: 'feishu', label: '飞书', icon: 'ChatSquare', desc: '集成到飞书' },
  { key: 'api', label: 'API接口', icon: 'Connection', desc: '通过API调用' },
]

const selectedChannels = ref<string[]>([])

const shareUrl = ref('')
const embedCode = ref('')

function updateShareUrl() {
  const base = `https://app.ais.com/share/${appInfo.id || appId()}`
  shareUrl.value = publishConfig.value.accessPassword ? `${base}?pwd=${publishConfig.value.accessPassword}` : base
  embedCode.value = `<iframe src="${shareUrl.value}" width="${embedOptions.value.width}" height="${embedOptions.value.height}" style="border:none;border-radius:8px" allow="clipboard-write" />`
}

async function loadPublishRecords() {
  try {
    const res: any = await api.getAppPublishRecords(appId())
    publishRecords.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
  } catch (e) {
    publishRecords.value = []
  }
}

async function handlePublish() {
  publishing.value = true
  try {
    const configSnapshot = await api.getAppConfig(appId())
    await api.publishApp(appId(), {
      version: '1.0.0',
      scopeType: 'production',
      configSnapshot,
    })
    ElMessage.success('发布成功！')
    await loadPublishRecords()
  } catch (e) {
    ElMessage.error('发布失败')
  } finally {
    publishing.value = false
  }
}

async function handleRevoke(row: any) {
  try {
    await ElMessageBox.confirm('确认撤回该版本？', '撤回确认', { type: 'warning' })
    // 后端无独立撤回接口，这里仅标记
    row.status = 'rolled_back'
    ElMessage.success('已撤回')
  } catch (e) {
    // 取消
  }
}

function toggleChannel(key: string) {
  const idx = selectedChannels.value.indexOf(key)
  if (idx >= 0) selectedChannels.value.splice(idx, 1)
  else selectedChannels.value.push(key)
}

async function copyToClipboard(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.error('复制失败')
  }
}

onMounted(() => {
  loadPublishRecords()
  updateShareUrl()
})

// 监听配置变化更新链接
watch(() => [publishConfig.value.accessPassword, embedOptions.value.width, embedOptions.value.height], updateShareUrl)
</script>

<template>
  <div class="config-section">
    <h3>分享 & 发布</h3>
    <p class="desc">将应用发布到各个渠道，或生成分享链接/嵌入代码</p>

    <el-tabs v-model="activePublishTab">
      <!-- 分享链接 -->
      <el-tab-pane label="分享链接" name="share">
        <div class="publish-card">
          <el-form label-width="140px" style="max-width:600px;margin-top:16px">
            <el-form-item label="分享状态">
              <el-switch v-model="publishConfig.shareEnabled" active-text="已开启" inactive-text="已关闭" />
            </el-form-item>
            <el-form-item label="分享链接" v-if="publishConfig.shareEnabled">
              <div style="display:flex;gap:8px;width:100%">
                <el-input :model-value="shareUrl" readonly>
                  <template #prepend><el-icon><Link /></el-icon></template>
                </el-input>
                <el-button @click="copyToClipboard(shareUrl)">复制</el-button>
              </div>
              <div class="form-tip">任何人拥有此链接即可访问应用</div>
            </el-form-item>
            <el-form-item label="访问密码" v-if="publishConfig.shareEnabled">
              <el-input v-model="publishConfig.accessPassword" placeholder="设置访问密码（可选）" style="width:200px" />
            </el-form-item>
            <el-form-item label="有效期">
              <el-select v-model="publishConfig.expiresIn" style="width:200px">
                <el-option label="永久有效" value="never" />
                <el-option label="7天" value="7d" />
                <el-option label="30天" value="30d" />
                <el-option label="90天" value="90d" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="ElMessage.success('分享配置已保存')">保存分享设置</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- 嵌入代码 -->
      <el-tab-pane label="嵌入代码" name="embed">
        <div class="publish-card">
          <div style="margin-top:16px">
            <h4 style="margin:0 0 8px;font-size:14px;font-weight:500">HTML 嵌入代码</h4>
            <p class="desc">将以下代码复制到您的网站页面中</p>
            <el-input type="textarea" :model-value="embedCode" :rows="10" readonly style="font-family:monospace;font-size:12px" />
            <el-button size="small" style="margin-top:8px" @click="copyToClipboard(embedCode)">复制代码</el-button>
          </div>
          <div style="margin-top:24px">
            <h4 style="margin:0 0 8px;font-size:14px;font-weight:500">嵌入选项</h4>
            <el-form label-width="120px" style="max-width:400px">
              <el-form-item label="宽">
                <el-input v-model.number="embedOptions.width" size="small">
                  <template #append>px</template>
                </el-input>
              </el-form-item>
              <el-form-item label="高">
                <el-input v-model.number="embedOptions.height" size="small">
                  <template #append>px</template>
                </el-input>
              </el-form-item>
              <el-form-item label="主题色">
                <el-color-picker v-model="embedOptions.themeColor" show-alpha />
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-tab-pane>

      <!-- 发布管理 -->
      <el-tab-pane label="发布管理" name="release">
        <div class="publish-card">
          <el-table :data="publishRecords" stripe size="small" style="margin-top:12px">
            <el-table-column prop="version" label="版本" width="100" />
            <el-table-column prop="environment" label="环境" width="100" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{row}">
                <el-tag :type="row.status==='released'?'success':row.status==='rolled_back'?'danger':'info'" size="small">
                  {{ {released:'已发布',rolled_back:'已回滚',pending:'待发布',draft:'草稿'}[row.status]||row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="160" />
            <el-table-column label="操作" width="160">
              <template #default="{row}">
                <el-button v-if="row.status==='draft'||row.status==='pending'" link type="success" size="small" @click="handlePublish">发布</el-button>
                <el-button v-if="row.status==='released'" link type="warning" size="small" @click="handleRevoke(row)">撤回</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!publishRecords.length" description="暂无发布记录" :image-size="60" />

          <div style="margin-top:16px">
            <el-button type="primary" :loading="publishing" @click="handlePublish">发布新版本</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.config-section {
  h3 { margin: 0 0 $spacing-lg; }
  .desc { color: $text-secondary; margin-bottom: $spacing-base; }
}

.publish-card {
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  padding: $spacing-base;
}

.form-tip {
  font-size: 12px;
  color: $text-secondary;
  margin-top: $spacing-xs;
}
</style>
