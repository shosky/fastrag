// 假 OpenAI 兼容 LLM（自测用 v2）：普通 JSON 响应、逐行分拍写入，确保 bodyToFlux(String) 按行切分
import http from 'node:http'

let seq = 0

const server = http.createServer((req, res) => {
  if (req.method === 'POST' && req.url.includes('/v1/chat/completions')) {
    let body = ''
    req.on('data', (c) => (body += c))
    req.on('end', () => {
      seq++
      const pair = (i) =>
        `{"question":"自测问题${seq}号第${i}条的标准问法是什么","answer":"自测答案${seq}-${i}号的内容说明"}`
      const content = `[${pair(1)},${pair(2)},${pair(3)}]`
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.write(JSON.stringify({ choices: [{ delta: { content } }], model: 'fake' }) + '\n')
      setTimeout(() => {
        res.write('[DONE]\n')
        res.end()
        console.log(`[fake-llm] served request #${seq}`)
      }, 20)
    })
    return
  }
  res.writeHead(200, { 'Content-Type': 'application/json' })
  res.end('{"ok":true}')
})

server.listen(11434, () => console.log('[fake-llm v2] listening on 11434'))
