// ===========================================================================
// 审计与审核发布类型定义
// ===========================================================================

/** 文件操作动作（采编记录用） */
export type EditAction = 'create' | 'update' | 'delete' | 'restore' | 'rename' | 'move' | 'copy'

/** 采编记录 */
export interface EditRecord {
  id: string
  kbId: string
  fileId: string
  fileName: string
  operator: string
  action: EditAction
  detail: string
  timestamp: string
}

// ===========================================================================
// 发布状态机
// ===========================================================================

/** 知识库发布状态 */
export type PublishStatus = 'draft' | 'pending_review' | 'approved' | 'published' | 'rejected'

/** 发布状态中文标签 */
export const PUBLISH_STATUS_LABELS: Record<PublishStatus, string> = {
  draft: '草稿',
  pending_review: '待审核',
  approved: '已通过',
  published: '已发布',
  rejected: '已驳回',
}

/** 发布状态颜色 */
export const PUBLISH_STATUS_COLORS: Record<PublishStatus, 'info' | 'warning' | 'success' | 'primary' | 'danger'> = {
  draft: 'info',
  pending_review: 'warning',
  approved: 'success',
  published: 'primary',
  rejected: 'danger',
}

/** 合法的状态流转 */
export const PUBLISH_TRANSITIONS: Record<PublishStatus, PublishStatus[]> = {
  draft: ['pending_review'],
  pending_review: ['approved', 'rejected'],
  approved: ['published'],
  published: ['draft'],  // 可回退到草稿（取消发布）
  rejected: ['draft'],   // 驳回后可重新编辑
}

// ===========================================================================
// 版本快照
// ===========================================================================

/** 知识库版本快照 */
export interface KnowledgeVersion {
  id: string
  kbId: string
  version: number
  /** 快照时的知识库名称 */
  name: string
  /** 快照时的描述 */
  description: string
  /** 快照时的标签 */
  tags: string[]
  /** 快照时的文件数量 */
  fileCount: number
  /** 快照时的切片数量 */
  chunkCount: number
  publishStatus: PublishStatus
  createdAt: string
  createdBy: string
}

// ===========================================================================
// 审核工单
// ===========================================================================

/** 审核工单状态 */
export type ReviewStatus = 'pending' | 'approved' | 'rejected' | 'timeout'

/** 审核工单 */
export interface ReviewTask {
  id: string
  kbId: string
  kbName: string
  /** 版本快照 ID */
  versionId: string
  version: number
  /** 申请人 */
  applicant: string
  /** 审核人 */
  reviewer: string
  status: ReviewStatus
  /** 审核备注 */
  comment?: string
  createdAt: string
  reviewedAt?: string
}

/** 审核工单状态中文标签 */
export const REVIEW_STATUS_LABELS: Record<ReviewStatus, string> = {
  pending: '待审核',
  approved: '已通过',
  rejected: '已驳回',
  timeout: '已超时',
}

/** 审核工单状态颜色（与 el-tag type 对齐） */
export const REVIEW_STATUS_COLORS: Record<ReviewStatus, 'warning' | 'success' | 'danger' | 'info'> = {
  pending: 'warning',
  approved: 'success',
  rejected: 'danger',
  timeout: 'info',
}
