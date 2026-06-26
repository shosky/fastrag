<script lang="ts">
/** Upload mode */
export type UploadMode = 'file' | 'folder' | 'url'

/** Content language */
export type ContentLanguage = 'auto' | 'zh' | 'en' | 'mixed'

/** Per-file metadata */
export interface FileMeta {
  file: File
  name: string
  size: number
  progress: number
  status: 'uploading' | 'completed' | 'error' | 'pending'
  error?: string
  /** 上传后的文件ID */
  fileId?: string
  /** 音视频时间裁剪范围（秒） */
  timeRange?: { start: number; end: number }
}

/** 文件编码 */
export type FileEncoding = 'auto' | 'utf-8' | 'gbk' | 'shift-jis'

/** 处理优先级 */
export type ProcessingPriority = 'low' | 'normal' | 'high'

/** Upload configuration emitted with the upload event */
export interface UploadConfig {
  mode: UploadMode
  autoIndex: boolean
  parseStrategyId: string
  processingMode: 'chunk' | 'qa'
  /** 内容语言（影响 OCR/ASR/Embedding/分词） */
  language: ContentLanguage
  /** 文件编码（TXT/CSV 专用） */
  encoding: FileEncoding
  /** 处理优先级 */
  priority: ProcessingPriority
  /** 失败重试次数 */
  retryCount: number
  engineConfig: {
    ocrEngine: string
    asrEngine: string
    videoStrategy: string
    keyframeInterval: number
    /** 视觉描述模型（图片 VLM） */
    vlmModel: string
  }
  /** 音频/视频专属 */
  mediaConfig?: {
    speakerDiarize: boolean
    /** per-file 时间裁剪范围（秒），key 为文件名 */
    timeRanges?: Record<string, { start: number; end: number }>
  }
  qaConfig?: {
    llmModel: string
    prompt: string
  }
}
</script>

<script setup lang="ts">
import { UploadFilled, Link, FolderOpened, Delete, ArrowLeft, Document, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatFileSize } from '@/types/knowledge'
import { useParseStrategy } from '@/composables/useParseStrategy'

const props = defineProps<{
  visible: boolean
  kbId?: string
  /** 已有文件名列表，用于上传时冲突检测 */
  existingFileNames?: string[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'upload', files: File[], config: UploadConfig & { fileIds?: string[] }): void
}>()

// --- Dialog visibility ---
const dialogVisible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

// --- Wizard state ---
type WizardStep = 1 | 2 | 3 | 4
const currentStep = ref<WizardStep>(1)

// --- Upload mode ---
const uploadMode = ref<UploadMode>('file')
const fileInputRef = ref<HTMLInputElement | null>(null)
const folderInputRef = ref<HTMLInputElement | null>(null)
const urlInput = ref('')
const isDragging = ref(false)

// --- File constraints ---
const MAX_FILE_COUNT = 20
const MAX_FILE_SIZE = 50 * 1024 * 1024 // 50MB（文档）
const MAX_VIDEO_SIZE = 200 * 1024 * 1024 // 200MB（视频单独放宽）
const ACCEPTED_EXTENSIONS = ['.txt', '.docx', '.csv', '.xlsx', '.pdf', '.md', '.html', '.pptx', '.mp3', '.wav', '.m4a', '.aac', '.ogg', '.mp4', '.avi', '.mov', '.mkv', '.flv', '.jpg', '.jpeg', '.png', '.bmp', '.gif', '.tiff']
const MAX_FILE_SIZE_MB = MAX_FILE_SIZE / 1024 / 1024
const VIDEO_EXTENSIONS = ['.mp4', '.avi', '.mov', '.mkv', '.flv']

// --- File list with progress (用 FileMeta 替代旧的 UploadFileItem) ---
const uploadFiles = ref<FileMeta[]>([])

// --- Computed: file stats ---
const totalFileSize = computed(() => uploadFiles.value.reduce((sum, f) => sum + f.size, 0))
const allFilesCompleted = computed(() => uploadFiles.value.length > 0 && uploadFiles.value.every(f => f.status === 'completed'))
const isStep1Valid = computed(() => uploadFiles.value.length > 0 && allFilesCompleted.value)

// --- Computed: file types in upload list ---
const hasDocuments = computed(() => uploadFiles.value.some(f => /\.(txt|docx|csv|xlsx|pdf|md|html|pptx)$/i.test(f.name)))
const hasImages = computed(() => uploadFiles.value.some(f => /\.(jpg|jpeg|png|bmp|gif|tiff)$/i.test(f.name)))
const hasAudio = computed(() => uploadFiles.value.some(f => /\.(mp3|wav|m4a|aac|ogg)$/i.test(f.name)))
const hasVideo = computed(() => uploadFiles.value.some(f => /\.(mp4|avi|mov|mkv|flv)$/i.test(f.name)))
const hasTextFiles = computed(() => uploadFiles.value.some(f => /\.(txt|csv)$/i.test(f.name)))

// --- Step 1: File helpers ---
function getFileExtension(filename: string): string {
  const ext = filename.substring(filename.lastIndexOf('.'))
  return ext.toLowerCase()
}

function isAcceptedFile(filename: string): boolean {
  const ext = getFileExtension(filename)
  return ACCEPTED_EXTENSIONS.includes(ext)
}

/** 音视频文件可设置时间裁剪 */
function isAudioOrVideo(filename: string): boolean {
  const ext = getFileExtension(filename)
  return ['.mp3', '.wav', '.m4a', '.aac', '.ogg', '.mp4', '.avi', '.mov', '.mkv', '.flv'].includes(ext)
}

/** TXT/CSV 文件可能需要指定编码 */
function isPlainText(filename: string): boolean {
  const ext = getFileExtension(filename)
  return ['.txt', '.csv'].includes(ext)
}

