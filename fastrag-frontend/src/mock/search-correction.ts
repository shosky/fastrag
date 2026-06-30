// ===========================================================================
// 搜索纠错 + 拼音映射 mock 数据层
//
// 常见错别字表 + 拼音首字母映射，供检索前纠错和拼音搜索。
// ===========================================================================

/** 常见错别字纠正表（错误 → 正确） */
const TYPO_CORRECTIONS: Record<string, string> = {
  '发飘': '发票',
  '发瞟': '发票',
  '报销单': '报销单',
  '合铜': '合同',
  '和同': '合同',
  '办工': '办公',
  '公做': '工作',
  '自寻': '咨询',
  '之寻': '咨询',
  '知识苦': '知识库',
  '知识裤': '知识库',
  '知始库': '知识库',
  '监控事': '监控室',
  '摄相头': '摄像头',
  '摄象头': '摄像头',
  '光千': '光纤',
  '光迁': '光纤',
  '布限': '布线',
  '步线': '布线',
  '组网落': '组网络',
  '无现网络': '无线网络',
  'wifi': 'WiFi',
  'Wifi': 'WiFi',
  'WIFI': 'WiFi',
  '小微it': '小微ICT',
  '小微itc': '小微ICT',
  '小微ict': '小微ICT',
  '全光租网': '全光组网',
  '全光祖网': '全光组网',
  '标品和同': '标品合同',
  '标品合铜': '标品合同',
  '总接': '总结',
  '总接下': '总结下',
  "ic't": 'ICT',
  "ic 't": 'ICT',
}

/** 拼音首字母 → 中文词映射 */
const PINYIN_ABBREVIATIONS: Record<string, string[]> = {
  'fp': ['发票'],
  'ht': ['合同', '后台'],
  'kh': ['客户', '开户'],
  'cp': ['产品'],
  'fw': ['服务', '访问'],
  'aq': ['安全'],
  'bg': ['报告', '办公'],
  'sj': ['数据', '时间', '事件'],
  'jk': ['监控', '健康'],
  'sjk': ['数据库', '数据集'],
  'wlaq': ['网络安全'],
  'zsk': ['知识库'],
  'xtsz': ['系统设置'],
  'xgwz': ['相关文章'],
  'wjwt': ['网络安全'],
  'xmic': ['小微ICT'],
  'xwic': ['小微ICT'],
  'ggzw': ['全光组网'],
  'spjk': ['视频监控'],
  'zhbx': ['综合布线'],
  'jftg': ['机房托管'],
}

/**
 * 自动纠错：对 query 中的错别字进行纠正。
 * 返回 { corrected, corrections }，corrections 记录了每个纠正项。
 */
export function correctQuery(query: string): {
  corrected: string
  corrections: Array<{ original: string; corrected: string }>
} {
  const corrections: Array<{ original: string; corrected: string }> = []
  let corrected = query

  // 按错误词长度降序匹配，避免短词先替换
  const sortedTypos = Object.keys(TYPO_CORRECTIONS).sort((a, b) => b.length - a.length)

  for (const typo of sortedTypos) {
    if (corrected.includes(typo)) {
      const fix = TYPO_CORRECTIONS[typo]
      corrected = corrected.replace(typo, fix)
      corrections.push({ original: typo, corrected: fix })
    }
  }

  return { corrected, corrections }
}

/**
 * 拼音搜索：将拼音首字母输入匹配到中文词。
 * 输入 "fp" → 返回 ["发票"]
 */
export function matchPinyin(input: string): string[] {
  const lower = input.toLowerCase().trim()
  return PINYIN_ABBREVIATIONS[lower] || []
}

/**
 * 综合纠错：纠错 + 拼音匹配 + 返回建议。
 * 如果纠错后结果与原 query 不同，返回建议；否则检查拼音匹配。
 */
export function getSuggestion(query: string): {
  suggestedQuery: string | null
  reason: string
} {
  // 1. 检查错别字
  const { corrected, corrections } = correctQuery(query)
  if (corrections.length > 0) {
    return {
      suggestedQuery: corrected,
      reason: `检测到错别字：${corrections.map((c) => `${c.original}→${c.corrected}`).join('、')}`,
    }
  }

  // 2. 检查拼音匹配
  const pinyinMatches = matchPinyin(query)
  if (pinyinMatches.length > 0) {
    return {
      suggestedQuery: pinyinMatches[0],
      reason: `拼音匹配：${query} → ${pinyinMatches.join('、')}`,
    }
  }

  return { suggestedQuery: null, reason: '' }
}
