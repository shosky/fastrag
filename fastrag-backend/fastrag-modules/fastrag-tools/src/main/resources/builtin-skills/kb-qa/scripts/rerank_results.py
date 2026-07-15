"""检索结果重排序工具"""

from typing import List, Dict
import re


def rerank_by_relevance(query: str, chunks: List[Dict]) -> List[Dict]:
    """
    根据查询与检索片段的相关性重新排序
    
    评分因子:
    1. 关键词命中率 (30%)
    2. 语义相似度分数 (50%)
    3. 文档权威性加分 (20%)
    """
    keywords = set(re.findall(r'\w+', query.lower()))
    
    for chunk in chunks:
        score = chunk.get('score', 0) * 0.5
        content = chunk.get('content', '').lower()
        
        # 关键词命中加分
        hits = sum(1 for kw in keywords if kw in content)
        score += (hits / max(len(keywords), 1)) * 0.3
        
        # 权威性加分：包含正式文档关键词
        if any(w in content for w in ['规定', '办法', '指南', '标准']):
            score += 0.2
        
        chunk['_rerank_score'] = round(score, 3)
    
    return sorted(chunks, key=lambda x: x.get('_rerank_score', 0), reverse=True)
