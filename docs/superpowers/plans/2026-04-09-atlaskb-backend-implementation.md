# AtlasKB Backend Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从零创建一个名为 AtlasKB 的 Java RAG 后端项目，以 `RAG_PaiCoding` 为主参考，忠实复刻 PaiSmart 的登录、上传、异步处理、向量检索和 WebSocket 聊天主链路。

**Architecture:** 项目采用 Spring Boot 单体后端 + 外部基础设施的实现方式。上传走 MinIO，文件处理走 Kafka，索引落 Elasticsearch，会话放 Redis，鉴权采用 Spring Security + JWT，聊天采用 WebSocket 流式输出。代码结构按领域分包，不使用原项目的全局平铺目录。

**Tech Stack:** Java 17, Spring Boot 3.4.x, Maven, Spring Security, Spring Data JPA, Redis, Kafka, MinIO, Elasticsearch Java Client, Apache Tika, WebClient/WebFlux, MySQL 8

---

## 0. 执行约束

- 所有 Java 代码统一使用根包 `io.hwan.atlaskb`
- 第一阶段只做后端，不创建前端工程
- 第一阶段只做忠实复刻，不提前加入魔改能力
- 每次只推进一个小阶段，当前阶段验证通过后再继续
- 每个阶段最多 1 到 2 个 commit，不允许把多个子系统揉成一个大提交
- 优先使用 `RAG_PaiCoding` 做实现参考，`PaiSmart-main` 主要补充上传和聊天细节

---

## 1. 参考材料阅读顺序

正式编码前，建议按以下顺序对照参考项目:

1. `D:\dev\learn_proj\wanger\RAG_PaiCoding\README.md`
2. `D:\dev\learn_proj\wanger\RAG_PaiCoding\docs\databases\ddl.sql`
3. `D:\dev\learn_proj\wanger\RAG_PaiCoding\src\main\resources\es-mappings\knowledge_base.json`
4. `D:\dev\learn_proj\wanger\PaiSmart-main\docs\upload-flow.md`
5. `D:\dev\learn_proj\wanger\PaiSmart-main\PaiSmart_AI_Chat_Call_Chain_Analysis.md`

目标不是逐文件照搬，而是先读清楚以下 5 件事:

- 上传链路怎么分阶段处理
- Kafka 在哪里承接异步任务
- Tika、切块、Embedding、ES 的衔接点在哪里
- 权限过滤放在检索链路的哪里
- 聊天入口、Prompt 构造和历史消息更新顺序是什么

---

## 2. 目标目录结构

```text
atlas-kb/
├── .gitignore
├── README.md
├── docker-compose.yml
├── pom.xml
├── docs/
│   ├── architecture/
│   └── api/
├── scripts/
├── src/main/java/io/hwan/atlaskb/
│   ├── AtlasKbApplication.java
│   ├── common/
│   ├── config/
│   ├── auth/
│   ├── user/
│   ├── organization/
│   ├── document/
│   ├── storage/
│   ├── embedding/
│   ├── search/
│   └── chat/
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-docker.yml
│   ├── es-mappings/atlas_kb_knowledge_base.json
│   └── db/ddl.sql
└── src/test/java/io/hwan/atlaskb/
```

默认配置值:

- 端口: `8081`
- 数据库: `atlas_kb`
- ES 索引: `atlas_kb_knowledge_base`
- MinIO bucket: `atlas-kb-uploads`
- Kafka topic: `atlas-kb-file-processing`
- Kafka DLT: `atlas-kb-file-processing-dlt`
- 分片大小: `5MB`
- 检索默认 `topK=5`
- 历史消息保留最近 `20` 条

---

## 3. 里程碑总览

推荐按以下顺序推进:

1. Task 0: 初始化工程
2. Task 1: 基础设施与配置分层
3. Task 2: 公共基础层
4. Task 3: 用户与组织标签持久化
5. Task 4: JWT 登录鉴权
6. Task 5: 文档元数据模型与文件校验
7. Task 6: 分片上传、进度查询、合并
8. Task 7: Kafka 异步处理骨架
9. Task 8: 文档解析与切块
10. Task 9: Embedding 与索引
11. Task 10: 检索与权限过滤
12. Task 11: WebSocket 聊天与 Redis 历史
13. Task 12: 文档管理、README、联调与回归

---

## 4. Task 0: 初始化工程

**Reference**

- `RAG_PaiCoding/README.md`

**Files**

