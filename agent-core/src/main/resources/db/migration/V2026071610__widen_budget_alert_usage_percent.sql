ALTER TABLE budget_alerts ALTER COLUMN usage_percent TYPE NUMERIC(18,2);
COMMENT ON COLUMN budget_alerts.usage_percent IS '预算使用百分比，支持极小限额触发的超大倍率告警';
