-- app_db_binding.allowed_tables 原为 JSON 列，但业务存的是逗号分隔表名字符串，
-- 空串/逗号分隔值都不是合法 JSON，插入报 Invalid JSON text；改为 TEXT。
ALTER TABLE app_db_binding MODIFY COLUMN allowed_tables TEXT NULL;
