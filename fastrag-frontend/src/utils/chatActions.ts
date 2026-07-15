import { Document, Packer, Paragraph, TextRun } from 'docx'
import { saveAs } from 'file-saver'
import { marked } from 'marked'

/**
 * 复制文本到剪贴板
 */
export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    // fallback: textarea
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    return true
  }
}

/**
 * 将 markdown 内容下载为 DOCX 文件
 */
export async function downloadAsDocx(content: string, filename: string): Promise<void> {
  if (!content?.trim()) return

  // 简易 markdown → docx 段落转换
  const html = marked.parse(content) as string
  const text = stripHtml(html)

  const lines = text.split('\n').filter((l) => l.trim())
  const paragraphs: Paragraph[] = lines.map(
    (line) =>
      new Paragraph({
        spacing: { after: 120 },
        children: [new TextRun({ text: line, size: 24, font: 'Microsoft YaHei' })],
      })
  )

  const doc = new Document({
    sections: [{ properties: {}, children: paragraphs }],
  })

  const blob = await Packer.toBlob(doc)
  saveAs(blob, `${filename}.docx`)
}

function stripHtml(html: string): string {
  const div = document.createElement('div')
  div.innerHTML = html
  return div.textContent || div.innerText || ''
}
