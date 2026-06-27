import { checkApiPermission } from './interceptor'

export interface PluginItem {
  id: string; name: string; description: string; type: 'custom' | 'uploaded' | 'json_import'
  tools: PluginTool[]; status: 'active' | 'inactive'; version: string; creator: string; createdAt: string
}
export interface PluginTool {
  name: string; description: string; apiEndpoint: string; method: 'GET' | 'POST' | 'PUT' | 'DELETE'
  parameters: { name: string; type: string; required: boolean; description?: string }[]
}

export interface DatabaseConnection {
  id: string; name: string; description: string; type: 'mysql' | 'postgresql' | 'mongodb' | 'redis'
  host: string; port: number; database: string; username: string
  tables: DatabaseTable[]; status: 'connected' | 'disconnected' | 'error'; createdAt: string
}
export interface DatabaseTable {
  name: string; columns: { name: string; type: string; nullable: boolean; primaryKey: boolean }[]; rowCount: number
}

export const PLUGIN_TYPE_LABELS: Record<string, string> = { custom: '自定义', uploaded: '上传', json_import: 'JSON导入' }
export const PLUGIN_TYPE_COLORS: Record<string, string> = { custom: 'primary', uploaded: 'success', json_import: 'warning' }
export const DB_TYPE_LABELS: Record<string, string> = { mysql: 'MySQL', postgresql: 'PostgreSQL', mongodb: 'MongoDB', redis: 'Redis' }
export const DB_STATUS_LABELS: Record<string, string> = { connected: '已连接', disconnected: '未连接', error: '连接错误' }
export const DB_STATUS_COLORS: Record<string, string> = { connected: 'success', disconnected: 'info', error: 'danger' }

let pluginStore: PluginItem[] = []
let dbStore: DatabaseConnection[] = []
let seq = 100

function initStore() {
  if (pluginStore.length > 0) return
  const now = new Date().toISOString()

  pluginStore = Array.from({ length: 6 }, (_, i) => ({
    id: `plg-${i + 1}`, name: `插件${i + 1}`, description: `插件描述${i + 1}`,
    type: (['custom', 'uploaded', 'json_import'] as const)[i % 3],
    tools: [
      { name: `工具${i + 1}A`, description: `工具描述${i + 1}A`, apiEndpoint: `https://api.example.com/tool${i + 1}a`, method: 'POST' as const,
        parameters: [{ name: 'input', type: 'string', required: true, description: '输入参数' }] },
    ],
    status: i % 3 === 0 ? 'inactive' : 'active', version: `v1.${i}.0`, creator: 'admin', createdAt: now,
  }))

  dbStore = Array.from({ length: 4 }, (_, i) => ({
    id: `db-${i + 1}`, name: `数据库${i + 1}`, description: `数据库描述${i + 1}`,
    type: (['mysql', 'postgresql', 'mongodb', 'redis'] as const)[i],
    host: 'localhost', port: [3306, 5432, 27017, 6379][i], database: `db_${i + 1}`, username: 'admin',
    tables: Array.from({ length: 3 }, (_, j) => ({
      name: `table_${j + 1}`, rowCount: Math.floor(Math.random() * 10000),
      columns: [
        { name: 'id', type: 'int', nullable: false, primaryKey: true },
        { name: 'name', type: 'varchar', nullable: false, primaryKey: false },
        { name: 'created_at', type: 'datetime', nullable: true, primaryKey: false },
      ],
    })),
    status: i % 3 === 0 ? 'disconnected' : i % 3 === 1 ? 'error' : 'connected', createdAt: now,
  }))
}

export function getPluginList(params?: { page?: number; pageSize?: number; keyword?: string }) {
  initStore(); let list = [...pluginStore]
  if (params?.keyword) list = list.filter(p => p.name.includes(params.keyword!))
  const total = list.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}
export function getPluginById(id: string): PluginItem | null { initStore(); return pluginStore.find(p => p.id === id) || null }
export function createPlugin(data: Partial<PluginItem>): PluginItem {
  checkApiPermission('app:write'); initStore()
  const item: PluginItem = { id: `plg-${++seq}`, name: data.name || '', description: data.description || '', type: data.type || 'custom', tools: data.tools || [], status: 'active', version: 'v1.0.0', creator: 'admin', createdAt: new Date().toISOString() }
  pluginStore.push(item); return item
}
export function updatePlugin(id: string, data: Partial<PluginItem>): PluginItem | null {
  checkApiPermission('app:write'); initStore()
  const idx = pluginStore.findIndex(p => p.id === id); if (idx === -1) return null
  pluginStore[idx] = { ...pluginStore[idx], ...data }; return pluginStore[idx]
}
export function deletePlugin(id: string): boolean {
  checkApiPermission('app:delete'); initStore()
  const idx = pluginStore.findIndex(p => p.id === id); if (idx === -1) return false
  pluginStore.splice(idx, 1); return true
}

export function getDatabaseList(params?: { page?: number; pageSize?: number; keyword?: string }) {
  initStore(); let list = [...dbStore]
  if (params?.keyword) list = list.filter(d => d.name.includes(params.keyword!))
  const total = list.length; const page = params?.page || 1; const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}
export function getDatabaseById(id: string): DatabaseConnection | null { initStore(); return dbStore.find(d => d.id === id) || null }
export function createDatabase(data: Partial<DatabaseConnection>): DatabaseConnection {
  checkApiPermission('app:write'); initStore()
  const item: DatabaseConnection = { id: `db-${++seq}`, name: data.name || '', description: data.description || '', type: data.type || 'mysql', host: data.host || '', port: data.port || 3306, database: data.database || '', username: data.username || '', tables: [], status: 'disconnected', createdAt: new Date().toISOString() }
  dbStore.push(item); return item
}
export function updateDatabase(id: string, data: Partial<DatabaseConnection>): DatabaseConnection | null {
  checkApiPermission('app:write'); initStore()
  const idx = dbStore.findIndex(d => d.id === id); if (idx === -1) return null
  dbStore[idx] = { ...dbStore[idx], ...data }; return dbStore[idx]
}
export function deleteDatabase(id: string): boolean {
  checkApiPermission('app:delete'); initStore()
  const idx = dbStore.findIndex(d => d.id === id); if (idx === -1) return false
  dbStore.splice(idx, 1); return true
}
