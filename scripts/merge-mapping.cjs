const fs = require('fs');

// Read both files
const main = fs.readFileSync('D:/Workspace/java/github/rag/fastrag/docs/功能点对应表.md', 'utf-8');
const sup = fs.readFileSync('D:/Workspace/java/github/rag/fastrag/docs/功能点语义匹配补充表.md', 'utf-8');

// Parse semantic matches from supplement file
// Format: | 序号 | 功能点 | 语义匹配 | 原因 |
const semanticMap = {};
const supLines = sup.split('\n');
for (const line of supLines) {
  // Match lines like: | 220 | 用户反馈修改/删除 | 应用→运营中心→反馈管理 | ... |
  const m = line.match(/^\|\s*(\d[\d\-]*)\s*\|([^|]*)\|([^|]*)\|([^|]*)\|/);
  if (m) {
    const id = m[1].trim();
    const target = m[3].trim();
    const reason = m[4].trim();
    if (target && target !== '语义匹配' && !target.includes('功能点')) {
      // Handle ranges like 4673-4708 or 2707-2711
      if (id.includes('-') && !id.startsWith('4')) {
        // skip header-like ranges
      }
      semanticMap[id] = { target, reason };
    }
  }
  // Also match range patterns like: | 4673-4708 | ... |
  const rm = line.match(/^\|\s*(\d+)\s*-\s*(\d+)\s*\|([^|]*)\|([^|]*)\|([^|]*)\|/);
  if (rm) {
    const start = parseInt(rm[1]);
    const end = parseInt(rm[2]);
    const target = rm[4].trim();
    const reason = rm[5].trim();
    if (target && target !== '语义匹配' && !target.includes('功能点')) {
      for (let i = start; i <= end; i++) {
        semanticMap[String(i)] = { target, reason };
      }
    }
  }
}

// Also parse the more detailed tables from the supplement
// Look for patterns like: | 4925 | 触发器（新增） | 应用→应用编辑器→技能配置 | ... |
const detailLines = sup.split('\n');
for (const line of detailLines) {
  const m = line.match(/^\|\s*(\d{3,5})\s*\|([^|]*)\|([^|]*)\|([^|]*)\|/);
  if (m) {
    const id = m[1].trim();
    const target = m[3].trim();
    const reason = m[4].trim();
    if (target && !['功能点', '语义匹配', '原因', '功能点描述'].includes(target) && !target.includes('功能点描述')) {
      semanticMap[id] = { target, reason };
    }
  }
}

// Process main file
const mainLines = main.split('\n');
let exactCount = 0;
let semanticCount = 0;
let noMatchCount = 0;

const mergedLines = mainLines.map(line => {
  // Match table rows with "无" in the 5th column
  // Pattern: | 数字 | ... | ... | ... | 无 | ... |
  const m = line.match(/^(\|\s*\d[\d\-]*\s*\|)([^|]*\|)([^|]*\|)([^|]*\|)\s*无\s*\|([^|]*\|)\s*$/);
  if (m) {
    const id = line.match(/\|\s*(\d[\d\-]*)\s*\|/)[1].trim();
    // Check semantic map - try exact match first, then range
    let sem = semanticMap[id];
    if (!sem) {
      // Try to find by checking if id falls in any range
      for (const [key, val] of Object.entries(semanticMap)) {
        if (key.includes('-')) {
          const [s, e] = key.split('-').map(Number);
          const num = parseInt(id);
          if (num >= s && num <= e) {
            sem = val;
            break;
          }
        }
      }
    }
    if (sem) {
      semanticCount++;
      return `${m[1]}${m[2]}${m[3]}${m[4]} 【语义】${sem.target} | ${sem.reason} |`;
    } else {
      noMatchCount++;
      return line; // Keep as "无"
    }
  }
  // Count exact matches (non-无 rows in table)
  if (line.match(/^\|\s*\d[\d\-]*\s*\|/) && !line.includes('无')) {
    exactCount++;
  }
  return line;
});

// Generate header with stats
const header = `# 知识管理系统功能点完整对应表

> 本文档将需求功能点清单中的每个功能点映射到系统实际功能位置。
> - 无标记：功能精确匹配，系统已有对应实现
> - 【语义】：功能语义相似，实现程度或交互细节有差异
> - 无对应：系统中确实没有相似功能

---

## 统计汇总

| 分类 | 数量 | 说明 |
|------|------|------|
| 总功能点 | 490 | 需求文档中的全部功能点 |
| 精确匹配 | ${exactCount} | 系统已有完全对应的功能 |
| 语义匹配 | ${semanticCount} | 功能语义相似，可复用现有功能 |
| 无对应 | ${noMatchCount} | 系统中确实没有相似功能 |

---

`;

const output = header + mergedLines.join('\n');
fs.writeFileSync('D:/Workspace/java/github/rag/fastrag/docs/功能点完整对应表.md', output, 'utf-8');
console.log(`Done! Exact: ${exactCount}, Semantic: ${semanticCount}, No match: ${noMatchCount}`);
