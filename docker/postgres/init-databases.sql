-- 仅在全新 PostgreSQL 数据卷初始化时执行。两个服务使用独立逻辑数据库。
CREATE DATABASE agent_registry_center;
CREATE DATABASE agent_core;
\connect agent_core
CREATE EXTENSION IF NOT EXISTS vector;
