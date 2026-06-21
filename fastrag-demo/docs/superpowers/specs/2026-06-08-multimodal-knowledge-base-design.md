# 多模态知识库管理模块设计文档

## 概述

为 fastrag-demo 项目设计并实现多模态知识库管理前端，支持视频、音频、Office、PDF、图片等文件类型的上传、预览、处理状态追踪和检索配置。

## 调研来源

- **Yuxi**：企业级 RAG 平台，文件管理 Tab 结构 + 四种上传方式 + OCR/ASR 引擎配置 + 检索测试侧栏 + 知识图谱 + 评估基准
- **PAI-RAG**：Next.js 全栈项目，6 种格式文件预览 + ToggleGroup 检索策略 + 元数据管理

## 技术栈

- Vue 3 + TypeScript + Element Plus（沿用现有技术栈）

## 新增路由

| 路由 | 组件 | 用途 |
|---|---|---|
| `/knowledge/:id` (改造) | `knowledge/detail/index.vue` | Tab 结构知识库详情 |

## 核心组件设计

### 1. 知识库详情页 Tab 结构

**文件**: `src/views/knowledge/detail/index.vue`

改造现有详情页为 Tab 结构：

```
文件管理 | 检索测试 | 知识图谱 | RAG评估 | 设置
```

- 文件管理：文件列表 + 上传 + 统计卡片
- 检索测试：沿用现有 debug.vue 功能
- 知识图谱：P2 阶段实现
- RAG评估：P2 阶段实现
- 设置：沿用现有编辑功能

### 2. 文件管理模块

**文件**: `src/views/knowledge/detail/components/FileManager.vue`

#### 2.1 统计卡片

顶部显示：
- 文件总数
- 已处理数
- 完成进度百分比

#### 2.2 文件列表表格

列：文件名 | 类型图标 | 状态 | 时长/页数 | 大小 | 处理进度 | 操作

状态类型：
- `pending` - 待处理
- `processing` - 处理中（显示进度条）
- `completed` - 已完成
- `failed` - 失败（显示重试按钮）

文件类型图标映射：
- 📄 文档类：PDF/DOCX/XLSX/PPTX/MD/TXT
- 🖼️ 图片类：JPG/PNG/BMP/TIFF
- 🎵 音频类：MP3/WAV/M4A/AAC/OGG
- 🎬 视频类：MP4/AVI/MOV/MKV

操作列：
- 预览（文档/图片/音频/视频）
- 下载
- 删除

#### 2.3 工具栏

- 搜索框
- 筛选下拉（按类型/状态）
- 刷新按钮
- 批量操作（全选/批量删除）

### 3. 文件上传组件

**文件**: `src/views/knowledge/detail/components/FileUploader.vue`

#### 3.1 上传方式 Tab

- 上传文件（拖拽 + 点击）
- 上传文件夹
- 解析 URL
- 工作区选择

#### 3.2 上传后自动入库开关

#### 3.3 处理引擎配置

- OCR 引擎（PDF/图片）：DeepSeek OCR 等
- ASR 引擎（音频/视频）：FunASR 等
- 视频策略：关键帧+ASR / 仅ASR / 均匀采样

#### 3.4 拖拽上传区

支持格式提示：
```
文档: .pdf .docx .xlsx .pptx .md .txt
图片: .jpg .png .bmp .tiff
音频: .mp3 .wav .m4a .aac .ogg
视频: .mp4 .avi .mov .mkv
```

### 4. 文件预览组件

**文件**: `src/views/knowledge/detail/components/FilePreviewDialog.vue`

统一预览弹窗，按文件类型分发：

| 文件类型 | 预览方式 |
|---|---|
| PDF | iframe 内嵌预览 |
| 图片 | img 标签展示 |
| Office | 微软 Office Online Viewer 嵌入 |
| MD/TXT | Markdown 渲染组件 |
| 音频 | 自定义 AudioPlayer 组件 |
| 视频 | 自定义 VideoPlayer 组件 |
| 其他 | 下载链接兜底 |

### 5. 音频播放器

**文件**: `src/views/knowledge/detail/components/AudioPlayer.vue`

