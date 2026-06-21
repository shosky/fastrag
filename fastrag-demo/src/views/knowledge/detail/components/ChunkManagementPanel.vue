<script setup lang="ts">
import { Close, Download, Search, VideoPlay, Headset, Document, Edit, Check, CloseBold } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

// --- Types ---
type FileCategory = 'video' | 'audio' | 'document'

interface Chunk {
  id: string
  index: number
  content: string
  parentId?: string
  children?: Chunk[]
  startTime?: number
  endTime?: number
  metadata: ChunkMetadata
}

interface ChunkMetadata {
  fileId: string
  fileName: string
  chunkIndex: number
  tokenCount: number
  createdAt: string
  updatedAt: string
}

// --- Props & Emits ---
const props = defineProps<{
  visible: boolean
  fileName: string
  fileType: FileCategory
  fileUrl?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

// --- Panel visibility ---
const panelVisible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

// --- Active tab ---
const activeTab = ref<'markdown' | 'chunks'>('chunks')

// --- Search ---
const searchQuery = ref('')
const searchChunkId = ref('')

// --- Edit state ---
const editingChunkId = ref<string | null>(null)
const editingContent = ref('')

// --- Mock markdown content ---
const markdownContent = ref(`# 小微ICT业务 FAQ-V1-0306

## 第一部分：小微ICT业务介绍

以下是中国电信商业客户部关于小微ICT业务以及场景化营销的介绍培训文档，围绕小微 ICT 的定义、业务内容、竞争劣势、目标客户、营销方法等方面展开，为相关人员提供营销指导。

### 1. 小微 ICT 定义

小微ICT是中国电信面向小微企业客户提供的标准化ICT服务产品...

### 2. 业务内容

- 网络维护项目
- 视频监控服务
- 云服务
- 智能组网

## 第二部分：标准化产品包介绍

以下是围绕小微 ICT 标准化产品包的详细设备介绍...`)

// --- Mock chunks with parent-child relationship ---
const chunks = ref<Chunk[]>([
  {
    id: 'chunk_001',
    index: 0,
    content: '## 第一部分：小微ICT业务介绍\n\n以下是中国电信商业客户部关于小微ICT业务以及场景化营销的介绍培训文档...',
    metadata: {
      fileId: 'file_1be435',
      fileName: '小微ICT业务 FAQ-V1-0306.docx',
      chunkIndex: 0,
      tokenCount: 256,
      createdAt: '2026-06-08 15:05:00',
      updatedAt: '2026-06-08 15:05:00',
    },
    children: [
      {
        id: 'chunk_001_1',
        index: 0,
        content: '### 1.1 小微ICT定义\n\n小微ICT是中国电信面向小微企业客户提供的标准化ICT服务产品...',
        parentId: 'chunk_001',
        metadata: {
          fileId: 'file_1be435',
          fileName: '小微ICT业务 FAQ-V1-0306.docx',
          chunkIndex: 0,
          tokenCount: 128,
          createdAt: '2026-06-08 15:05:00',
          updatedAt: '2026-06-08 15:05:00',
        },
      },
      {
        id: 'chunk_001_2',
        index: 1,
        content: '### 1.2 业务内容\n\n- 网络维护项目\n- 视频监控服务\n- 云服务\n- 智能组网',
        parentId: 'chunk_001',
        metadata: {
          fileId: 'file_1be435',
          fileName: '小微ICT业务 FAQ-V1-0306.docx',
          chunkIndex: 1,
          tokenCount: 96,
          createdAt: '2026-06-08 15:05:00',
          updatedAt: '2026-06-08 15:05:00',
        },
      },
    ],
  },
  {
    id: 'chunk_002',
    index: 1,
    content: '员保障全省业务交付与售后，10009 线上 24 小时客服在线，2 - 6 小时上门服务；电脑公司维护人员少，无法及时响应...',
    metadata: {
      fileId: 'file_1be435',
      fileName: '小微ICT业务 FAQ-V1-0306.docx',
      chunkIndex: 1,
      tokenCount: 189,
      createdAt: '2026-06-08 15:05:00',
      updatedAt: '2026-06-08 15:05:00',
    },
  },
  {
    id: 'chunk_003',
    index: 2,
    content: '#### 3 小微ICT的目标客户\n\n#### 4 小微ICT业务营销六步法\n\n## 第二部分：小微ICT标准化产品包介绍...',
    metadata: {
      fileId: 'file_1be435',
      fileName: '小微ICT业务 FAQ-V1-0306.docx',
      chunkIndex: 2,
      tokenCount: 145,
      createdAt: '2026-06-08 15:05:00',
      updatedAt: '2026-06-08 15:05:00',
    },
    children: [
      {
        id: 'chunk_003_1',
        index: 0,
        content: '### 3.1 目标客户类型\n\n- 小微企业\n- 个体工商户\n- 初创公司',
        parentId: 'chunk_003',
        metadata: {
          fileId: 'file_1be435',
          fileName: '小微ICT业务 FAQ-V1-0306.docx',
          chunkIndex: 0,
          tokenCount: 85,
          createdAt: '2026-06-08 15:05:00',
          updatedAt: '2026-06-08 15:05:00',
        },
      },
    ],
  },
  {
    id: 'chunk_004',
    index: 3,
    content: '- 包含网络维护项目，如网络优化，网络负载均衡设置等\n包含服务：全光组网服务费、设计费、施工费、调测费、线路辅材...',
    metadata: {
      fileId: 'file_1be435',
      fileName: '小微ICT业务 FAQ-V1-0306.docx',
      chunkIndex: 3,
      tokenCount: 167,
      createdAt: '2026-06-08 15:05:00',
      updatedAt: '2026-06-08 15:05:00',
    },
  },
  {
    id: 'chunk_005',
    index: 4,
    content: '| **总价** | **2999** | **叠加1个摄像头680元：** | **费用类型** | **型号** | **价格** |',
    metadata: {
      fileId: 'file_1be435',
      fileName: '小微ICT业务 FAQ-V1-0306.docx',
      chunkIndex: 4,
      tokenCount: 112,
      createdAt: '2026-06-08 15:05:00',
      updatedAt: '2026-06-08 15:05:00',
    },
  },
])

// --- Current selected chunk ---
const selectedChunk = ref<Chunk | null>(null)

// --- Computed ---
const chunkCount = computed(() => {
  let count = 0
  chunks.value.forEach(c => {
    count++
    if (c.children) count += c.children.length
  })
  return count
})

const filteredChunks = computed(() => {
  if (!searchChunkId.value) return chunks.value
  return chunks.value.filter(c =>
    c.id.includes(searchChunkId.value) ||
    c.content.includes(searchChunkId.value)
  )
})

// --- Methods ---
function selectChunk(chunk: Chunk) {
  selectedChunk.value = chunk
}

function toggleExpand(chunk: Chunk) {
  if (selectedChunk.value?.id === chunk.id) {
    selectedChunk.value = null
  } else {
    selectedChunk.value = chunk
  }
}

function startEdit(chunk: Chunk) {
  editingChunkId.value = chunk.id
  editingContent.value = chunk.content
}

function cancelEdit() {
  editingChunkId.value = null
  editingContent.value = ''
}

function saveEdit(chunk: Chunk) {
  chunk.content = editingContent.value
  chunk.metadata.updatedAt = new Date().toLocaleString('zh-CN')
  editingChunkId.value = null
  editingContent.value = ''
  ElMessage.success('分片内容已更新')
}

function handleDownload() {
  ElMessage.success('开始下载分片数据')
}

function closePanel() {
  panelVisible.value = false
  selectedChunk.value = null
  editingChunkId.value = null
}

// --- Video/Audio mock ---
const videoChunks = ref([
  { id: '1', index: 0, startTime: 0, endTime: 30, content: '该切片未识别到说话声音' },
  { id: '2', index: 1, startTime: 30, endTime: 60, content: '演示Knowledge settings页面配置' },
  { id: '3', index: 2, startTime: 60, endTime: 90, content: '讲解分块结构和索引设置' },
])

const currentVideoChunk = ref<any>(null)

function formatTime(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}
</script>

<template>
  <Transition name="panel-slide">
    <div v-if="panelVisible" class="chunk-panel">
      <!-- Header -->
      <div class="chunk-panel__header">
        <div class="chunk-panel__file-info">
          <el-icon v-if="fileType === 'video'" class="chunk-panel__file-icon chunk-panel__file-icon--video">
            <VideoPlay />
          </el-icon>
          <el-icon v-else-if="fileType === 'audio'" class="chunk-panel__file-icon chunk-panel__file-icon--audio">
            <Headset />
          </el-icon>
          <el-icon v-else class="chunk-panel__file-icon chunk-panel__file-icon--document">
            <Document />
          </el-icon>
          <span class="chunk-panel__file-name">{{ fileName }}</span>
        </div>
        <div class="chunk-panel__header-actions">
          <span class="chunk-panel__chunk-count">{{ chunkCount }} 个片段</span>
          <el-button :icon="Download" link @click="handleDownload">下载</el-button>
          <el-button :icon="Close" link @click="closePanel" />
        </div>
      </div>

      <!-- Content -->
      <div class="chunk-panel__content">
        <!-- Video/Audio type -->
        <template v-if="fileType === 'video' || fileType === 'audio'">
          <div class="chunk-panel__media">
            <!-- Video player -->
            <div v-if="fileType === 'video'" class="chunk-panel__video">
              <div class="chunk-panel__video-placeholder">
                <el-icon :size="48"><VideoPlay /></el-icon>
                <span>视频播放器</span>
              </div>
            </div>

            <!-- Audio player -->
            <div v-else class="chunk-panel__audio">
              <div class="chunk-panel__audio-placeholder">
                <el-icon :size="48"><Headset /></el-icon>
                <span>音频播放器</span>
              </div>
              <div class="chunk-panel__waveform">
                <div v-for="i in 50" :key="i" class="chunk-panel__wave-bar" :style="{ height: `${Math.random() * 40 + 10}px` }" />
              </div>
            </div>

            <!-- Chunk list (timeline style) -->
            <div class="chunk-panel__timeline">
              <div class="chunk-panel__timeline-header">
                <span>{{ videoChunks.length }} {{ fileType === 'video' ? '视频' : '音频' }}切片</span>
                <el-button link type="primary" size="small">下载</el-button>
              </div>
              <div class="chunk-panel__timeline-list">
                <div
                  v-for="chunk in videoChunks"
                  :key="chunk.id"
                  class="chunk-panel__timeline-item"
                  :class="{ 'chunk-panel__timeline-item--active': currentVideoChunk?.id === chunk.id }"
                  @click="currentVideoChunk = chunk"
                >
                  <div class="chunk-panel__timeline-thumb">
                    <span class="chunk-panel__timeline-index">#{{ chunk.index + 1 }}</span>
                  </div>
                  <div class="chunk-panel__timeline-info">
                    <span class="chunk-panel__timeline-time">
                      {{ formatTime(chunk.startTime) }} - {{ formatTime(chunk.endTime) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Transcript/Outline panel -->
          <div class="chunk-panel__detail">
            <div class="chunk-panel__tabs">
              <el-radio-group v-model="activeTab" size="small">
                <el-radio-button value="transcript">转录文稿</el-radio-button>
                <el-radio-button value="outline">智能大纲</el-radio-button>
              </el-radio-group>
              <el-input
                v-model="searchChunkId"
                placeholder="请输入切片ID"
                size="small"
                style="width: 150px"
                :prefix-icon="Search"
              />
            </div>

            <div class="chunk-panel__transcript">
              <div v-if="currentVideoChunk" class="chunk-panel__transcript-content">
                <div class="chunk-panel__transcript-header">
                  <span class="chunk-panel__transcript-id">#{{ currentVideoChunk.index + 1 }}</span>
                  <span class="chunk-panel__transcript-id-text">ID: {{ currentVideoChunk.id }}</span>
                </div>
                <div class="chunk-panel__transcript-text">
                  {{ currentVideoChunk.content }}
                </div>
              </div>
              <div v-else class="chunk-panel__transcript-empty">
                请选择一个切片查看内容
              </div>
            </div>
          </div>
        </template>

        <!-- Document type -->
        <template v-else>
          <div class="chunk-panel__document">
            <!-- Tabs header -->
            <div class="chunk-panel__doc-header">
              <div class="chunk-panel__doc-tabs">
                <el-radio-group v-model="activeTab" size="small">
                  <el-radio-button value="markdown">Markdown</el-radio-button>
                  <el-radio-button value="chunks">Chunks</el-radio-button>
                </el-radio-group>
                <el-input
                  v-if="activeTab === 'chunks'"
                  v-model="searchChunkId"
                  placeholder="搜索切片"
                  size="small"
                  :prefix-icon="Search"
                  style="width: 180px"
                />
              </div>
              <el-button :icon="Download" size="small">下载</el-button>
            </div>

            <div class="chunk-panel__doc-content">
              <!-- Metadata sidebar -->
              <div class="chunk-panel__metadata">
                <h4 class="chunk-panel__metadata-title">元数据信息</h4>

                <div class="chunk-panel__metadata-section">
                  <div class="chunk-panel__metadata-item">
                    <span class="chunk-panel__metadata-label">文件名</span>
                    <span class="chunk-panel__metadata-value">{{ fileName }}</span>
                  </div>
                  <div class="chunk-panel__metadata-item">
                    <span class="chunk-panel__metadata-label">文件类型</span>
                    <span class="chunk-panel__metadata-value">docx</span>
                  </div>
                  <div class="chunk-panel__metadata-item">
                    <span class="chunk-panel__metadata-label">文件大小</span>
                    <span class="chunk-panel__metadata-value">850 KB</span>
                  </div>
                </div>

                <el-divider />

                <div class="chunk-panel__metadata-section">
                  <div class="chunk-panel__metadata-item">
                    <span class="chunk-panel__metadata-label">总分片数</span>
                    <span class="chunk-panel__metadata-value">{{ chunkCount }}</span>
                  </div>
                  <div class="chunk-panel__metadata-item">
                    <span class="chunk-panel__metadata-label">父分片数</span>
                    <span class="chunk-panel__metadata-value">{{ chunks.length }}</span>
                  </div>
                  <div class="chunk-panel__metadata-item">
                    <span class="chunk-panel__metadata-label">总 Token</span>
                    <span class="chunk-panel__metadata-value">2,456</span>
                  </div>
                </div>

                <el-divider />

                <div class="chunk-panel__metadata-section">
                  <div class="chunk-panel__metadata-item">
                    <span class="chunk-panel__metadata-label">分块策略</span>
                    <span class="chunk-panel__metadata-value">General</span>
                  </div>
                  <div class="chunk-panel__metadata-item">
                    <span class="chunk-panel__metadata-label">分块大小</span>
                    <span class="chunk-panel__metadata-value">500</span>
                  </div>
                  <div class="chunk-panel__metadata-item">
                    <span class="chunk-panel__metadata-label">重叠大小</span>
                    <span class="chunk-panel__metadata-value">50</span>
                  </div>
                  <div class="chunk-panel__metadata-item">
                    <span class="chunk-panel__metadata-label">嵌入模型</span>
                    <span class="chunk-panel__metadata-value">text-embedding-v4</span>
                  </div>
                </div>

                <el-divider />

                <div class="chunk-panel__metadata-section">
                  <div class="chunk-panel__metadata-item">
                    <span class="chunk-panel__metadata-label">创建时间</span>
                    <span class="chunk-panel__metadata-value">2026-06-08 15:05</span>
                  </div>
                  <div class="chunk-panel__metadata-item">
                    <span class="chunk-panel__metadata-label">更新时间</span>
                    <span class="chunk-panel__metadata-value">2026-06-08 15:05</span>
                  </div>
                </div>

                <!-- Selected chunk metadata -->
                <template v-if="selectedChunk">
                  <el-divider />
                  <h5 class="chunk-panel__metadata-subtitle">当前选中分片</h5>
                  <div class="chunk-panel__metadata-section">
                    <div class="chunk-panel__metadata-item">
                      <span class="chunk-panel__metadata-label">分片 ID</span>
                      <span class="chunk-panel__metadata-value chunk-panel__metadata-value--id">{{ selectedChunk.id }}</span>
                    </div>
                    <div class="chunk-panel__metadata-item">
                      <span class="chunk-panel__metadata-label">分片索引</span>
                      <span class="chunk-panel__metadata-value">{{ selectedChunk.index }}</span>
                    </div>
                    <div class="chunk-panel__metadata-item">
                      <span class="chunk-panel__metadata-label">Token 数</span>
                      <span class="chunk-panel__metadata-value">{{ selectedChunk.metadata.tokenCount }}</span>
                    </div>
                    <div class="chunk-panel__metadata-item">
                      <span class="chunk-panel__metadata-label">父分片</span>
                      <span class="chunk-panel__metadata-value">{{ selectedChunk.parentId || '无' }}</span>
                    </div>
                  </div>
                </template>
              </div>

              <!-- Main content -->
              <div class="chunk-panel__main-content">
                <!-- Markdown view -->
                <div v-if="activeTab === 'markdown'" class="chunk-panel__markdown">
                  <div class="chunk-panel__markdown-content" v-html="markdownContent" />
                </div>

                <!-- Chunks view -->
                <div v-else class="chunk-panel__chunks">
                  <div class="chunk-panel__chunks-list">
                    <div
                      v-for="chunk in filteredChunks"
                      :key="chunk.id"
                      class="chunk-panel__chunk-wrapper"
                    >
                      <!-- Parent chunk -->
                      <div
                        class="chunk-panel__chunk-item"
                        :class="{
                          'chunk-panel__chunk-item--active': selectedChunk?.id === chunk.id,
                          'chunk-panel__chunk-item--has-children': chunk.children && chunk.children.length > 0,
                        }"
                        @click="toggleExpand(chunk)"
                      >
                        <div class="chunk-panel__chunk-header">
                          <span class="chunk-panel__chunk-index">#{{ chunk.index }}</span>
                          <span class="chunk-panel__chunk-id">ID: {{ chunk.id }}</span>
                          <el-button
                            v-if="editingChunkId !== chunk.id"
                            :icon="Edit"
                            link
                            size="small"
                            @click.stop="startEdit(chunk)"
                          />
                        </div>

                        <!-- Content or edit mode -->
                        <div v-if="editingChunkId !== chunk.id" class="chunk-panel__chunk-content">
                          {{ chunk.content }}
                        </div>
                        <div v-else class="chunk-panel__chunk-edit">
                          <el-input
                            v-model="editingContent"
                            type="textarea"
                            :rows="4"
                            placeholder="编辑分片内容"
                          />
                          <div class="chunk-panel__chunk-edit-actions">
                            <el-button size="small" @click="cancelEdit">取消</el-button>
                            <el-button type="primary" size="small" @click="saveEdit(chunk)">保存</el-button>
                          </div>
                        </div>

                        <!-- Metadata -->
                        <div class="chunk-panel__chunk-meta">
                          <span>Token: {{ chunk.metadata.tokenCount }}</span>
                          <span>更新: {{ chunk.metadata.updatedAt }}</span>
                          <span v-if="chunk.children && chunk.children.length > 0" class="chunk-panel__chunk-children-count">
                            子分片: {{ chunk.children.length }}
                          </span>
                        </div>
                      </div>

                      <!-- Children chunks -->
                      <div v-if="chunk.children && chunk.children.length > 0 && selectedChunk?.id === chunk.id" class="chunk-panel__children">
                        <div
                          v-for="child in chunk.children"
                          :key="child.id"
                          class="chunk-panel__chunk-item chunk-panel__chunk-item--child"
                          :class="{ 'chunk-panel__chunk-item--active': editingChunkId === child.id }"
                        >
                          <div class="chunk-panel__chunk-header">
                            <span class="chunk-panel__chunk-index">#{{ chunk.index }}.{{ child.index }}</span>
                            <span class="chunk-panel__chunk-id">ID: {{ child.id }}</span>
                            <el-button
                              v-if="editingChunkId !== child.id"
                              :icon="Edit"
                              link
                              size="small"
                              @click.stop="startEdit(child)"
                            />
                          </div>

                          <div v-if="editingChunkId !== child.id" class="chunk-panel__chunk-content">
                            {{ child.content }}
                          </div>
                          <div v-else class="chunk-panel__chunk-edit">
                            <el-input
                              v-model="editingContent"
                              type="textarea"
                              :rows="3"
                              placeholder="编辑分片内容"
                            />
                            <div class="chunk-panel__chunk-edit-actions">
                              <el-button size="small" @click="cancelEdit">取消</el-button>
                              <el-button type="primary" size="small" @click="saveEdit(child)">保存</el-button>
                            </div>
                          </div>

                          <div class="chunk-panel__chunk-meta">
                            <span>Token: {{ child.metadata.tokenCount }}</span>
                            <span>更新: {{ child.metadata.updatedAt }}</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </Transition>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.chunk-panel {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: $bg-white;
  z-index: 2000;
  display: flex;
  flex-direction: column;

  // --- Header ---
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-base $spacing-lg;
    border-bottom: 1px solid $border-lighter;
    flex-shrink: 0;
  }

  &__file-info {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__file-icon {
    font-size: 20px;

    &--video { color: #E91E63; }
    &--audio { color: #9C27B0; }
    &--document { color: #2196F3; }
  }

  &__file-name {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
  }

  &__header-actions {
    display: flex;
    align-items: center;
    gap: $spacing-base;
  }

  &__chunk-count {
    font-size: 14px;
    color: $text-secondary;
  }

  // --- Content ---
  &__content {
    flex: 1;
    display: flex;
    overflow: hidden;
  }

  // --- Media (video/audio) ---
  &__media {
    flex: 1;
    display: flex;
    flex-direction: column;
    border-right: 1px solid $border-lighter;
    overflow: hidden;
  }

  &__video {
    flex: 1;
    background: #000;
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 400px;
  }

  &__video-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $spacing-sm;
    color: #fff;
  }

  &__audio {
    padding: $spacing-lg;
    background: $bg-hover;
  }

  &__audio-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $spacing-sm;
    color: $text-secondary;
    margin-bottom: $spacing-base;
  }

  &__waveform {
    display: flex;
    align-items: flex-end;
    gap: 2px;
    height: 60px;
    padding: $spacing-sm;
    background: $bg-white;
    border-radius: $radius-base;
  }

  &__wave-bar {
    width: 4px;
    background: $color-primary;
    border-radius: 2px;
  }

  // --- Timeline ---
  &__timeline {
    height: 200px;
    border-top: 1px solid $border-lighter;
    display: flex;
    flex-direction: column;
  }

  &__timeline-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-sm $spacing-base;
    font-size: 13px;
    color: $text-secondary;
    border-bottom: 1px solid $border-lighter;
  }

  &__timeline-list {
    flex: 1;
    overflow-x: auto;
    display: flex;
    gap: $spacing-sm;
    padding: $spacing-sm;
  }

  &__timeline-item {
    flex-shrink: 0;
    width: 120px;
    cursor: pointer;
    border: 2px solid transparent;
    border-radius: $radius-base;
    overflow: hidden;

    &:hover, &--active {
      border-color: $color-primary;
    }
  }

  &__timeline-thumb {
    height: 60px;
    background: $bg-hover;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__timeline-index {
    font-size: 12px;
    font-weight: 600;
    color: $text-secondary;
  }

  &__timeline-info {
    padding: 4px $spacing-xs;
    background: $bg-white;
  }

  &__timeline-time {
    font-size: 11px;
    color: $text-secondary;
  }

  // --- Detail panel (transcript) ---
  &__detail {
    width: 400px;
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
  }

  &__tabs {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-sm $spacing-base;
    border-bottom: 1px solid $border-lighter;
  }

  &__transcript {
    flex: 1;
    overflow-y: auto;
    padding: $spacing-base;
  }

  &__transcript-content {
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    overflow: hidden;
  }

  &__transcript-header {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-base;
    background: $bg-hover;
    border-bottom: 1px solid $border-lighter;
  }

  &__transcript-id {
    font-weight: 600;
    color: $text-primary;
  }

  &__transcript-id-text {
    font-size: 12px;
    color: $text-secondary;
  }

  &__transcript-text {
    padding: $spacing-base;
    font-size: 14px;
    line-height: 1.6;
    color: $text-regular;
  }

  &__transcript-empty {
    text-align: center;
    color: $text-placeholder;
    padding: $spacing-xxl;
  }

  // --- Document type ---
  &__document {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  &__doc-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-base $spacing-lg;
    border-bottom: 1px solid $border-lighter;
    flex-shrink: 0;
  }

  &__doc-tabs {
    display: flex;
    align-items: center;
    gap: $spacing-base;
  }

  &__doc-content {
    flex: 1;
    display: flex;
    overflow: hidden;
  }

  // --- Metadata sidebar ---
  &__metadata {
    width: 20%;
    min-width: 220px;
    border-right: 1px solid $border-lighter;
    padding: $spacing-base;
    overflow-y: auto;
    background: $bg-hover;
    flex-shrink: 0;
  }

  &__metadata-title {
    margin: 0 0 $spacing-base;
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
  }

  &__metadata-subtitle {
    margin: 0 0 $spacing-sm;
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
  }

  &__metadata-section {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }

  &__metadata-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__metadata-label {
    font-size: 11px;
    color: $text-placeholder;
  }

  &__metadata-value {
    font-size: 13px;
    color: $text-primary;
    word-break: break-all;

    &--id {
      font-family: monospace;
      font-size: 11px;
      background: $bg-white;
      padding: 2px 6px;
      border-radius: $radius-sm;
    }
  }

  // --- Main content ---
  &__main-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  // --- Markdown view ---
  &__markdown {
    flex: 1;
    overflow-y: auto;
    padding: $spacing-lg;
  }

  &__markdown-content {
    max-width: 800px;
    margin: 0 auto;
    font-size: 14px;
    line-height: 1.8;
    color: $text-regular;

    :deep(h1), :deep(h2), :deep(h3), :deep(h4) {
      color: $text-primary;
      margin: $spacing-lg 0 $spacing-base;
    }

    :deep(h1) { font-size: 24px; }
    :deep(h2) { font-size: 20px; border-bottom: 1px solid $border-lighter; padding-bottom: $spacing-sm; }
    :deep(h3) { font-size: 16px; }

    :deep(ul), :deep(ol) {
      padding-left: $spacing-lg;
    }

    :deep(code) {
      background: $bg-hover;
      padding: 2px 6px;
      border-radius: $radius-sm;
      font-size: 13px;
    }

    :deep(pre) {
      background: $bg-hover;
      padding: $spacing-base;
      border-radius: $radius-base;
      overflow-x: auto;
    }
  }

  // --- Chunks view ---
  &__chunks {
    flex: 1;
    overflow-y: auto;
    padding: $spacing-base;
  }

  &__chunks-list {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }

  &__chunk-wrapper {
    display: flex;
    flex-direction: column;
  }

  &__chunk-item {
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    padding: $spacing-base;
    background: $bg-white;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: $color-primary;
    }

    &--active {
      border-color: $color-primary;
      background: #E3F2FD;
    }

    &--has-children {
      border-left: 3px solid $color-primary;
    }

    &--child {
      margin-left: $spacing-xl;
      border-left: 2px solid $border-base;
      background: $bg-hover;
    }
  }

  &__chunk-header {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-bottom: $spacing-sm;
  }

  &__chunk-index {
    font-weight: 600;
    color: $color-primary;
    font-size: 14px;
  }

  &__chunk-id {
    font-size: 12px;
    color: $text-secondary;
    font-family: monospace;
  }

  &__chunk-content {
    font-size: 13px;
    color: $text-regular;
    line-height: 1.6;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
    word-break: break-word;
  }

  &__chunk-edit {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }

  &__chunk-edit-actions {
    display: flex;
    justify-content: flex-end;
    gap: $spacing-sm;
  }

  &__chunk-meta {
    display: flex;
    gap: $spacing-base;
    margin-top: $spacing-sm;
    font-size: 11px;
    color: $text-placeholder;
  }

  &__chunk-children-count {
    color: $color-primary;
  }

  &__children {
    margin-top: $spacing-xs;
  }
}

// Slide transition
.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: transform 0.3s ease;
}

.panel-slide-enter-from,
.panel-slide-leave-to {
  transform: translateX(100%);
}
</style>
