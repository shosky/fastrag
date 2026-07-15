"""搜索结果格式化工具"""

from typing import Dict, List
from datetime import datetime

def format_search_response(query: str, results: List[Dict], include_answer: bool = False) -> str:
    """
    将搜索 API 返回的结果格式化为结构化输出
    
    Args:
        query: 原始查询
        results: 搜索结果列表，每项含 title/url/content/score
        include_answer: 是否包含 LLM 摘要
    """
    lines = [f"## {query}", ""]
    
    for i, r in enumerate(results, 1):
        lines.append(f"### {i}. {r.get('title', '无标题')}")
        lines.append(f"来源：<cite source=\"{r.get('url', '#')}\" type=\"url\">{r.get('content', '')[:200]}</cite>")
        lines.append("")
    
    return "\n".join(lines)
