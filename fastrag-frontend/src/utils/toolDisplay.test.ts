import { describe, it, expect } from 'vitest'
import { resolveToolDisplayType, summarizeToolCall, mcpStructuredRows } from './toolDisplay'

// 契约锚点：docs/contract/ui.md 特性 19「工具结果结构化渲染」——
// 后端结构化通道 data 字段（display_type 两类）+ 工具名后缀的双通道渲染路由；
// summarizeToolCall 供 ToolCallRenderer 收起态常驻摘要。
// 三批扩展：mcp / skill_md 兜底形态（按工具名识别）+ mcpStructuredRows 轻结构化。

describe('resolveToolDisplayType（三段优先级）', () => {
  it('第一段：result.data.display_type 显式标记优先于工具名', () => {
    expect(
      resolveToolDisplayType('some_unknown_tool', {
        data: { display_type: 'database_query' },
      })
    ).toBe('database_query')
    expect(
      resolveToolDisplayType('weird_name', {
        data: { display_type: 'knowledge_chunks' },
      })
    ).toBe('knowledge_chunks')
  })

  it("data.display_type 显式标记压过相反的工具名后缀（display_type 最可信）", () => {
    expect(
      resolveToolDisplayType('query_kb', {
        data: { display_type: 'database_query' },
      })
    ).toBe('database_query')
  })

  it('未知 display_type 值降级，继续走工具名判定', () => {
    expect(
      resolveToolDisplayType('query_kb', {
        data: { display_type: 'future_type' },
      })
    ).toBe('knowledge_chunks')
    expect(
      resolveToolDisplayType('other_tool', {
        data: { display_type: 'future_type' },
      })
    ).toBe('generic')
  })

  it("第二段：工具名后缀 _query_database → database_query（无 data 的旧记录）", () => {
    // 运行时真实命名 {dbId}_query_database（DatabaseMiddleware.java:72）
    expect(resolveToolDisplayType('db-001_query_database')).toBe('database_query')
    expect(resolveToolDisplayType('mysql_query_database')).toBe('database_query')
  })

  it("裸名 query_database 不含 _ 前缀，不命中后缀规则 → generic（对齐 spec 字面：后缀匹配）", () => {
    expect(resolveToolDisplayType('query_database')).toBe('generic')
  })

  it("第三段：工具名 query_kb → knowledge_chunks", () => {
    expect(resolveToolDisplayType('query_kb')).toBe('knowledge_chunks')
    expect(resolveToolDisplayType('query_kb', null)).toBe('knowledge_chunks')
  })

  it('其余工具 → generic 回退', () => {
    expect(resolveToolDisplayType('web_search')).toBe('generic')
    expect(resolveToolDisplayType('')).toBe('generic')
    expect(resolveToolDisplayType('query_kb_like_but_not')).toBe('generic')
  })
})

describe('summarizeToolCall（收起态一行摘要）', () => {
  it('pending（无 result）=「执行中…」', () => {
    expect(summarizeToolCall('query_database')).toBe('执行中…')
    expect(summarizeToolCall('any_tool', null)).toBe('执行中…')
  })

  it('失败 = error 文本', () => {
    expect(
      summarizeToolCall('query_database', { success: false, error: '连接超时', output: '' })
    ).toBe('连接超时')
  })

  it('失败且无 error 时兜底 output（历史落库把 error 写进 output）', () => {
    expect(
      summarizeToolCall('query_database', { success: false, output: 'Table not found' })
    ).toBe('Table not found')
  })

  it('失败 error 超 40 字截断加省略号', () => {
    const long = 'e'.repeat(50)
    const summary = summarizeToolCall('any_tool', { success: false, error: long })
    expect(summary).toBe('e'.repeat(40) + '...')
    expect(summary.length).toBe(43)
  })

  it('database_query =「查询返回 {row_count} 行」', () => {
    expect(
      summarizeToolCall('query_database', {
        success: true,
        output: '...',
        data: { display_type: 'database_query', row_count: 12 },
      })
    ).toBe('查询返回 12 行')
  })

  it('database_query 无 row_count 时回退「执行完成」', () => {
    expect(
      summarizeToolCall('query_database', { success: true, output: 'ok' })
    ).toBe('执行完成')
  })

  it('query_kb =「命中 {chunks.length} 条分片」', () => {
    expect(
      summarizeToolCall('query_kb', {
        success: true,
        output: '...',
        data: {
          display_type: 'knowledge_chunks',
          chunks: [{ content: 'a' }, { content: 'b' }, { content: 'c' }],
        },
      })
    ).toBe('命中 3 条分片')
  })

  it('query_kb 无 chunks 时回退「执行完成」', () => {
    expect(summarizeToolCall('query_kb', { success: true, output: 'ok' })).toBe('执行完成')
  })

  it('成功 generic =「执行完成」', () => {
    expect(summarizeToolCall('web_search', { success: true, output: 'done', durationMs: 120 })).toBe(
      '执行完成'
    )
  })

  it('三批兜底形态无结构化 data：mcp / skill_md 摘要同归「执行完成」', () => {
    expect(summarizeToolCall('mcp_file_search', { success: true, output: '{"n":1}' })).toBe('执行完成')
    expect(summarizeToolCall('skill_read', { success: true, output: '# 文档' })).toBe('执行完成')
  })
})