功能：
- 播放/暂停按钮
- 进度条（可拖拽）
- 当前时间 / 总时长
- 音量控制
- ASR 转写结果展示（带时间戳）

### 6. 视频播放器

**文件**: `src/views/knowledge/detail/components/VideoPlayer.vue`

功能：
- 播放/暂停按钮
- 进度条（可拖拽）
- 当前时间 / 总时长
- 音量控制
- 全屏切换
- Tab 切换：关键帧时间线 / ASR转写 / 合并视图

### 7. 检索配置面板

**文件**: `src/views/knowledge/detail/components/RetrievalConfigPanel.vue`

右侧侧栏配置：
- 检索模式：向量检索 / 全文检索 / 混合检索
- 最终返回 Chunk 数（输入框）
- 相似度阈值 0-1（输入框）
- BM25 召回数量
- 向量检索权重（混合模式下显示）
- BM25 权重（混合模式下显示）
- BM25 稀疏项丢弃比例
- 保存按钮

### 8. 处理进度组件

**文件**: `src/views/knowledge/detail/components/ProcessStatusBar.vue`

功能：
- 进度条展示
- 当前阶段文字描述
- 阶段列表（已完成/进行中/待处理）

阶段定义：
- 文档: "文本提取" → "分块" → "Embedding" → "存储"
- 图片: "OCR识别" → "描述生成" → "Embedding" → "存储"
- 音频: "ASR转写" → "文本清理" → "分块" → "Embedding" → "存储"
- 视频: "关键帧提取" → "帧理解" → "ASR转写" → "合并" → "分块" → "Embedding" → "存储"

## 数据模型

```typescript
// 文件类型枚举
type FileCategory = 'document' | 'image' | 'audio' | 'video'

// 文件处理状态
type ProcessStatus = 'pending' | 'processing' | 'completed' | 'failed'

// 知识库文件
interface KnowledgeFile {
  id: string
  name: string
  category: FileCategory
  extension: string
  size: number
  url: string
  status: ProcessStatus
  progress: number        // 0-100
  stage?: string          // 当前处理阶段
  duration?: number       // 音视频时长（秒）
  pages?: number          // 文档页数
  createdAt: string
  updatedAt: string
}

// 检索配置
interface RetrievalConfig {
  mode: 'vector' | 'fulltext' | 'hybrid'
  topK: number
  similarityThreshold: number
  bm25RecallCount: number
  vectorWeight: number
  bm25Weight: number
  bm25SparseDropRate: number
}

// 处理引擎配置
interface ProcessingConfig {
  ocrEngine: string       // OCR 引擎
  asrEngine: string       // ASR 引擎
  videoStrategy: 'keyframe_asr' | 'asr_only' | 'uniform_sample'
  keyframeInterval?: number // 关键帧间隔（秒）
}
```

## 实现优先级

### P0 - 核心功能（必须）
1. 知识库详情页 Tab 结构改造
2. 文件管理模块（列表 + 统计 + 搜索筛选）
3. 文件上传组件（拖拽上传 + 多格式支持）
4. 文件预览弹窗（6种格式）
5. 处理状态追踪

### P1 - 增强功能（重要）
6. 音频播放器 + ASR转写展示
7. 视频播放器 + 关键帧时间线
8. 检索配置侧栏
9. 元数据管理

### P2 - 高级功能（可选）
10. 知识图谱配置
11. 评估基准生成
12. URL 解析上传
13. 工作区选择上传

## 文件结构

```
src/views/knowledge/detail/
├── index.vue                          # 详情页主容器（Tab结构）
├── components/
│   ├── FileManager.vue                # 文件管理模块
│   ├── FileUploader.vue               # 文件上传弹窗
│   ├── FilePreviewDialog.vue          # 文件预览弹窗
│   ├── AudioPlayer.vue                # 音频播放器
│   ├── VideoPlayer.vue                # 视频播放器
│   ├── RetrievalConfigPanel.vue       # 检索配置侧栏
│   ├── ProcessStatusBar.vue           # 处理进度组件
│   └── FileTable.vue                  # 文件列表表格
├── debug.vue                          # 检索调试（现有）
└── api-doc.vue                        # API文档（现有）
```
