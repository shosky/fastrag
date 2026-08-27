// ===========================================================================
// 模型监控类型（operation/model-monitor 页面）
// 与后端 ModelMonitorData 模型对齐
// ===========================================================================

/** 指标卡（4 个：Token/调用次数/响应时长/失败数） */
export interface MetricItem {
  label: string
  /** 格式化后的值，如 "1,234,567" */
  value: string
  /** 环比变化，如 "+12.3%" */
  change: string
  /** 趋势方向（用于样式着色） */
  trend: 'up' | 'down'
}

/** 模型使用分布项 */
export interface ModelDistItem {
  name: string
  percentage: number
  token: string
}

/** 高消耗应用排行项 */
export interface TopAppItem {
  rank: number
  name: string
  token: string
  cost: string
}

/** 模型调用统计项（分页列表行） */
export interface ModelStatsItem {
  code: string
  calls: number
  fails: number
  token: string
  cost: string
}

/** 模型监控总览数据（GET /monitor/model/overview） */
export interface ModelMonitorOverview {
  metrics: MetricItem[]
  distribution: ModelDistItem[]
  topApps: TopAppItem[]
  stats: { list: ModelStatsItem[]; total: number }
}
