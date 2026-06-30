-- Test data for H2
MERGE INTO sys_config (id, config_key, config_value, config_type, description, is_default) VALUES
('c1', 'kb.parse.chunk_size', '512', 'parse', '文档切片大小', 1),
('c2', 'kb.retrieval.top_k', '10', 'retrieval', '检索返回条数', 1),
('c3', 'kb.qa.model', 'qwen3-72b', 'qa', '问答模型', 1);

MERGE INTO wf_template (id, name, category, description, config, is_builtin) VALUES
('t1', '智能问答工作流', '问答', '意图识别→知识库检索→LLM回答', '{}', 1),
('t2', '多轮对话工作流', '对话', '上下文管理→意图识别→知识检索→回答生成', '{}', 1);
