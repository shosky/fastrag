"""代码审查辅助工具"""

import ast
import re
from typing import List, Dict

def check_python_style(source: str) -> List[Dict]:
    """
    检查 Python 代码风格问题
    
    Returns:
        [{"line": int, "severity": "warning"|"error", "message": str}, ...]
    """
    issues = []
    tree = ast.parse(source)
    
    for node in ast.walk(tree):
        # 检查函数是否有 docstring
        if isinstance(node, (ast.FunctionDef, ast.ClassDef)):
            if not ast.get_docstring(node):
                issues.append({
                    "line": node.lineno,
                    "severity": "warning",
                    "message": f"{node.name} 缺少 docstring"
                })
        
        # 检查裸 except
        if isinstance(node, ast.ExceptHandler):
            if node.type is None:
                issues.append({
                    "line": node.lineno,
                    "severity": "error",
                    "message": "禁止使用裸 except，应指定异常类型"
                })
    
    return issues
