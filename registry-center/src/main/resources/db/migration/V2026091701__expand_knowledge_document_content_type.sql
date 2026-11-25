ALTER TABLE knowledge_documents
    ALTER COLUMN content_type TYPE VARCHAR(128);

COMMENT ON COLUMN knowledge_documents.content_type IS '内容 MIME 类型，支持 DOCX 等较长标准 MIME 值';
