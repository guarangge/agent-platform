ALTER TABLE knowledge_runtime_documents
    ALTER COLUMN content_type TYPE VARCHAR(128);

COMMENT ON COLUMN knowledge_runtime_documents.content_type IS '文档 MIME 类型快照，支持 DOCX 等较长标准 MIME 值';