describe('resolveToolDisplayType（三批兜底：mcp / skill_md）', () => {
  it('工具名含 mcp → mcp（前缀与中缀变体都命中）', () => {
    expect(resolveToolDisplayType('mcp_file_search')).toBe('mcp')
    expect(resolveToolDisplayType('srv1_mcp_search')).toBe('mcp')
  })

  it('工具名含 skill → skill_md', () => {
    expect(resolveToolDisplayType('skill_read')).toBe('skill_md')
    expect(resolveToolDisplayType('run_skill_report')).toBe('skill_md')
  })

  it('read_file / read 裸名 → skill_md；含 read 子串的其他名不误伤', () => {
    expect(resolveToolDisplayType('read_file')).toBe('skill_md')
    expect(resolveToolDisplayType('read')).toBe('skill_md')
    expect(resolveToolDisplayType('spreadsheet_export')).toBe('generic')
    expect(resolveToolDisplayType('thread_dump')).toBe('generic')
  })

  it('data.display_type 显式标记仍最可信（压过 mcp/skill 名称判定）', () => {
    expect(
      resolveToolDisplayType('mcp_query', { data: { display_type: 'database_query' } })
    ).toBe('database_query')
    expect(
      resolveToolDisplayType('skill_scan', { data: { display_type: 'knowledge_chunks' } })
    ).toBe('knowledge_chunks')
  })

  it('_query_database 后缀通道优先于兜底名称判定；query_kb 仅认裸名（既有全等语义）', () => {
    expect(resolveToolDisplayType('mcp_db_query_database')).toBe('database_query')
    expect(resolveToolDisplayType('query_kb')).toBe('knowledge_chunks')
    expect(resolveToolDisplayType('skill_query_kb')).toBe('skill_md')
  })
})

describe('mcpStructuredRows（MCP 结果轻结构化，不强解析）', () => {
  it('对象 output → 顶层键值行（原始类型直接字符串化）', () => {
    expect(mcpStructuredRows('{"name":"a","count":2,"ok":true}')).toEqual([
      { key: 'name', value: 'a' },
      { key: 'count', value: '2' },
      { key: 'ok', value: 'true' },
    ])
  })

  it('数组 output → 索引行，嵌套值 JSON 字符串化', () => {
    expect(mcpStructuredRows('[1,"x",{"k":1}]')).toEqual([
      { key: '[0]', value: '1' },
      { key: '[1]', value: 'x' },
      { key: '[2]', value: '{"k":1}' },
    ])
  })

  it('不可解析 / 标量 / 空串 → undefined（调用方回退原始输出 pre）', () => {
    expect(mcpStructuredRows('plain text output')).toBeUndefined()
    expect(mcpStructuredRows('"just a string"')).toBeUndefined()
    expect(mcpStructuredRows('42')).toBeUndefined()
    expect(mcpStructuredRows('')).toBeUndefined()
  })

  it('maxRows 截断（防超大结果撑爆卡片）', () => {
    const rows = mcpStructuredRows('[1,2,3,4,5]', 3)
    expect(rows).toHaveLength(3)
    expect(rows?.[2]).toEqual({ key: '[2]', value: '3' })
  })
})
