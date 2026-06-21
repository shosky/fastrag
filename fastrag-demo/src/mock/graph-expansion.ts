import type { GraphEntity, GraphRelation, GraphExpansionResult } from '@/types/knowledge'

// Mock图谱数据
export const mockGraphData: Record<string, GraphExpansionResult> = {
  '小微ICT': {
    entities: [
      { id: '1', name: '智能组网', type: '服务' },
      { id: '2', name: '机房托管', type: '服务' },
      { id: '3', name: '极速专线', type: '服务' },
      { id: '4', name: '综合布线', type: '服务' },
      { id: '5', name: '视频监控', type: '服务' },
    ],
    relations: [
      { source: '小微ICT', target: '智能组网', label: '提供' },
      { source: '小微ICT', target: '机房托管', label: '提供' },
      { source: '小微ICT', target: '极速专线', label: '提供' },
      { source: '小微ICT', target: '综合布线', label: '提供' },
      { source: '小微ICT', target: '视频监控', label: '提供' },
    ],
    expandedQuery: '小微ICT 智能组网 机房托管 极速专线 综合布线 视频监控',
  },
  'CRM系统': {
    entities: [
      { id: '6', name: '客户管理', type: '功能' },
      { id: '7', name: '销售跟进', type: '功能' },
      { id: '8', name: '数据分析', type: '功能' },
    ],
    relations: [
      { source: 'CRM系统', target: '客户管理', label: '包含' },
      { source: 'CRM系统', target: '销售跟进', label: '支持' },
      { source: 'CRM系统', target: '数据分析', label: '提供' },
    ],
    expandedQuery: 'CRM系统 客户管理 销售跟进 数据分析',
  },
  '政企客户': {
    entities: [
      { id: '9', name: '政府机构', type: '客户类型' },
      { id: '10', name: '大型企业', type: '客户类型' },
      { id: '11', name: '中小企业', type: '客户类型' },
    ],
    relations: [
      { source: '政企客户', target: '政府机构', label: '包括' },
      { source: '政企客户', target: '大型企业', label: '包括' },
      { source: '政企客户', target: '中小企业', label: '包括' },
    ],
    expandedQuery: '政企客户 政府机构 大型企业 中小企业',
  },
}

// 实体识别mock（简单规则匹配）
const knownEntities = ['小微ICT', 'CRM系统', '政企客户', '电信网络', '省客支', '校园客户']

export function mockRecognizeEntities(query: string): string[] {
  const found: string[] = []
  for (const entity of knownEntities) {
    if (query.includes(entity)) {
      found.push(entity)
    }
  }
  return found
}

// 图谱查询mock
export function mockQueryGraph(entity: string): GraphExpansionResult | null {
  return mockGraphData[entity] || null
}