function validateFiles(files: File[]): { valid: File[], errors: string[] } {
  const errors: string[] = []
  const valid: File[] = []

  // Check total count
  if (uploadFiles.value.length + files.length > MAX_FILE_COUNT) {
    errors.push(`最多只能上传 ${MAX_FILE_COUNT} 个文件`)
    return { valid, errors }
  }

  for (const file of files) {
    // Check size（视频单独放宽到 200MB）
    const ext = getFileExtension(file.name)
    const sizeLimit = VIDEO_EXTENSIONS.includes(ext) ? MAX_VIDEO_SIZE : MAX_FILE_SIZE
    const limitMB = sizeLimit / 1024 / 1024
    if (file.size > sizeLimit) {
      errors.push(`「${file.name}」超过 ${limitMB}MB 限制`)
      continue
    }
    // Check type
    if (!isAcceptedFile(file.name)) {
      errors.push(`「${file.name}」不是支持的文件类型`)
      continue
    }
    valid.push(file)
  }

  return { valid, errors }
}

// --- Step 1: Upload methods ---
function handleDragOver(e: DragEvent) {
  e.preventDefault()
  isDragging.value = true
}

function handleDragLeave() {
  isDragging.value = false
}

function handleDrop(e: DragEvent) {
  e.preventDefault()
  isDragging.value = false
  if (e.dataTransfer?.files) {
    addFiles(Array.from(e.dataTransfer.files))
  }
}

function handleFileInputChange(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files) {
    addFiles(Array.from(target.files))
  }
  target.value = ''
}

function triggerFileInput() {
  fileInputRef.value?.click()
}

function triggerFolderInput() {
  folderInputRef.value?.click()
}

async function addFiles(files: File[]) {
  const { valid, errors } = validateFiles(files)

  if (errors.length > 0) {
    errors.forEach(err => ElMessage.warning(err))
  }

  // 批次内去重
  const existingKeys = new Set(uploadFiles.value.map(f => `${f.name}-${f.size}`))

  // 与知识库已有文件的冲突检测
  const existingNames = new Set(props.existingFileNames || [])
  const conflictNames = valid.filter(f => existingNames.has(f.name) && !existingKeys.has(`${f.name}-${f.size}`))

  // 有冲突时逐个询问覆盖（简化：冲突的跳过并提示）
  let filesToAdd = valid.filter(f => !existingKeys.has(`${f.name}-${f.size}`))
  if (conflictNames.length > 0) {
    try {
      await ElMessageBox.confirm(
        `检测到 ${conflictNames.length} 个同名文件已存在（${conflictNames.map(f => f.name).slice(0, 3).join('、')}${conflictNames.length > 3 ? '等' : ''}），将跳过这些文件。`,
        '文件名冲突',
        { confirmButtonText: '继续上传其余文件', cancelButtonText: '取消', type: 'warning' },
      )
      filesToAdd = filesToAdd.filter(f => !existingNames.has(f.name))
    } catch {
      return // 用户取消
    }
  }

  // 添加文件到列表
  const startIndex = uploadFiles.value.length
  filesToAdd.forEach(file => {
    const item: FileMeta = {
      file,
      name: file.name,
      size: file.size,
      progress: 0,
      status: 'uploading',
      ...(isAudioOrVideo(file.name) ? { timeRange: { start: 0, end: 0 } } : {}),
    }
    uploadFiles.value.push(item)
  })

  // 立即开始上传
  for (let i = startIndex; i < uploadFiles.value.length; i++) {
    simulateUpload(i) // 异步启动，不阻塞
  }
}

async function simulateUpload(index: number) {
  const item = uploadFiles.value[index]
  if (!item) return

  const kbId = props.kbId || 'kb_sample'
  const formData = new FormData()
  formData.append('file', item.file)

  try {
    const response = await new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open('POST', `/api/kb/${kbId}/files`)

      const token = localStorage.getItem('ais_token')
      if (token) {
        const cleanToken = token.replace(/^"|"$/g, '')
        xhr.setRequestHeader('Authorization', `Bearer ${cleanToken}`)
      }

      xhr.upload.addEventListener('progress', (e) => {
        if (e.lengthComputable) {
          const progress = Math.round((e.loaded / e.total) * 100)
          uploadFiles.value[index] = {
            ...uploadFiles.value[index],
            progress,
            status: 'uploading',
          }
        }
      })

      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(JSON.parse(xhr.responseText))
        } else {
          reject(new Error(xhr.responseText || '上传失败'))
        }
      }

      xhr.onerror = () => reject(new Error('网络错误'))
      xhr.send(formData)
    })

    // 上传成功，保存文件ID
    const responseData = response as any
    const fileId = responseData?.data?.id || responseData?.id || responseData?.fileId
    uploadFiles.value[index] = {
      ...uploadFiles.value[index],
      progress: 100,
      status: 'completed',
      fileId,
    }
  } catch (error: any) {
    // 上传失败
    uploadFiles.value[index] = {
      ...uploadFiles.value[index],
      status: 'error',
      error: error.message || '上传失败',
    }
  }
}

function retryUpload(index: number) {
  const item = uploadFiles.value[index]
  if (item) {
    uploadFiles.value[index] = {
      ...item,
      progress: 0,
      status: 'uploading',
      error: undefined,
    }
    simulateUpload(index)
  }
}

function removeFile(index: number) {
  uploadFiles.value.splice(index, 1)
}

function handleUrlAdd() {
  const url = urlInput.value.trim()
  if (!url) return

  // Check file count limit
  if (uploadFiles.value.length >= MAX_FILE_COUNT) {
    ElMessage.warning(`最多只能上传 ${MAX_FILE_COUNT} 个文件`)
    return
  }

  // Validate URL format
  try {
    new URL(url)
  } catch {
    ElMessage.warning('请输入有效的 URL 地址')
    return
  }

  // Check duplicate
  if (uploadFiles.value.some(f => f.name === url)) {
    ElMessage.warning('该 URL 已添加')
    return
  }

  const urlFile = new File([], url, { type: 'text/uri-list' })
  const item: FileMeta = {
    file: urlFile,
    name: url,
    size: 0,
    progress: 0,
    status: 'completed',
  }
  uploadFiles.value.push(item)
  urlInput.value = ''
}

