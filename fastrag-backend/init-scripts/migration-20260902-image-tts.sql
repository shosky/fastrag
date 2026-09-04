-- FastRAG AI 配图 & TTS 能力迁移脚本
-- 适用日期：2026-09-02
USE fastrag2;

-- 1. kb_knowledge 增加 cover_image 字段
ALTER TABLE kb_knowledge ADD COLUMN cover_image VARCHAR(512) DEFAULT NULL
    COMMENT '封面图 object_key（指向 kb_media_storage.object_key）';

-- 2. kb_media_storage 增加 source 字段，标识媒体来源
ALTER TABLE kb_media_storage ADD COLUMN source VARCHAR(32) DEFAULT 'manual'
    COMMENT 'manual / upload / ai_tts / ai_image_gen';
