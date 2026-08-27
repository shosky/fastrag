/**
 * FastRAG 前端运行时配置。
 *
 * <p>OnlyOffice URL 来自后端 {@code GET /api/system/config} 之类的统一配置接口（暂未实现，
 * 因此前端通过 {@code import.meta.env.VITE_ONLYOFFICE_URL} 读取 .env 配置；缺省走
 * localhost:8082 的 docker compose 默认端口）。
 */

/**
 * OnlyOffice Document Server URL（用于动态注入 api.js）。
 *
 * <p>默认跟随页面主机名：Windows 上 Docker Desktop 的 [::1] 转发（wslrelay）偶发失效，
 * 固定 'localhost' 时浏览器会优先走 IPv6 坏链路；改为取 {@code location.hostname}
 * 后，用户改用 http://127.0.0.1:3000 访问前端即自动绕开，局域网部署亦自洽
 * （前提：OO 的 8082 端口在访问主机上可直达）。也可用 VITE_ONLYOFFICE_URL 显式覆盖。</p>
 */
export const onlyofficeConfig = {
  url:
    (import.meta.env.VITE_ONLYOFFICE_URL as string | undefined) ||
    `${location.protocol}//${location.hostname}:8082`,
  /** api.js 资源路径（OO Document Server 标准约定） */
  get apiScriptUrl(): string {
    return `${this.url}/web-apps/apps/api/documents/api.js`
  },
}

/** FastRAG 前端需要识别的 Office 扩展名集合（与 OnlyOfficeServiceImpl.SUPPORTED_EXTS 保持一致） */
export const officeExtensions = new Set([
  'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx',
])

/** 判断文件名是否为 OnlyOffice 支持的 Office 文件 */
export function isOfficeFile(name: string): boolean {
  if (!name) return false
  const dot = name.lastIndexOf('.')
  if (dot < 0 || dot === name.length - 1) return false
  return officeExtensions.has(name.substring(dot + 1).toLowerCase())
}
