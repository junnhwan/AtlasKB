# AtlasKB Local Dev Infrastructure

## Goal

This document describes the local dependency stack used by AtlasKB Phase 1.

## Services

| Service | Port | Default Credential | Notes |
| --- | --- | --- | --- |
| MySQL 8.0.37 | `3306` | `root / 123456` | Initializes `atlas_kb` from `src/main/resources/db/ddl.sql` |
| Redis 7.0.11 | `6379` | none | Used for upload state and conversation history |
| Elasticsearch 8.10.0 | `9200` | none | Security disabled for local development |
| MinIO | `9000` / `9001` | `minioadmin / minioadmin` | API and console |
| ZooKeeper | `2181` | none | Kafka dependency |
| Kafka | `9092` | none | Local broker for file processing |

## Startup

Before running any Docker command, make sure the Docker daemon is running.

```bash
docker compose up -d
docker compose ps
```

## Local Profiles

- `dev`: connect to services on `localhost`
- `docker`: connect to services by container name

## Naming

- Database: `atlas_kb`
- Elasticsearch index: `atlas_kb_knowledge_base`
- MinIO bucket: `atlas-kb-uploads`
- Kafka topic: `atlas-kb-file-processing`
- Kafka DLT: `atlas-kb-file-processing-dlt`
