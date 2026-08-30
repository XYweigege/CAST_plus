-- ============================================================
-- 迁移脚本：feedback 表新增 is_analyzed 归因状态列
-- 适用：已初始化的存量库（全新库由 schema.sql 直接建好，无需执行）
-- 幂等：重复执行不报错
-- ============================================================

USE voc_insight;

-- 1. 加列（不存在时才加）
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'voc_insight'
      AND TABLE_NAME = 'feedback'
      AND COLUMN_NAME = 'is_analyzed'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE feedback ADD COLUMN is_analyzed TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否已归因（0=待归因，1=已归因）'' AFTER is_reviewed',
    'SELECT ''is_analyzed 列已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 加索引（不存在时才加）
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'voc_insight'
      AND TABLE_NAME = 'feedback'
      AND INDEX_NAME = 'idx_feedback_is_analyzed'
);
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE feedback ADD KEY idx_feedback_is_analyzed (is_analyzed)',
    'SELECT ''idx_feedback_is_analyzed 索引已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 存量数据迁移：已有分析结果的反馈标记为已归因
UPDATE feedback SET is_analyzed = 1 WHERE sentiment != 'neutral' OR urgency != 'info';
