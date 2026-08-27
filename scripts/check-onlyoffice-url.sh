#!/bin/bash
# 用法: bash scripts/check-onlyoffice-url.sh <KB_ID> <FILE_ID> <JWT_TOKEN>
# KB_ID 和 FILE_ID 在浏览器地址栏 / 数据库里都能查到
# JWT_TOKEN 用浏览器 DevTools → Application → Local Storage → token 复制

set -e
KB_ID="${1:-YOUR_KB_ID}"
FILE_ID="${2:-YOUR_FILE_ID}"
TOKEN="${3:-YOUR_JWT_TOKEN}"

if [ "$KB_ID" = "YOUR_KB_ID" ] || [ "$TOKEN" = "YOUR_JWT_TOKEN" ]; then
    echo "用法: $0 <KB_ID> <FILE_ID> <JWT_TOKEN>"
    echo ""
    echo "获取 JWT_TOKEN 步骤:"
    echo "  1. 浏览器登录 FastRAG"
    echo "  2. F12 → Application → Local Storage → http://localhost:8081"
    echo "  3. 找到 key='token' 的值(就是 JWT)"
    exit 1
fi

echo "=== 拉取 OnlyOffice 编辑器配置 ==="
RESP=$(curl --noproxy '*' -s \
    -H "Authorization: Bearer $TOKEN" \
    "http://localhost:8081/api/kb/${KB_ID}/files/${FILE_ID}/onlyoffice/config")

echo "$RESP" | python -m json.tool 2>/dev/null || echo "$RESP"
echo ""

# 提取 document.url 字段
URL=$(echo "$RESP" | grep -o '"url":"[^"]*"' | head -1 | sed 's/"url":"//; s/"$//')
echo "=== document.url ==="
echo "$URL"
echo ""

# 检查 key 参数
if echo "$URL" | grep -q "&key="; then
    echo "✓ URL 已包含 &key= 参数"
else
    echo "✗ URL 缺失 &key= 参数(后端代码需要重启)"
fi
