---
name: 代码生成
slug: code-generate
description: 根据自然语言需求生成符合最佳实践的代码片段，支持多种编程语言。含代码审查、测试生成、重构建议能力。
version: 1.1.0
category: generation
icon: "#67c23a"
tool_dependencies: []
skill_dependencies: []
mcp_dependencies: []
---

# 代码生成技能

当用户请求编写、审查、重构或解释代码时使用此技能。核心原则：**先明确需求边界 → 再选技术方案 → 后实现代码 → 附带测试和说明**。

## 编排流程

### 1. 澄清需求

需求模糊时，用 `ask_user_question` 澄清 1-2 个关键问题：

- 目标语言/框架版本
- 输入输出格式
- 性能要求 / 约束条件
- 是否需要处理边界情况

已明确的需求不要追问。

### 2. 技术选型

| 场景 | 推荐方案 |
|------|---------|
| REST API | Python FastAPI / Java Spring Boot + 输入校验 + OpenAPI |
| CLI 工具 | Python argparse / click + 错误处理 + --help |
| 数据处理 | Pandas 脚本 + 异常处理 + 进度条 tqdm |
| 前端组件 | Vue 3 Composition API + TypeScript + 单元测试 |
| 批处理脚本 | Bash + 前置检查 + 日志 + 幂等设计 |

### 3. 代码实现规范

```python
# 每个函数/类必须有 docstring（用途、参数、返回值、异常）
def process_data(input_path: str, output_path: str | None = None) -> dict:
    """
    处理数据文件并返回统计结果
    
    Args:
        input_path: 输入文件路径
        output_path: 可选输出路径，指定则写入结果
        
    Returns:
        dict: 包含统计指标的字典
        
    Raises:
        FileNotFoundError: 输入文件不存在
        ValueError: 数据格式不合法
    """
```

### 4. 代码输出模板

````
## 功能描述
[一句话说明]

## 技术栈
- 语言: Python 3.12
- 依赖: pandas, requests

## 实现代码
```python
[完整可运行的代码]
```

## 使用示例
```bash
python script.py --input data.csv --output result.json
```

## 注意事项
- [边界情况说明]
- [已知限制]

## 测试用例
```python
def test_process_data():
    assert process_data("test.csv")["count"] > 0
```
````

## 输出约束

- 生成的代码必须是**完整可运行**的，不仅仅是片段
- 包含必要的 import 和入口点（`if __name__ == "__main__"`）
- 包含错误处理和边界情况
- 附上使用示例和测试用例
- 标注已知限制和兼容性说明
- 参考 `references/style-guide/` 中的语言风格指南

## 参考资源

- `references/style-guide/python-guide.md` — Python 编码规范
- `references/style-guide/vue-guide.md` — Vue 3 组件规范