- Create: `.gitignore`
- Create: `pom.xml`
- Create: `README.md`
- Create: `src/main/java/io/hwan/atlaskb/AtlasKbApplication.java`
- Create: `src/main/java/io/hwan/atlaskb/common/controller/PingController.java`
- Create: `src/main/resources/application.yml`
- Create: `src/test/java/io/hwan/atlaskb/AtlasKbApplicationTests.java`

- [ ] 创建最小可运行的 Spring Boot 工程，包名使用 `io.hwan.atlaskb`
- [ ] 只引入最少依赖:
  - `spring-boot-starter-web`
  - `spring-boot-starter-test`
- [ ] 增加一个 `/ping` 或健康检查接口
- [ ] 写第一版 README，只说明项目目标、Phase 1 范围和技术栈
- [ ] 运行测试和编译，确认骨架可用
- [ ] 提交 `chore(init): bootstrap AtlasKB spring boot project`

**Verify**

- Run: `mvn test`
- Run: `mvn -q -DskipTests compile`
- Expected: 测试通过，项目可编译

---

## 5. Task 1: 基础设施与配置分层

**Reference**

- `RAG_PaiCoding/docs/databases/ddl.sql`
- `RAG_PaiCoding/src/main/resources/es-mappings/knowledge_base.json`
- `PaiSmart-main/README.md`

**Files**

- Create: `docker-compose.yml`
- Create: `src/main/resources/application-dev.yml`
- Create: `src/main/resources/application-docker.yml`
- Create: `src/main/resources/db/ddl.sql`
- Create: `src/main/resources/es-mappings/atlas_kb_knowledge_base.json`
- Create: `docs/architecture/local-dev.md`

- [ ] 在 `docker-compose.yml` 中定义 MySQL、Redis、MinIO、Kafka、Zookeeper、Elasticsearch
- [ ] 将参考 DDL 改成 AtlasKB 命名
- [ ] 将 ES mapping 改成 `atlas_kb_knowledge_base`
- [ ] 在 `application.yml` 中只保留通用键
- [ ] 在 `application-dev.yml` 中配置本地地址
- [ ] 在 `application-docker.yml` 中配置容器网络地址
- [ ] 编写本地依赖启动说明
- [ ] 提交 `chore(infra): add local docker compose and layered configs`

**Verify**

- Run: `docker compose up -d`
- Run: `docker compose ps`
- Expected: 依赖容器正常启动

---

## 6. Task 2: 公共基础层

**Files**

- Create: `src/main/java/io/hwan/atlaskb/common/api/ApiResponse.java`
- Create: `src/main/java/io/hwan/atlaskb/common/exception/BusinessException.java`
- Create: `src/main/java/io/hwan/atlaskb/common/exception/GlobalExceptionHandler.java`
- Create: `src/main/java/io/hwan/atlaskb/common/logging/RequestLoggingInterceptor.java`
- Create: `src/main/java/io/hwan/atlaskb/config/WebMvcConfig.java`
- Create: `src/test/java/io/hwan/atlaskb/common/GlobalExceptionHandlerTest.java`

- [ ] 统一响应格式，至少包含 `code`、`message`、`data`
- [ ] 添加业务异常和全局异常处理
- [ ] 增加请求日志拦截器，记录 method、path、status、cost
- [ ] 确保 controller 中不重复拼错误响应
- [ ] 提交 `feat(common): add shared response, error handling and request logging`

**Verify**

- Run: `mvn test`
- Expected: 测试通过，异常返回结构一致

---

## 7. Task 3: 用户与组织标签持久化

**Reference**

- `RAG_PaiCoding/docs/databases/ddl.sql`

**Files**

- Create: `src/main/java/io/hwan/atlaskb/user/entity/User.java`
- Create: `src/main/java/io/hwan/atlaskb/organization/entity/OrganizationTag.java`
- Create: `src/main/java/io/hwan/atlaskb/user/repository/UserRepository.java`
- Create: `src/main/java/io/hwan/atlaskb/organization/repository/OrganizationTagRepository.java`
- Create: `src/main/java/io/hwan/atlaskb/user/service/UserQueryService.java`
- Create: `src/test/java/io/hwan/atlaskb/user/UserRepositoryTest.java`

- [ ] 建立 `users` 和 `organization_tags` 实体与 Repository
- [ ] 保留 PaiSmart 的最小组织标签模型
- [ ] 准备初始化数据策略，至少包含一个管理员账号和默认组织标签
- [ ] 提交 `feat(user-org): add user and organization tag persistence`