function getFileSize(size: number): string {
  if (size === 0) return 'URL'
  return formatFileSize(size)
}

function clearAllFiles() {
  uploadFiles.value = []
}

// --- Step 1: Mode switch ---
function switchMode(mode: UploadMode) {
  if (uploadMode.value === mode) return
  if (uploadFiles.value.length > 0) {
    ElMessageBox.confirm(
      '切换上传模式将清空已选文件，是否继续？',
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    ).then(() => {
      uploadFiles.value = []
      uploadMode.value = mode
    }).catch(() => {})
  } else {
    uploadMode.value = mode
  }
}

// --- Step 2: Processing config ---
const autoIndex = ref(true)
const processingMode = ref<'chunk' | 'qa'>('chunk')
// 解析策略选择器（切片参数统一在解析策略中配置，此处不再重复）
const selectedStrategyId = ref('')
// 内容语言（影响 OCR/ASR/Embedding/分词）
const contentLanguage = ref<ContentLanguage>('auto')
// 文件编码（TXT/CSV 专用）
const fileEncoding = ref<FileEncoding>('auto')
// 处理优先级
const processingPriority = ref<ProcessingPriority>('normal')
// 失败重试次数
const retryCount = ref(2)
// 说话人分离（音频/视频）
const speakerDiarize = ref(false)

// --- 解析策略选项（从 useParseStrategy 取） ---
const { strategies: strategyList, load: loadStrategies } = useParseStrategy(props.kbId || 'default')
const strategyOptions = computed(() => [
  { label: '自动匹配（按扩展名）', value: '' },
  ...strategyList.value.map((s) => ({ label: `${s.name}${s.isDefault ? '（默认）' : ''}`, value: s.id })),
])

// --- Step 2: QA config ---
const qaConfig = ref({
  llmModel: 'gpt-4',
  prompt: '请从以下文本中提取问答对，每个问答对包含一个问题和一个答案。',
})

const llmModelOptions = [
  { label: 'GPT-4', value: 'gpt-4' },
  { label: 'GPT-3.5', value: 'gpt-3.5-turbo' },
  { label: 'Claude 3', value: 'claude-3' },
  { label: '本地模型', value: 'local' },
]

// --- Step 2: Engine config ---
const engineConfig = ref({
  ocrEngine: 'DeepSeek OCR',
  asrEngine: 'FunASR',
  videoStrategy: 'keyframe_asr',
  keyframeInterval: 5,
  vlmModel: 'disabled',
})

const ocrEngineOptions = [
  { label: 'DeepSeek OCR', value: 'DeepSeek OCR' },
  { label: 'PaddleOCR', value: 'PaddleOCR' },
]

const asrEngineOptions = [
  { label: 'FunASR', value: 'FunASR' },
  { label: 'Whisper', value: 'Whisper' },
]

const videoStrategyOptions = [
  { label: '关键帧+ASR', value: 'keyframe_asr' as const },
  { label: '仅ASR', value: 'asr_only' as const },
  { label: '均匀采样', value: 'uniform_sample' as const },
]

const vlmModelOptions = [
  { label: '禁用（仅 OCR）', value: 'disabled' },
  { label: 'Qwen-VL-Max', value: 'qwen-vl-max' },
  { label: 'GPT-4o', value: 'gpt-4o' },
]

const languageOptions = [
  { label: '自动检测', value: 'auto' as ContentLanguage },
  { label: '中文', value: 'zh' as ContentLanguage },
  { label: '英文', value: 'en' as ContentLanguage },
  { label: '中英混合', value: 'mixed' as ContentLanguage },
]

const encodingOptions = [
  { label: '自动检测', value: 'auto' as FileEncoding },
  { label: 'UTF-8', value: 'utf-8' as FileEncoding },
  { label: 'GBK', value: 'gbk' as FileEncoding },
  { label: 'Shift-JIS', value: 'shift-jis' as FileEncoding },
]

const priorityOptions = [
  { label: '低', value: 'low' as ProcessingPriority },
  { label: '中', value: 'normal' as ProcessingPriority },
  { label: '高', value: 'high' as ProcessingPriority },
]

const retryOptions = [
  { label: '不重试', value: 0 },
  { label: '1 次', value: 1 },
  { label: '2 次', value: 2 },
  { label: '3 次', value: 3 },
]

const showKeyframeInterval = computed(() => engineConfig.value.videoStrategy === 'keyframe_asr')

// --- Step 3: Preview ---
const previewFileIndex = ref(0)

// Generate mock preview based on file type
function getPreviewChunks(fileIndex: number) {
  const file = uploadFiles.value[fileIndex]
  if (!file) return []

  // Document preview
  if (/\.(txt|docx|csv|xlsx|pdf|md|html|pptx)$/i.test(file.name)) {
    return [
      { title: '第一部分：业务介绍', content: '以下是关于业务以及场景化营销的介绍培训文档，围绕业务的定义、内容、竞争优势、目标客户、营销方法等方面展开...' },
      { title: '第二部分：产品包介绍', content: '包含多种网络接入及融合产品。如互联网极速专线，具备融安、融云、融应用的特性；极致融合套餐...' },
      { title: '第三部分：营销策略', content: '对比电脑公司和第三方，在多方面具备显著优势。在维护方面，拥有完善的省 - 市 - 县三级装维架构...' },
    ]
  }

  // Audio preview
  if (/\.(mp3|wav|m4a|aac|ogg)$/i.test(file.name)) {
    return [
      { title: '#1 00:00 - 01:30', content: '该切片包含会议开场白，介绍了本次会议的主要议题和参与人员...' },
      { title: '#2 01:30 - 03:00', content: '讨论了产品规划和市场策略，包括Q2的重点项目和预算分配...' },
      { title: '#3 03:00 - 05:00', content: '技术团队汇报了系统架构升级计划，预计在下季度完成迁移...' },
    ]
  }

  // Video preview
  if (/\.(mp4|avi|mov|mkv|flv)$/i.test(file.name)) {
    return [
      { title: '#1 00:00 - 00:30', content: '演示 Knowledge settings 页面配置' },
      { title: '#2 00:30 - 01:00', content: '讲解分块结构和索引设置' },
      { title: '#3 01:00 - 02:00', content: '展示检索测试功能和效果' },
    ]
  }

  // Image preview
  if (/\.(jpg|jpeg|png|bmp|gif|tiff)$/i.test(file.name)) {
    return [
      { title: 'OCR 识别结果', content: '通过 OCR 技术识别图片中的文字内容，支持中英文混合识别...' },
    ]
  }

  return [{ title: '预览', content: '暂无预览内容' }]
}

