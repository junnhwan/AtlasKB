CREATE DATABASE IF NOT EXISTS atlas_kb
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE atlas_kb;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS chunk_info;
DROP TABLE IF EXISTS document_vectors;
DROP TABLE IF EXISTS file_upload;
DROP TABLE IF EXISTS organization_tags;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'User identifier',
    username VARCHAR(255) NOT NULL UNIQUE COMMENT 'Username',
    password VARCHAR(255) NOT NULL COMMENT 'Encrypted password',
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER' COMMENT 'User role',
    org_tags VARCHAR(255) DEFAULT NULL COMMENT 'Comma separated org tags',
    primary_org VARCHAR(50) DEFAULT NULL COMMENT 'Primary org tag',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User table';

CREATE TABLE organization_tags (
    tag_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY COMMENT 'Org tag id',
    name VARCHAR(100) NOT NULL COMMENT 'Org tag name',
    description TEXT COMMENT 'Description',
    parent_tag VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Parent tag id',
    created_by BIGINT NOT NULL COMMENT 'Creator user id',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    FOREIGN KEY (parent_tag) REFERENCES organization_tags(tag_id) ON DELETE SET NULL,
    FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Organization tag table';

CREATE TABLE file_upload (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    file_md5 VARCHAR(32) NOT NULL COMMENT 'File md5',
    file_name VARCHAR(255) NOT NULL COMMENT 'File name',
    total_size BIGINT NOT NULL COMMENT 'File size',
    status TINYINT NOT NULL DEFAULT 0 COMMENT 'File status',
    user_id VARCHAR(64) NOT NULL COMMENT 'Uploader user id',
    org_tag VARCHAR(50) DEFAULT NULL COMMENT 'Org tag',
    is_public BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Public flag',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    merged_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'Merged time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_md5_user (file_md5, user_id),
    INDEX idx_user (user_id),
    INDEX idx_org_tag (org_tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='File upload table';

CREATE TABLE chunk_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Chunk identifier',
    file_md5 VARCHAR(32) NOT NULL COMMENT 'File md5',
    chunk_index INT NOT NULL COMMENT 'Chunk index',
    chunk_md5 VARCHAR(32) NOT NULL COMMENT 'Chunk md5',
    storage_path VARCHAR(255) NOT NULL COMMENT 'Object storage path'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='File chunk table';

CREATE TABLE document_vectors (
    vector_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Vector record identifier',
    file_md5 VARCHAR(32) NOT NULL COMMENT 'File md5',
    chunk_id INT NOT NULL COMMENT 'Text chunk id',
    text_content TEXT COMMENT 'Chunk text',
    model_version VARCHAR(32) COMMENT 'Embedding model version',
    user_id VARCHAR(64) NOT NULL COMMENT 'Uploader user id',
    org_tag VARCHAR(50) COMMENT 'Org tag',
    is_public BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Public flag'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Document vector metadata table';

SET FOREIGN_KEY_CHECKS = 1;