**Verify**

- Run: `mvn test`
- Expected: JPA 映射可通过测试或启动验证

---

## 8. Task 4: JWT 登录鉴权

**Files**

- Create: `src/main/java/io/hwan/atlaskb/auth/dto/LoginRequest.java`
- Create: `src/main/java/io/hwan/atlaskb/auth/dto/LoginResponse.java`
- Create: `src/main/java/io/hwan/atlaskb/auth/service/JwtService.java`
- Create: `src/main/java/io/hwan/atlaskb/auth/filter/JwtAuthenticationFilter.java`
- Create: `src/main/java/io/hwan/atlaskb/config/SecurityConfig.java`
- Create: `src/main/java/io/hwan/atlaskb/auth/controller/AuthController.java`
- Create: `src/main/java/io/hwan/atlaskb/auth/service/AuthService.java`
- Create: `src/test/java/io/hwan/atlaskb/auth/AuthControllerTest.java`

- [ ] 使用 BCrypt 保存密码
- [ ] 实现登录接口，返回 token 和基础用户信息
- [ ] 配置 Spring Security:
  - 登录接口匿名访问
  - 其他 `/api/v1/**` 默认鉴权
- [ ] 过滤器中解析 token 并注入 `userId`、`username`、`role`
- [ ] 提交 `feat(auth): add jwt login and security filter`

**Verify**

- Run: `mvn test`
- Run: `curl -X POST http://localhost:8081/api/v1/auth/login`
- Expected: 登录成功返回 token，受保护接口未授权时返回 401

---

## 9. Task 5: 文档元数据模型与文件校验

**Reference**

- `RAG_PaiCoding/docs/databases/ddl.sql`
- `PaiSmart-main/docs/upload-flow.md`

**Files**

- Create: `src/main/java/io/hwan/atlaskb/document/entity/FileUpload.java`
- Create: `src/main/java/io/hwan/atlaskb/document/entity/ChunkInfo.java`
- Create: `src/main/java/io/hwan/atlaskb/document/entity/DocumentVector.java`
- Create: `src/main/java/io/hwan/atlaskb/document/repository/FileUploadRepository.java`
- Create: `src/main/java/io/hwan/atlaskb/document/repository/ChunkInfoRepository.java`
- Create: `src/main/java/io/hwan/atlaskb/document/repository/DocumentVectorRepository.java`
- Create: `src/main/java/io/hwan/atlaskb/document/service/FileTypeValidationService.java`
- Create: `src/test/java/io/hwan/atlaskb/document/FileTypeValidationServiceTest.java`

- [ ] 建立文档相关 3 个核心实体和 Repository
- [ ] 明确文件状态枚举:
  - `0` 已创建未完成
  - `1` 已合并待处理
  - `2` 已索引完成
  - `3` 处理失败
- [ ] 初版白名单支持 `pdf`、`docx`、`txt`、`md`
- [ ] 提交 `feat(document-model): add document metadata entities and validation`

**Verify**

- Run: `mvn test`
- Expected: 文件类型校验通过，实体映射清晰

---

## 10. Task 6: 分片上传、进度查询、合并

**Reference**

- `PaiSmart-main/docs/upload-flow.md`

**Files**

- Create: `src/main/java/io/hwan/atlaskb/storage/config/MinioConfig.java`
- Create: `src/main/java/io/hwan/atlaskb/storage/controller/UploadController.java`
- Create: `src/main/java/io/hwan/atlaskb/storage/service/UploadService.java`
- Create: `src/main/java/io/hwan/atlaskb/storage/dto/MergeRequest.java`
- Create: `src/test/java/io/hwan/atlaskb/storage/UploadControllerTest.java`

- [ ] 配置 MinIO client 和 bucket 初始化
- [ ] 实现 `/api/v1/upload/chunk`
  - 首片校验文件类型
  - 记录文件元数据
  - 保存分片到 `chunks/<fileMd5>/<chunkIndex>`
  - 更新 Redis bitmap
  - 写入 `chunk_info`
- [ ] 实现 `/api/v1/upload/status`
- [ ] 实现 `/api/v1/upload/merge`
  - 校验分片完整性
  - 通过 MinIO compose 合并对象
  - 更新 `file_upload.status=1`
- [ ] 组织标签为空时默认回落到用户主组织
- [ ] 提交 `feat(upload): add chunk upload status and merge with minio`