const currentPreviewChunks = computed(() => getPreviewChunks(previewFileIndex.value))

// --- Step 4: Config summary ---
const configSummary = computed(() => {
  const items: { label: string, value: string }[] = []
  items.push({ label: '处理方式', value: processingMode.value === 'chunk' ? '分块存储' : '问答对提取' })

  // 内容语言
  const langLabel = languageOptions.find(o => o.value === contentLanguage.value)?.label || '自动检测'
  items.push({ label: '内容语言', value: langLabel })

  // 文件编码（仅 TXT/CSV 且非自动时显示）
  if (hasTextFiles.value && fileEncoding.value !== 'auto') {
    items.push({ label: '文件编码', value: encodingOptions.find(o => o.value === fileEncoding.value)?.label || '自动' })
  }

  // 处理优先级
  if (processingPriority.value !== 'normal') {
    items.push({ label: '处理优先级', value: priorityOptions.find(o => o.value === processingPriority.value)?.label || '中' })
  }

  // 失败重试
  if (retryCount.value !== 2) {
    items.push({ label: '失败重试', value: retryCount.value === 0 ? '不重试' : `${retryCount.value} 次` })
  }

  // 显示选中的解析策略名称
  const strategyName = selectedStrategyId.value
    ? strategyList.value.find((s) => s.id === selectedStrategyId.value)?.name || '未知策略'
    : '自动匹配'
  items.push({ label: '解析策略', value: strategyName })

  if (processingMode.value !== 'chunk') {
    items.push({ label: 'LLM 模型', value: qaConfig.value.llmModel })
  }

  if (hasDocuments.value || hasImages.value) {
    items.push({ label: 'OCR 引擎', value: engineConfig.value.ocrEngine })
  }
  if (hasImages.value && engineConfig.value.vlmModel !== 'disabled') {
    items.push({ label: '视觉描述', value: vlmModelOptions.find(o => o.value === engineConfig.value.vlmModel)?.label || '' })
  }
  if (hasAudio.value || hasVideo.value) {
    items.push({ label: 'ASR 引擎', value: engineConfig.value.asrEngine })
  }
  if (hasVideo.value) {
    items.push({ label: '视频策略', value: videoStrategyOptions.find(o => o.value === engineConfig.value.videoStrategy)?.label || '' })
  }
  if ((hasAudio.value || hasVideo.value) && speakerDiarize.value) {
    items.push({ label: '说话人分离', value: '已启用' })
  }

  // 显示时间裁剪
  const trimmedFiles = uploadFiles.value.filter(f => f.timeRange && f.timeRange.end > f.timeRange.start)
  if (trimmedFiles.length > 0) {
    items.push({ label: '时间裁剪', value: `${trimmedFiles.length} 个文件已设置` })
  }

  if (autoIndex.value) {
    items.push({ label: '自动入库', value: '已启用' })
  }

  return items
})

// --- Wizard navigation ---
function nextStep() {
  if (currentStep.value < 4) {
    currentStep.value++
  }
}

function prevStep() {
  if (currentStep.value > 1) {
    currentStep.value--
  }
}

