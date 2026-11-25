ALTER TABLE models
    ALTER COLUMN api_key TYPE VARCHAR(1200);

COMMENT ON COLUMN models.api_key IS '模型 API Key 密文，使用 enc:v1: 前缀标识 AES-GCM 加密版本';
