-- 保留旧迁移 checksum，仅锁定仍使用已公开演示密码的原始管理员。
-- 已修改密码、已停用或已删除的账号不参与初始化，避免升级时覆盖人工管理结果。
UPDATE users
SET status = 'disabled',
    metadata = COALESCE(metadata, '{}'::jsonb) || '{"bootstrapPasswordRequired": true}'::jsonb,
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'zc'
  AND password_hash = '$2a$10$g7ZIXZGvNUKQfSnj/.3iOujZHuFsjZSgPDC85j.hQG/otQKpaOIeC'
  AND status = 'enabled'
  AND is_deleted = FALSE;