**Verify**

- Run: `mvn test`
- 手工调用上传接口上传样本文档
- Expected: MinIO 中可看到分片与合并对象，状态接口返回进度

---

## 11. Task 7: Kafka 异步处理骨架

**Files**

- Create: `src/main/java/io/hwan/atlaskb/config/KafkaConfig.java`
- Create: `src/main/java/io/hwan/atlaskb/document/model/FileProcessingTask.java`
- Create: `src/main/java/io/hwan/atlaskb/document/consumer/FileProcessingConsumer.java`
- Modify: `src/main/java/io/hwan/atlaskb/storage/controller/UploadController.java`
- Create: `src/test/java/io/hwan/atlaskb/document/FileProcessingTaskSerializationTest.java`

- [ ] 定义 Kafka producer、consumer、error handler、DLT
- [ ] `/merge` 成功后发送 `FileProcessingTask`
- [ ] 第一个版本的 consumer 先只打印日志并更新状态
- [ ] 提交 `feat(async): add kafka file processing pipeline`

**Verify**

- 合并文件后观察 consumer 是否收到消息
- 故意制造异常，确认 DLT 配置生效

---

## 12. Task 8: 文档解析与切块

**Files**

- Create: `src/main/java/io/hwan/atlaskb/document/service/ParseService.java`
- Create: `src/main/java/io/hwan/atlaskb/document/service/ChunkingService.java`
- Create: `src/main/java/io/hwan/atlaskb/document/model/TextChunk.java`
- Modify: `src/main/java/io/hwan/atlaskb/document/consumer/FileProcessingConsumer.java`
- Create: `src/test/java/io/hwan/atlaskb/document/ChunkingServiceTest.java`

- [ ] 使用 Apache Tika 提取文档纯文本
- [ ] 实现基础清洗:
  - 去 BOM
  - 合并多余空白
  - 去纯空 chunk
- [ ] 实现固定切块策略:
  - chunk size `512`
  - overlap `50`
- [ ] 让 consumer 能得到 `TextChunk` 列表
- [ ] 提交 `feat(parse): add document parsing and text chunking`

**Verify**

- 使用样本文档观察 chunk 数量和文本内容
- 验证空文档、超短文档的行为

---

## 13. Task 9: Embedding 与索引

**Reference**

- `RAG_PaiCoding/src/main/resources/es-mappings/knowledge_base.json`

**Files**

- Create: `src/main/java/io/hwan/atlaskb/embedding/client/EmbeddingClient.java`
- Create: `src/main/java/io/hwan/atlaskb/search/config/ElasticsearchConfig.java`
- Create: `src/main/java/io/hwan/atlaskb/search/config/EsIndexInitializer.java`
- Create: `src/main/java/io/hwan/atlaskb/search/entity/EsDocument.java`
- Create: `src/main/java/io/hwan/atlaskb/search/service/IndexingService.java`
- Modify: `src/main/java/io/hwan/atlaskb/document/consumer/FileProcessingConsumer.java`
- Create: `src/test/java/io/hwan/atlaskb/embedding/EmbeddingClientTest.java`

- [ ] 接入 Embedding-compatible HTTP 客户端
- [ ] 输入 chunk 文本列表，输出 `List<List<Float>>`
- [ ] 启动时检查索引是否存在，不存在则自动创建
- [ ] 将 chunk 元数据写入 ES
- [ ] 将 chunk 元数据同步写入 `document_vectors`
- [ ] 成功后将 `file_upload.status=2`
- [ ] 失败时更新为 `status=3`
- [ ] 提交 `feat(index): add embedding client and elasticsearch indexing`

**Verify**

- 查询 ES 确认索引和文档写入成功
- 故障场景下确认状态更新正确

---

## 14. Task 10: 检索与权限过滤

**Files**

- Create: `src/main/java/io/hwan/atlaskb/search/dto/SearchRequest.java`
- Create: `src/main/java/io/hwan/atlaskb/search/dto/SearchResult.java`
- Create: `src/main/java/io/hwan/atlaskb/search/controller/SearchController.java`
- Create: `src/main/java/io/hwan/atlaskb/search/service/HybridSearchService.java`
- Create: `src/main/java/io/hwan/atlaskb/organization/service/OrgTagPermissionService.java`
- Create: `src/test/java/io/hwan/atlaskb/search/HybridSearchServiceTest.java`

