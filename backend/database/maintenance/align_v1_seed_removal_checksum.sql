-- 仅适用于已执行旧 V1 的数据库；执行前备份并核对部署包为本次版本。
-- 只确认本次移除演示 INSERT 的已知校验和变更，不重跑 V1，不修改业务数据。
-- 新库和 baseline=5 的旧库无需执行此脚本。不要用它覆盖其他迁移错误。
START TRANSACTION;
UPDATE flyway_schema_history
SET checksum = 1243288876
WHERE version = '1'
  AND script = 'V1__baseline_schema.sql'
  AND type = 'SQL'
  AND success = 1
  AND checksum = 120434260;
SELECT ROW_COUNT() AS updated_v1_rows;
SELECT version, script, checksum, success FROM flyway_schema_history WHERE version = '1';
COMMIT;
