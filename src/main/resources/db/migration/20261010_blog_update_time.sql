-- 在升级应用前执行；可重复运行。保留已有的非空修改时间。
-- 不使用 ON UPDATE：点赞、评分不能改变最近编辑时间。
SET @has_update_time = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'blog_post' AND COLUMN_NAME = 'update_time');
SET @ddl = IF(@has_update_time = 0,
    'ALTER TABLE blog_post ADD COLUMN update_time DATETIME NULL COMMENT ''文章最后编辑时间''',
    'ALTER TABLE blog_post MODIFY COLUMN update_time DATETIME NULL COMMENT ''文章最后编辑时间''');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
UPDATE blog_post SET update_time = COALESCE(create_time, CURRENT_TIMESTAMP) WHERE update_time IS NULL;
SET @has_recent_index = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'blog_post' AND INDEX_NAME = 'idx_blog_post_recent');
SET @ddl = IF(@has_recent_index = 0,
    'CREATE INDEX idx_blog_post_recent ON blog_post (update_time DESC, id DESC)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