async function handleExit() {
  if (uploadFiles.value.length > 0) {
    try {
      await ElMessageBox.confirm(
        '当前有未上传的文件，确定要退出吗？',
        '提示',
        { confirmButtonText: '确定退出', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return // User cancelled
    }
  }
  resetState()
  dialogVisible.value = false
}

function resetState() {
  uploadFiles.value = []
  currentStep.value = 1
  uploadMode.value = 'file'
  urlInput.value = ''
  autoIndex.value = true
  processingMode.value = 'chunk'
  selectedStrategyId.value = ''
  contentLanguage.value = 'auto'
  fileEncoding.value = 'auto'
  processingPriority.value = 'normal'
  retryCount.value = 2
  speakerDiarize.value = false
  engineConfig.value = { ocrEngine: 'DeepSeek OCR', asrEngine: 'FunASR', videoStrategy: 'keyframe_asr', keyframeInterval: 5, vlmModel: 'disabled' }
  qaConfig.value = { llmModel: 'gpt-4', prompt: '请从以下文本中提取问答对，每个问答对包含一个问题和一个答案。' }
  previewFileIndex.value = 0
}

async function handleStartUpload() {
  if (uploadFiles.value.length === 0) return

  // 等待正在上传的文件完成
  const pendingUploads = uploadFiles.value.filter(f => f.status === 'uploading')
  if (pendingUploads.length > 0) {
    ElMessage.info(`等待 ${pendingUploads.length} 个文件上传完成...`)
    // 等待一段时间让上传完成
    await new Promise(resolve => setTimeout(resolve, 2000))
  }

  // 收集配置信息
  const timeRanges: Record<string, { start: number; end: number }> = {}
  uploadFiles.value.forEach((item) => {
    if (item.timeRange && item.timeRange.end > item.timeRange.start) {
      timeRanges[item.name] = { start: item.timeRange.start, end: item.timeRange.end }
    }
  })

  const hasMedia = hasAudio.value || hasVideo.value
  const files = uploadFiles.value.map(item => item.file)
  const fileIds = uploadFiles.value.map(item => item.fileId).filter(Boolean) as string[]

  emit('upload', files, {
    mode: uploadMode.value,
    autoIndex: autoIndex.value,
    parseStrategyId: selectedStrategyId.value,
    processingMode: processingMode.value,
    language: contentLanguage.value,
    encoding: fileEncoding.value,
    priority: processingPriority.value,
    retryCount: retryCount.value,
    engineConfig: { ...engineConfig.value },
    fileIds,
    ...(hasMedia ? { mediaConfig: { speakerDiarize: speakerDiarize.value, ...(Object.keys(timeRanges).length > 0 ? { timeRanges } : {}) } } : {}),
    ...(processingMode.value === 'qa' ? { qaConfig: { ...qaConfig.value } } : {}),
  })

  ElMessage.success('上传完成')
  resetState()
  dialogVisible.value = false
}

// 对话框打开时加载策略列表
watch(dialogVisible, (val) => {
  if (val) {
    loadStrategies()
  }
})
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :show-close="false"
    :close-on-click-modal="false"
    width="80%"
    style="max-width: 1200px"
    destroy-on-close
    class="file-uploader"
  >
    <!-- Header with back button and steps -->
    <div class="file-uploader__header">
      <div class="file-uploader__back">
        <el-button
          v-if="currentStep > 1"
          :icon="ArrowLeft"
          link
          @click="prevStep"
        >
          上一步
        </el-button>
        <el-button
          v-else
          :icon="ArrowLeft"
          link
          @click="handleExit"
        >
          退出
        </el-button>
      </div>
    </div>

    <!-- Steps -->
    <el-steps :active="currentStep - 1" finish-status="success" align-center class="file-uploader__steps">
      <el-step title="选择文件" />
      <el-step title="参数设置" />
      <el-step title="数据预览" />
      <el-step title="确认上传" />
    </el-steps>

    <!-- Step 1: Select files -->
    <div v-if="currentStep === 1" class="file-uploader__step-content">
      <!-- Upload mode tabs -->
      <div class="file-uploader__mode-tabs">
        <div
          v-for="mode in [{ label: '上传文件', value: 'file' }, { label: '上传文件夹', value: 'folder' }, { label: '解析URL', value: 'url' }]"
          :key="mode.value"
          class="file-uploader__mode-tab"
          :class="{ 'is-active': uploadMode === mode.value }"
          @click="switchMode(mode.value as UploadMode)"
        >
          {{ mode.label }}
        </div>
        <div class="file-uploader__auto-index">
          <span class="file-uploader__auto-index-label">上传后自动入库</span>
          <el-switch v-model="autoIndex" />
        </div>
      </div>

      <!-- File upload mode -->
      <div v-if="uploadMode === 'file'">
        <div
          class="file-uploader__dropzone"
          :class="{ 'is-dragging': isDragging }"
          @dragover="handleDragOver"
          @dragleave="handleDragLeave"
          @drop="handleDrop"
          @click="triggerFileInput"
        >
          <el-icon :size="40" class="file-uploader__dropzone-icon">
            <UploadFilled />
          </el-icon>
          <div class="file-uploader__dropzone-text">
            点击或拖动文件到此处上传
          </div>
          <div class="file-uploader__formats">
            支持 .txt, .docx, .csv, .xlsx, .pdf, .md, .html, .pptx 等类型文件
          </div>
          <div class="file-uploader__formats">
            单次可上传 {{ MAX_FILE_COUNT }} 个 {{ MAX_FILE_SIZE_MB }} MB 的文件
          </div>
          <input
            ref="fileInputRef"
            type="file"
            multiple
            hidden
            @change="handleFileInputChange"
          />
        </div>
      </div>

      <!-- Folder upload mode -->
      <div v-else-if="uploadMode === 'folder'">
        <div
          class="file-uploader__dropzone"
          :class="{ 'is-dragging': isDragging }"
          @dragover="handleDragOver"
          @dragleave="handleDragLeave"
          @drop="handleDrop"
          @click="triggerFolderInput"
        >
          <el-icon :size="40" class="file-uploader__dropzone-icon">
            <FolderOpened />
          </el-icon>
          <div class="file-uploader__dropzone-text">
            将文件夹拖到此处，或点击选择文件夹
          </div>
          <input
            ref="folderInputRef"
            type="file"
            webkitdirectory
            hidden
            @change="handleFileInputChange"
          />
        </div>
      </div>

      <!-- URL mode -->
      <div v-else-if="uploadMode === 'url'" class="file-uploader__url-input">
        <el-input
          v-model="urlInput"
          placeholder="请输入网页 URL，如 https://example.com/article"
          clearable
          @keyup.enter="handleUrlAdd"
        >
          <template #prefix>
            <el-icon><Link /></el-icon>
          </template>
          <template #append>
            <el-button @click="handleUrlAdd">添加</el-button>
          </template>
        </el-input>
      </div>

      <!-- File list -->
      <div v-if="uploadFiles.length > 0" class="file-uploader__file-table">
        <div class="file-uploader__file-table-header">
          <span>已选择 {{ uploadFiles.length }} 个文件，共 {{ getFileSize(totalFileSize) }}</span>
          <el-button type="danger" link size="small" @click="clearAllFiles">清空</el-button>
        </div>
        <el-table :data="uploadFiles" style="width: 100%">
          <el-table-column label="文件名" min-width="300">
            <template #default="{ row }">
              <div class="file-uploader__file-name-cell">
                <el-icon class="file-uploader__file-icon"><Document /></el-icon>
                <span>{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <!-- 时间裁剪列：仅音视频显示 -->
          <el-table-column label="时间裁剪" width="260">
            <template #default="{ row }">
              <div v-if="isAudioOrVideo(row.name)" class="file-uploader__time-range">
                <el-input-number
                  v-model="row.timeRange.start"
                  :min="0"
                  :max="row.timeRange.end || 9999"
                  :step="1"
                  size="small"
                  controls-position="right"
                  style="width: 90px"
                  placeholder="开始(秒)"
                />
                <span class="file-uploader__time-sep">→</span>
                <el-input-number
                  v-model="row.timeRange.end"
                  :min="row.timeRange.start || 0"
                  :step="1"
                  size="small"
                  controls-position="right"
                  style="width: 90px"
                  placeholder="结束(秒)"
                />
              </div>
              <span v-else class="file-uploader__no-password">-</span>
            </template>
          </el-table-column>
          <el-table-column label="文件上传进度" width="300">
            <template #default="{ row }">
              <div class="file-uploader__progress-cell">
                <el-progress
                  :percentage="Math.min(100, Math.round(row.progress))"
                  :status="row.status === 'completed' ? 'success' : row.status === 'error' ? 'exception' : undefined"
                  :stroke-width="8"
                />
                <span v-if="row.error" class="file-uploader__error-text">{{ row.error }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="文件大小" width="120" align="right">
            <template #default="{ row }">
              {{ getFileSize(row.size) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center">
            <template #default="{ row, $index }">
              <el-button v-if="row.status === 'error'" type="primary" link size="small" @click="retryUpload($index)">
                重试
              </el-button>
              <el-button type="danger" :icon="Delete" link @click="removeFile($index)" />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- Step 2: Parameter settings -->
    <div v-if="currentStep === 2" class="file-uploader__step-content">
      <!-- 全局设置 -->
      <div class="file-uploader__settings-section">
        <div class="file-uploader__section-title">
          <span class="file-uploader__section-indicator"></span>
          基础设置
        </div>
        <div class="file-uploader__section-content">
          <!-- 内容语言 -->
          <div class="file-uploader__form-item">
            <label class="file-uploader__form-label">内容语言</label>
            <el-select v-model="contentLanguage" style="width: 240px">
              <el-option
                v-for="opt in languageOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <span class="file-uploader__form-hint">影响 OCR、ASR、Embedding 模型和分词方式</span>
          </div>

          <!-- 文件编码（仅 TXT/CSV） -->
          <div v-if="hasTextFiles" class="file-uploader__form-item">
            <label class="file-uploader__form-label">文件编码</label>
            <el-select v-model="fileEncoding" style="width: 240px">
              <el-option
                v-for="opt in encodingOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <span class="file-uploader__form-hint">TXT/CSV 文件的字符编码，自动检测可能不准时请手动指定</span>
          </div>

          <!-- 解析策略 -->
          <div class="file-uploader__form-item">
            <label class="file-uploader__form-label">解析策略</label>
            <el-select v-model="selectedStrategyId" placeholder="自动匹配（按扩展名）" style="width: 320px">
              <el-option
                v-for="opt in strategyOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <span class="file-uploader__form-hint">选择"自动匹配"将根据文件扩展名自动选用对应策略</span>
          </div>

          <!-- 处理方式 -->
          <div class="file-uploader__form-item">
            <label class="file-uploader__form-label">处理方式</label>
            <el-radio-group v-model="processingMode">
              <el-radio value="chunk">分块存储</el-radio>
              <el-radio value="qa">问答对提取</el-radio>
            </el-radio-group>
          </div>

          <!-- 优先级 + 重试策略 -->
          <div class="file-uploader__form-row">
            <div class="file-uploader__form-item file-uploader__form-item--half">
              <label class="file-uploader__form-label">处理优先级</label>
              <el-select v-model="processingPriority" style="width: 100%">
                <el-option
                  v-for="opt in priorityOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </div>
            <div class="file-uploader__form-item file-uploader__form-item--half">
              <label class="file-uploader__form-label">失败重试</label>
              <el-select v-model="retryCount" style="width: 100%">
                <el-option
                  v-for="opt in retryOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </div>
          </div>

          <!-- QA 配置 -->
          <template v-if="processingMode === 'qa'">
            <div class="file-uploader__form-item">
              <label class="file-uploader__form-label">LLM 模型</label>
              <el-select v-model="qaConfig.llmModel" style="width: 100%">
                <el-option
                  v-for="opt in llmModelOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </div>
            <div class="file-uploader__form-item">
              <label class="file-uploader__form-label">提取提示词</label>
              <el-input v-model="qaConfig.prompt" type="textarea" :rows="3" placeholder="请输入提示词..." />
            </div>
          </template>

          <!-- 分块提示 -->
          <template v-else>
            <div class="file-uploader__form-item">
              <el-alert
                type="info"
                :closable="false"
                show-icon
                description="切片方式、切片长度、分隔符等参数请在「解析策略管理」中配置。上传时将使用所选策略的配置。"
              />
            </div>
          </template>
        </div>
      </div>

      <!-- 引擎配置（按文件类型条件显示） -->
      <div v-if="hasDocuments || hasAudio || hasVideo || hasImages" class="file-uploader__settings-section">
        <div class="file-uploader__section-title">
          <span class="file-uploader__section-indicator"></span>
          引擎配置
        </div>
        <div class="file-uploader__section-content">
          <div class="file-uploader__engine-grid">
            <!-- OCR（文档/图片） -->
            <div v-if="hasDocuments || hasImages" class="file-uploader__engine-item">
              <span class="file-uploader__engine-label">OCR 引擎</span>
              <el-select v-model="engineConfig.ocrEngine" style="width: 100%">
                <el-option v-for="opt in ocrEngineOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </div>
            <!-- VLM（图片视觉理解） -->
            <div v-if="hasImages" class="file-uploader__engine-item">
              <span class="file-uploader__engine-label">图片视觉描述模型</span>
              <el-select v-model="engineConfig.vlmModel" style="width: 100%">
                <el-option v-for="opt in vlmModelOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </div>
            <!-- ASR（音频/视频） -->
            <div v-if="hasAudio || hasVideo" class="file-uploader__engine-item">
              <span class="file-uploader__engine-label">ASR 引擎</span>
              <el-select v-model="engineConfig.asrEngine" style="width: 100%">
                <el-option v-for="opt in asrEngineOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </div>
            <!-- 视频策略 -->
            <div v-if="hasVideo" class="file-uploader__engine-item">
              <span class="file-uploader__engine-label">视频策略</span>
              <el-select v-model="engineConfig.videoStrategy" style="width: 100%">
                <el-option v-for="opt in videoStrategyOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </div>
            <!-- 关键帧间隔 -->
            <div v-if="hasVideo && showKeyframeInterval" class="file-uploader__engine-item">
              <span class="file-uploader__engine-label">关键帧间隔（秒）</span>
              <el-input-number v-model="engineConfig.keyframeInterval" :min="1" :max="60" :step="1" style="width: 100%" />
            </div>
          </div>
        </div>
      </div>

      <!-- 音视频专属设置 -->
      <div v-if="hasAudio || hasVideo" class="file-uploader__settings-section">
        <div class="file-uploader__section-title">
          <span class="file-uploader__section-indicator"></span>
          音视频设置
        </div>
        <div class="file-uploader__section-content">
          <!-- 说话人分离 -->
          <div class="file-uploader__form-item">
            <div class="file-uploader__switch-row">
              <el-switch v-model="speakerDiarize" />
              <span class="file-uploader__switch-label">说话人分离</span>
              <span class="file-uploader__form-hint">多人录音中区分不同说话人，输出 [Speaker 1]: ... 格式</span>
            </div>
          </div>

          <!-- 时间裁剪提示 -->
          <div class="file-uploader__form-item">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              description="如需只处理音视频的某一片段，请在左侧文件列表中点击文件设置起止时间。"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Step 3: Data preview -->
    <div v-if="currentStep === 3" class="file-uploader__step-content file-uploader__step-content--preview">
      <!-- Left: File list -->
      <div class="file-uploader__preview-sidebar">
        <div class="file-uploader__preview-sidebar-title">文件列表</div>
        <div class="file-uploader__preview-file-list">
          <div
            v-for="(item, index) in uploadFiles"
            :key="index"
            class="file-uploader__preview-file-item"
            :class="{ 'is-active': previewFileIndex === index }"
            @click="previewFileIndex = index"
          >
            <el-icon class="file-uploader__preview-file-icon"><Document /></el-icon>
            <span class="file-uploader__preview-file-name">{{ item.name }}</span>
          </div>
        </div>
      </div>

      <!-- Right: Chunk preview -->
      <div class="file-uploader__preview-main">
        <div class="file-uploader__preview-header">
          <span>分块预览</span>
          <span class="file-uploader__preview-count">共 {{ currentPreviewChunks.length }} 个分块</span>
        </div>
        <div class="file-uploader__preview-content">
          <div
            v-for="(chunk, index) in currentPreviewChunks"
            :key="index"
            class="file-uploader__preview-chunk"
          >
            <h3 class="file-uploader__preview-chunk-title">{{ chunk.title }}</h3>
            <div class="file-uploader__preview-chunk-content">{{ chunk.content }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Step 4: Confirm upload -->
    <div v-if="currentStep === 4" class="file-uploader__step-content">
      <!-- Config summary -->
      <div class="file-uploader__summary">
        <div class="file-uploader__summary-title">上传配置摘要</div>
        <div class="file-uploader__summary-grid">
          <div v-for="item in configSummary" :key="item.label" class="file-uploader__summary-item">
            <span class="file-uploader__summary-label">{{ item.label }}:</span>
            <span class="file-uploader__summary-value">{{ item.value }}</span>
          </div>
        </div>
      </div>

      <!-- File list -->
      <div class="file-uploader__confirm-table">
        <el-table :data="uploadFiles" style="width: 100%">
          <el-table-column label="来源名" min-width="300">
            <template #default="{ row }">
              <div class="file-uploader__file-name-cell">
                <el-icon class="file-uploader__file-icon"><Document /></el-icon>
                <span>{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="150" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'completed'" type="success">就绪</el-tag>
              <el-tag v-else-if="row.status === 'error'" type="danger">上传失败</el-tag>
              <el-tag v-else type="info">上传中 {{ Math.round(row.progress) }}%</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button type="danger" :icon="Delete" link @click="removeFile($index)" />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- Footer -->
    <template #footer>
      <div class="file-uploader__footer">
        <template v-if="currentStep === 1">
          <el-button type="primary" :disabled="!isStep1Valid" @click="nextStep">
            共 {{ uploadFiles.length }} 个文件 | 下一步
          </el-button>
        </template>
        <template v-else-if="currentStep === 4">
          <el-button
            type="primary"
            :disabled="uploadFiles.length === 0"
            @click="handleStartUpload"
          >
            {{ uploadFiles.some(f => f.status === 'uploading') ? '上传中...' : `共 ${uploadFiles.length} 个文件 | 确认上传` }}
          </el-button>
        </template>
        <template v-else>
          <el-button type="primary" @click="nextStep">
            下一步
          </el-button>
        </template>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.file-uploader {
  :deep(.el-dialog) {
    border-radius: $radius-lg;
  }

  :deep(.el-dialog__header) {
    display: none;
  }

  :deep(.el-dialog__body) {
    padding: 24px;
  }

  :deep(.el-dialog__footer) {
    padding: 16px 24px;
    border-top: 1px solid $border-lighter;
  }

  &__header {
    display: flex;
    align-items: center;
    margin-bottom: 16px;
  }

  &__back {
    .el-button {
      font-size: 14px;
      color: $text-primary;
    }
  }

  &__steps {
    margin-bottom: 32px;
  }

  &__step-content {
    min-height: 400px;

    &--preview {
      display: flex;
      gap: 16px;
    }
  }

  // --- Mode tabs ---
  &__mode-tabs {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-bottom: 20px;
    border-bottom: 1px solid $border-lighter;
    padding-bottom: 12px;
  }

  &__mode-tab {
    padding: 8px 20px;
    font-size: 14px;
    cursor: pointer;
    border-radius: $radius-base;
    color: $text-secondary;
    transition: all 0.2s;

    &:hover {
      color: $color-primary;
      background: $bg-hover;
    }

    &.is-active {
      color: $color-primary;
      background: #ecf5ff;
      font-weight: 500;
    }
  }

  &__auto-index {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__auto-index-label {
    font-size: 14px;
    color: $text-primary;
  }

  // --- Dropzone ---
  &__dropzone {
    border: 2px dashed $border-lighter;
    border-radius: $radius-base;
    padding: 48px 24px;
    text-align: center;
    cursor: pointer;
    transition: all 0.2s;
    background: #fafafa;

    &:hover,
    &.is-dragging {
      border-color: $color-primary;
      background: #ecf5ff;
    }
  }

  &__dropzone-icon {
    color: $color-primary;
    margin-bottom: 12px;
  }

  &__dropzone-text {
    font-size: 16px;
    color: $text-primary;
    margin-bottom: 12px;
  }

  &__formats {
    font-size: 13px;
    color: $text-secondary;
    line-height: 1.8;
  }

  // --- URL input ---
  &__url-input {
    padding: 16px 0;
  }

  // --- File table ---
  &__file-table {
    margin-top: 24px;
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    overflow: hidden;
  }

  &__file-table-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    background: $bg-hover;
    font-size: 13px;
    color: $text-secondary;
  }

  &__file-name-cell {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__file-icon {
    color: $color-primary;
    font-size: 18px;
  }

  &__progress-cell {
    padding-right: 16px;
  }

  &__error-text {
    font-size: 12px;
    color: $color-danger;
    margin-top: 4px;
  }

  // --- Settings sections ---
  &__settings-section {
    margin-bottom: 24px;
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    overflow: hidden;
  }

  &__section-title {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 16px 20px;
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
    background: $bg-white;
    border-bottom: 1px solid $border-lighter;
  }

  &__section-indicator {
    width: 3px;
    height: 16px;
    background: $color-primary;
    border-radius: 2px;
  }

  &__section-content {
    padding: 20px;
  }

  &__form-item {
    margin-bottom: 20px;

    &:last-child {
      margin-bottom: 0;
    }

    &--half {
      flex: 1;
      min-width: 0;
    }
  }

  &__form-label {
    display: block;
    font-size: 14px;
    color: $text-primary;
    margin-bottom: 8px;
    font-weight: 500;
  }

  &__tooltip-icon {
    color: $text-secondary;
    font-size: 14px;
    cursor: pointer;
    margin-left: 4px;
  }

  &__condition-row {
    display: flex;
    gap: 12px;
    align-items: center;
  }

  &__checkbox-group {
    display: flex;
    gap: 24px;
    flex-wrap: wrap;
  }

  &__params-options {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  &__params-option {
    padding: 16px;
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: $color-primary;
    }

    &.is-selected {
      border-color: $color-primary;
      background: #ecf5ff;
    }
  }

  &__params-option-desc {
    font-size: 13px;
    color: $text-secondary;
    margin-top: 4px;
    margin-left: 24px;
  }

  &__custom-params {
    margin-top: 16px;
    padding: 20px;
    background: $bg-hover;
    border-radius: $radius-base;
    border: 1px solid $border-lighter;
  }

  // --- Engine config ---
  &__engine-config {
    margin-top: 20px;
    padding: 16px;
    background: #f5f7fa;
    border-radius: $radius-base;
  }

  &__engine-title {
    font-size: 14px;
    font-weight: 600;
    margin-bottom: 12px;
    color: $text-primary;
  }

  &__engine-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
  }

  &__engine-item {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  &__engine-label {
    font-size: 12px;
    color: $text-secondary;
  }

  &__switch-row {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__switch-label {
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
  }

  &__form-row {
    display: flex;
    gap: 16px;
  }

  &__form-hint {
    font-size: 12px;
    color: $text-secondary;
    margin-left: 8px;
  }

  &__no-password {
    color: $text-placeholder;
    font-size: 12px;
  }

  &__time-range {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  &__time-sep {
    color: $text-secondary;
    font-size: 12px;
  }

  // --- Preview layout ---
  &__preview-sidebar {
    width: 280px;
    flex-shrink: 0;
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    overflow: hidden;
  }

  &__preview-sidebar-title {
    padding: 16px;
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    border-bottom: 1px solid $border-lighter;
    background: $bg-white;
  }

  &__preview-file-list {
    padding: 8px;
  }

  &__preview-file-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px;
    border-radius: $radius-base;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      background: $bg-hover;
    }

    &.is-active {
      background: #ecf5ff;
      color: $color-primary;
    }
  }

  &__preview-file-icon {
    font-size: 18px;
    color: $color-primary;
  }

  &__preview-file-name {
    font-size: 13px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__preview-main {
    flex: 1;
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }

  &__preview-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px;
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    border-bottom: 1px solid $border-lighter;
    background: $bg-white;
  }

  &__preview-count {
    font-size: 13px;
    font-weight: 400;
    color: $text-secondary;
  }

  &__preview-content {
    flex: 1;
    padding: 20px;
    overflow-y: auto;
    background: $bg-white;
  }

  &__preview-chunk {
    margin-bottom: 24px;
    padding-bottom: 24px;
    border-bottom: 1px solid $border-lighter;

    &:last-child {
      border-bottom: none;
      margin-bottom: 0;
      padding-bottom: 0;
    }
  }

  &__preview-chunk-title {
    margin: 0 0 12px;
    font-size: 18px;
    font-weight: 700;
    color: $text-primary;
  }

  &__preview-chunk-content {
    font-size: 14px;
    line-height: 1.8;
    color: $text-regular;
  }

  // --- Summary ---
  &__summary {
    margin-bottom: 24px;
    padding: 16px 20px;
    background: #f5f7fa;
    border-radius: $radius-base;
    border: 1px solid $border-lighter;
  }

  &__summary-title {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 12px;
  }

  &__summary-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 8px;
  }

  &__summary-item {
    font-size: 13px;
  }

  &__summary-label {
    color: $text-secondary;
  }

  &__summary-value {
    color: $text-primary;
    font-weight: 500;
  }

  // --- Confirm table ---
  &__confirm-table {
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    overflow: hidden;
  }

  // --- Footer ---
  &__footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
}
</style>