- [ ] 从用户身份推导有效权限范围:
  - 自己上传的文档
  - 公开文档
  - 所属组织标签文档
- [ ] 生成 query embedding
- [ ] 执行向量召回并结合关键词相关性做混合检索
- [ ] 提供 `POST /api/v1/search` 作为独立验证入口
- [ ] 提交 `feat(search): add permission-aware hybrid retrieval`

**Verify**

- 使用不同用户和组织标签测试可见性
- Expected: 公开文档、私有文档和组织文档的命中范围正确

---

## 15. Task 11: WebSocket 聊天与 Redis 历史

**Reference**

- `PaiSmart-main/PaiSmart_AI_Chat_Call_Chain_Analysis.md`

**Files**

- Create: `src/main/java/io/hwan/atlaskb/chat/config/WebSocketConfig.java`
- Create: `src/main/java/io/hwan/atlaskb/chat/handler/ChatWebSocketHandler.java`
- Create: `src/main/java/io/hwan/atlaskb/chat/service/ChatService.java`
- Create: `src/main/java/io/hwan/atlaskb/chat/client/DeepSeekClient.java`
- Create: `src/main/java/io/hwan/atlaskb/chat/config/AiProperties.java`
- Create: `src/test/java/io/hwan/atlaskb/chat/PromptBuilderTest.java`

- [ ] WebSocket 路径固定为 `/api/v1/chat/{token}`
- [ ] 连接建立时解析 token 得到用户身份
- [ ] Redis 中维护 `user:{userId}:current_conversation`
- [ ] 每轮流程固定为:
  - 读历史
  - 检索 topK=5
  - 拼系统提示词
  - 调用流式接口
  - 按 chunk 推送
  - 完成后写回 Redis
- [ ] 系统提示词至少包含:
  - 仅用简体中文作答
  - 先给结论再给论据
  - 引用文件名
  - 无检索结果时明确说明
- [ ] 提交 `feat(chat): add websocket rag chat with redis conversation state`

**Verify**

- 使用 WebSocket 客户端手工发送消息
- Expected: 能看到流式输出，第二轮对话可读取历史

---

## 16. Task 12: 文档管理、README、联调与回归

**Files**

- Create: `src/main/java/io/hwan/atlaskb/document/controller/DocumentController.java`
- Create: `src/main/java/io/hwan/atlaskb/document/service/DocumentService.java`
- Modify: `README.md`
- Create: `docs/api/manual-test.md`
- Create: `scripts/demo-flow.md`

- [ ] 增加文档列表查询接口
- [ ] 增加文档删除接口，并同步清理:
  - MySQL 记录
  - ES 文档
  - MinIO 合并对象
- [ ] README 补齐:
  - 项目目标
  - 架构图
  - 技术栈
  - 启动方式
  - Phase 1 功能列表
  - 演示流程
- [ ] 编写手工联调文档，覆盖登录、上传、合并、Kafka 处理、检索、聊天
- [ ] 从空环境重新跑一遍主链路
- [ ] 提交 `feat(document-api): add document list and delete endpoints`
- [ ] 提交 `docs(readme): document setup, architecture and demo flow`

**Verify**

- 从空环境执行一轮完整回归
- Expected: README 足够让第三方复现主链路

---

## 17. 提交节奏要求

必须遵守以下节奏:

1. 一个任务最多 1 到 2 个 commit
2. 每次 commit 只表达一个清晰意图
3. 当前任务未验证通过，不进入下一个任务
4. 不允许出现“一次提交一个完整系统”的痕迹

推荐提交列表:

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

---

## 18. 验收标准

Phase 1 视为完成的标准:

- 工程命名统一为 AtlasKB
- 根包统一为 `io.hwan.atlaskb`
- 依赖服务可通过 compose 启动
- 上传、处理、检索、聊天主链路可跑通
- README 和 docs 足够支撑继续开发
- Git 历史体现出小阶段推进而不是一把梭

---

## 19. 下一会话执行提示

后续正式开发时，建议直接使用下面的起手提示:

```text
请先阅读以下文档并严格按计划执行，不要自行扩大范围：

1. docs/superpowers/specs/2026-04-09-atlaskb-backend-design.md
2. docs/superpowers/plans/2026-04-09-atlaskb-backend-implementation.md

目标是开始创建 AtlasKB 项目，并从 Task 0 开始逐步实现。
要求每次只完成一个小阶段，先验证再提交，不要一次性写一大堆代码。
```
