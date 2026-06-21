import type {
  PublishStatus,
  KnowledgeVersion,
  ReviewTask,
  ReviewStatus,
} from '@/types/audit'
import { PUBLISH_TRANSITIONS } from '@/types/audit'
import { checkApiPermission } from '@/mock/interceptor'
import { addPublishLog } from '@/mock/kb-logs'

// ===========================================================================
// 审核发布 mock 数据层
//
// 管理：发布状态机 + 版本快照 + 审核工单。
// ===========================================================================

const versionStore: Record<string, KnowledgeVersion[]> = {}
const reviewStore: ReviewTask[] = []
let versionSeq = 100
let reviewSeq = 100

function now(): string {
  return new Date().toLocaleString('zh-CN')
}

function getVersions(kbId: string): KnowledgeVersion[] {
  if (!versionStore[kbId]) {
    // 种子数据
    versionStore[kbId] = [
      {
        id: `v_1`,
        kbId,
        version: 1,
        name: '产品知识库',
        description: '初始版本',
        tags: ['产品', '文档'],
        fileCount: 5,
        chunkCount: 120,
        publishStatus: 'published',
        createdAt: '2026-06-01 10:00',
        createdBy: '管理员',
      },
      {
        id: `v_2`,
        kbId,
        version: 2,
        name: '产品知识库',
        description: '新增 FAQ 文档',
        tags: ['产品', '文档', 'FAQ'],
        fileCount: 8,
        chunkCount: 186,
        publishStatus: 'draft',
        createdAt: '2026-06-15 14:30',
        createdBy: '张三',
      },
    ]
  }
  return versionStore[kbId]
}

// --- 版本快照 CRUD ---

export function getVersionsByKb(kbId: string): KnowledgeVersion[] {
  return getVersions(kbId).map((v) => ({ ...v }))
}

export function getLatestVersion(kbId: string): KnowledgeVersion | null {
  const versions = getVersions(kbId)
  return versions.length > 0 ? { ...versions[versions.length - 1] } : null
}

export function getPublishedVersion(kbId: string): KnowledgeVersion | null {
  const versions = getVersions(kbId)
  const published = versions.find((v) => v.publishStatus === 'published')
  return published ? { ...published } : null
}

export function createVersion(
  kbId: string,
  data: { name: string; description: string; tags: string[]; fileCount: number; chunkCount: number },
): KnowledgeVersion {
  const versions = getVersions(kbId)
  const maxVersion = versions.length > 0 ? Math.max(...versions.map((v) => v.version)) : 0
  const version: KnowledgeVersion = {
    id: `v_${++versionSeq}`,
    kbId,
    version: maxVersion + 1,
    ...data,
    publishStatus: 'draft',
    createdAt: now(),
    createdBy: '当前用户',
  }
  versions.push(version)
  addPublishLog(kbId, 'version_created', `v${version.version}`, `创建版本 v${version.version} 快照`, '草稿', '当前用户')
  return { ...version }
}

// --- 发布状态机 ---

/**
 * 状态流转：检查当前状态是否允许转到目标状态。
 * 返回 true 表示成功，false 表示不允许。
 */
export function transitionStatus(
  kbId: string,
  versionId: string,
  targetStatus: PublishStatus,
  operator: string,
): boolean {
  const versions = getVersions(kbId)
  const version = versions.find((v) => v.id === versionId)
  if (!version) return false

  const allowed = PUBLISH_TRANSITIONS[version.publishStatus] || []
  if (!allowed.includes(targetStatus)) return false

  version.publishStatus = targetStatus

  // 写入发布日志
  const statusLabels: Record<PublishStatus, string> = {
    draft: '草稿', pending_review: '待审核', approved: '已通过', published: '已发布', rejected: '已驳回',
  }
  addPublishLog(kbId, targetStatus === 'draft' ? 'reverted' : targetStatus, `v${version.version}`, `版本 v${version.version} 状态变更为「${statusLabels[targetStatus]}」`, statusLabels[targetStatus], operator)

  // 如果是驳回/回退草稿，同时更新审核工单
  if (targetStatus === 'approved' || targetStatus === 'rejected') {
    const task = reviewStore.find((r) => r.versionId === versionId && r.status === 'pending')
    if (task) {
      task.status = targetStatus as ReviewStatus
      task.reviewedAt = now()
    }
  }
  return true
}

// --- 审核工单 ---

export function getReviewTasks(kbId?: string): ReviewTask[] {
  const list = kbId ? reviewStore.filter((r) => r.kbId === kbId) : reviewStore
  return [...list].reverse().map((r) => ({ ...r }))
}

export function getPendingReviews(): ReviewTask[] {
  return reviewStore.filter((r) => r.status === 'pending').map((r) => ({ ...r }))
}

export function submitForReview(
  kbId: string,
  kbName: string,
  versionId: string,
  version: number,
  applicant: string,
): ReviewTask {
  const task: ReviewTask = {
    id: `review_${++reviewSeq}`,
    kbId,
    kbName,
    versionId,
    version,
    applicant,
    reviewer: '管理员',
    status: 'pending',
    createdAt: now(),
  }
  reviewStore.push(task)
  return { ...task }
}

export function approveReview(reviewId: string, comment: string = ''): boolean {
  checkApiPermission('review:approve')
  const task = reviewStore.find((r) => r.id === reviewId)
  if (!task || task.status !== 'pending') return false
  task.status = 'approved'
  task.comment = comment
  task.reviewedAt = now()
  // 同步更新版本状态
  transitionStatus(task.kbId, task.versionId, 'approved', task.reviewer)
  return true
}

export function rejectReview(reviewId: string, comment: string = ''): boolean {
  checkApiPermission('review:reject')
  const task = reviewStore.find((r) => r.id === reviewId)
  if (!task || task.status !== 'pending') return false
  task.status = 'rejected'
  task.comment = comment
  task.reviewedAt = now()
  // 同步更新版本状态
  transitionStatus(task.kbId, task.versionId, 'rejected', task.reviewer)
  return true
}
