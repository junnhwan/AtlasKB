# AtlasKB Phase 1 Plan Summary

## Goal

AtlasKB 的第一阶段目标是: 以 `RAG_PaiCoding` 为主参考，结合 `PaiSmart-main` 的聊天链路和上传说明，尽快忠实复刻 PaiSmart 的后端主链路；暂时不做魔改，不做前端，只做命名、包结构、工程组织和提交节奏上的重构。

## Project Identity

- 项目名: `AtlasKB`
- 仓库名: `atlas-kb`
- Java 根包: `io.hwan.atlaskb`
- 启动类: `io.hwan.atlaskb.AtlasKbApplication`
- 数据库: `atlas_kb`
- Elasticsearch 索引: `atlas_kb_knowledge_base`
- MinIO bucket: `atlas-kb-uploads`
- Kafka topic: `atlas-kb-file-processing`

## Phase 1 Scope

包含:

- Spring Boot 3.x + Java 17 + Maven 后端工程
- JWT 登录鉴权
- 文件分片上传、进度查询、合并
- MinIO 对象存储
- Kafka 异步文件处理任务
- Apache Tika 文档解析与文本切块
- Embedding 接入与 Elasticsearch 建索引
- 权限过滤检索
- WebSocket 流式聊天
- README、联调文档、演示脚本

不包含:

- 前端页面
- 魔改功能
- 多会话高级管理
- 高级召回优化和复杂重排
- OCR、图片理解、复杂版面恢复
- 微服务拆分

## Architecture Direction

采用单体后端 + 按领域分包的结构:

```text
src/main/java/io/hwan/atlaskb/
├── AtlasKbApplication.java
├── common/
├── config/
├── auth/
├── user/
├── organization/
├── document/
├── storage/
├── embedding/
├── search/
└── chat/
```

保留 PaiSmart 的核心主链路:

`上传 -> Kafka -> 解析 -> 切块 -> Embedding -> ES -> 检索 -> Chat`

## Delivery Rhythm

开发必须按小阶段推进，不能一次性写一大堆代码后再回头整理。

推荐提交顺序:

1. `chore(init): bootstrap AtlasKB spring boot project`
2. `chore(infra): add local docker compose and layered configs`
3. `feat(common): add shared response, error handling and request logging`
4. `feat(user-org): add user and organization tag persistence`
5. `feat(auth): add jwt login and security filter`
6. `feat(document-model): add document metadata entities and validation`
7. `feat(upload): add chunk upload status and merge with minio`
8. `feat(async): add kafka file processing pipeline`
9. `feat(parse): add document parsing and text chunking`
10. `feat(index): add embedding client and elasticsearch indexing`
11. `feat(search): add permission-aware hybrid retrieval`
12. `feat(chat): add websocket rag chat with redis conversation state`
13. `feat(document-api): add document list and delete endpoints`
14. `docs(readme): document setup, architecture and demo flow`

## Success Criteria

- `docker compose up` 后依赖可启动
- 后端能正常连接主要中间件
- 登录鉴权可用
- 上传一个文档后能触发异步处理
- 文档可完成解析、切块、向量化和索引
- `/api/v1/search` 可返回权限正确的检索结果
- WebSocket 聊天可基于检索上下文流式回答
- Git 提交历史体现出真实的分阶段开发节奏

## Detailed Docs

- 设计基线: `docs/superpowers/specs/2026-04-09-atlaskb-backend-design.md`
- 实现计划: `docs/superpowers/plans/2026-04-09-atlaskb-backend-implementation.md`
