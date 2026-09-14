-- 查询重写/扩写规则示例数据（幂等：固定主键 + INSERT IGNORE）
USE fastrag2;

INSERT IGNORE INTO query_rule (id, name, description, rule_type, pattern, action, enabled, priority) VALUES
('qr_seed_r1', 'wifi→WiFi组网', '口语/简写转规范术语', 'rewrite', 'wifi', 'WiFi 组网', 1, 1),
('qr_seed_r2', '装监控→视频监控安装', '口语转规范术语', 'rewrite', '装监控', '视频监控 安装', 1, 1),
('qr_seed_r3', '拉网线→综合布线', '口语转规范术语', 'rewrite', '拉网线', '综合布线', 1, 1),
('qr_seed_r4', '多少钱→价格报价', '口语转规范术语', 'rewrite', '多少钱', '价格 报价 费用', 1, 2),
('qr_seed_r5', '咋办→办理流程', '口语转规范术语', 'rewrite', '咋办', '办理流程', 1, 2),
('qr_seed_r6', '价格扩写', '价格类查询追加上下文词', 'expand', '价格', '价格 报价 费用 方案 套餐', 1, 3),
('qr_seed_r7', '安装扩写', '安装类查询追加上下文词', 'expand', '安装', '安装 部署 施工 调试', 1, 3),
('qr_seed_r8', '故障扩写', '故障类查询追加上下文词', 'expand', '故障', '故障 问题 报修 维修', 1, 3),
('qr_seed_r9', '合同扩写', '合同类查询追加上下文词（默认停用）', 'expand', '合同', '合同 协议 条款 签约', 0, 4),
('qr_seed_r10', '总结小微ICT→帮忙总结内容', '指令口语化重写', 'rewrite', '总结小微ICT', '帮忙总结小微ICT的内容', 1, 1);
