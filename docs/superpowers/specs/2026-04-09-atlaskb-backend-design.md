# AtlasKB Backend Rebuild Design

## 文档信息

- 日期: 2026-04-09
- 项目名: `AtlasKB`
- 阶段: `Phase 1`
- 项目定位: 忠实复刻 PaiSmart 后端主链路的独立重构版
- Java 根包: `io.hwan.atlaskb`
- 当前结论: 设计已确认，可据此细化实现计划

---

## 1. 背景与参考基线

AtlasKB 不是在参考仓库上直接二次开发，而是基于公开项目重新手搓的独立后端项目。目标不是“换个名字继续改”，而是先把参考项目的关键链路彻底读懂，再以自己的工程组织方式从零复现出来。

当前使用的参考来源:

1. `D:\dev\learn_proj\wanger\RAG_PaiCoding`
2. `D:\dev\learn_proj\wanger\PaiSmart-main`
3. `D:\dev\learn_proj\wanger\paismart-nbx1234`
4. `https://javabetter.cn/zhishixingqiu/paismart.html`

参考仓库虽然来自不同 GitHub 账号，但 README、目录组织、技术栈和文档内容都明显属于同一条 PaiSmart 血缘线，因此适合作为“读懂主链路后再重构复现”的学习对象。

Phase 1 的参考优先级:

1. `RAG_PaiCoding`
   - 作为主参考仓库
   - 原因是后端结构更直接，更适合抽取最小可行复刻路径
2. `PaiSmart-main`
   - 作为补充参考
   - 重点借鉴上传说明、聊天调用链、整体工程说明
3. `javabetter.cn` 对应介绍页
   - 用于确认项目定位、技术栈、架构叙事和主链路描述

重点参考材料:

- `RAG_PaiCoding/README.md`
- `RAG_PaiCoding/docs/databases/ddl.sql`
- `RAG_PaiCoding/src/main/resources/es-mappings/knowledge_base.json`
- `PaiSmart-main/PaiSmart_AI_Chat_Call_Chain_Analysis.md`
- `PaiSmart-main/docs/upload-flow.md`

---

## 2. 项目目标

### 2.1 核心目标

Phase 1 的唯一核心目标是: 尽快忠实复刻 PaiSmart 的后端主链路，跑通一个真正可验证的 Java RAG 闭环系统。

这里的“忠实复刻”指的是:

- 保留原项目的核心技术栈和系统边界
- 保留上传、异步处理、向量化、检索、聊天这条主流程
- 保留真实工程中常见的中间件协作方式

这里的“重构”只限于:

- 统一命名为 `AtlasKB`
- Java 根包改为 `io.hwan.atlaskb`
- 代码结构改为按领域分包
- 配置、README、脚本和提交历史按自己的工程节奏重写

### 2.2 明确不是本阶段目标的内容

Phase 1 不追求:

- 加入新颖魔改功能
- 做一个更“现代化”的替代系统
- 把项目先行拆成微服务
- 把中间件大量裁剪成轻量 demo

当前阶段先把 PaiSmart 的主链路复刻出来，后续再基于这条稳定主线做魔改，会更容易讲清楚每次改动的意义。

---

## 3. Phase 1 范围与成功标准

### 3.1 包含范围

Phase 1 包含以下后端能力:

- Spring Boot 3.x + Java 17 + Maven 工程初始化
- MySQL、Redis、MinIO、Elasticsearch、Kafka 本地编排
- JWT 登录鉴权
- 用户与组织标签的最小权限模型
- 文件分片上传、进度查询、合并
- Kafka 异步文件处理任务
- Apache Tika 文档解析
- 文本切块
- Embedding 接入
- Elasticsearch 索引初始化与写入
- 带权限过滤的检索接口
- WebSocket 流式聊天
- Redis 会话历史
- README、联调说明、演示脚本

### 3.2 不包含范围

Phase 1 明确不做:

- Vue 前端页面
- 多会话高级管理
- 多模型编排
- 复杂召回优化和重排
- 长期记忆和双层记忆体系
- OCR、图片理解、复杂版面恢复
- 灰度发布、监控平台和生产运维自动化
- 与 PaiSmart 无关的个性化魔改

### 3.3 成功标准

AtlasKB Phase 1 完成时，需要满足以下标准:

1. `docker compose up` 后主要依赖可启动
2. 后端可成功启动并连接 MySQL、Redis、MinIO、Elasticsearch、Kafka
3. 测试账号可登录并获得 JWT
4. 支持文档分片上传、进度查询和合并
5. 合并后可通过 Kafka 触发异步处理
6. 文档可完成解析、切块、向量化与索引写入
7. `/api/v1/search` 可返回权限正确的检索结果
8. WebSocket 聊天可基于检索上下文返回流式响应
9. README 和联调文档足以支撑下一会话继续开发
10. Git 提交历史体现出真实、分阶段的开发过程

---

## 4. 命名与项目身份

为了避免仓库在观感上变成“原项目直接改名”，Phase 1 所有命名必须统一替换到 AtlasKB 体系。

统一命名规则:

- 项目名: `AtlasKB`
- 仓库名: `atlas-kb`
- Java 根包: `io.hwan.atlaskb`
- 启动类: `AtlasKbApplication`
- MySQL 数据库名: `atlas_kb`
- Elasticsearch 索引名: `atlas_kb_knowledge_base`
- MinIO bucket: `atlas-kb-uploads`
- Kafka topic: `atlas-kb-file-processing`
- Kafka DLT: `atlas-kb-file-processing-dlt`

README 和对外描述建议使用:

> AtlasKB 是一个企业级 Java RAG 知识库系统，受公开 PaiSmart 参考项目启发完成独立重构，重点复刻了文档摄取、向量检索、权限过滤与流式问答主链路。

---

## 5. 架构方向与模块边界

### 5.1 总体架构

AtlasKB Phase 1 采用“单体后端 + 外部基础设施”的结构，不主动拆微服务。原因很简单: 当前目标是尽快忠实复刻主链路，而不是提前增加部署和边界复杂度。

高层架构如下:

1. API 层
   - 登录、上传、检索、文档管理、聊天入口
2. 鉴权层
   - Spring Security + JWT
3. 存储层
   - MinIO 分片和合并对象管理
4. 文档处理层
   - Kafka 异步消费、Tika 解析、文本切块
5. 向量与索引层
   - Embedding 调用、ES 索引写入
6. 检索层
   - 向量召回、关键词相关性、权限过滤
7. 会话与聊天层
   - Redis 历史消息、Prompt 构造、DeepSeek 流式问答

### 5.2 代码结构

建议的根包结构如下:

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

模块职责定义:

- `common`
  - 统一响应、异常、日志、常量、基础工具
- `config`
  - Security、WebMvc、Kafka、Redis、MinIO、ES、WebSocket 等配置
- `auth`
  - 登录接口、JWT 服务、鉴权过滤器、认证 DTO
- `user`
  - 用户实体、Repository、用户查询服务
- `organization`
  - 组织标签实体、权限标签推导服务
- `document`
  - 文件元数据、分片记录、解析任务模型、解析/切块、文档管理
- `storage`
  - 上传接口、MinIO 交互、分片上传与合并逻辑
- `embedding`
  - Embedding 客户端及配置
- `search`
  - ES 索引初始化、索引写入、混合检索
- `chat`
  - WebSocket、聊天编排、Prompt 构造、LLM 客户端、会话历史

约束:

- `controller/service/repository/entity/dto` 这类层级只在领域包内组织
- 不在根目录重新平铺成全局 `controller`、`service`、`repository`

---

## 6. 核心业务流

### 6.1 登录与鉴权

流程:

1. `POST /api/v1/auth/login`
2. 从 MySQL 查询用户
3. 校验密码并生成 JWT
4. 受保护接口由 JWT 过滤器解析用户身份
5. 将 `userId`、`username`、`role` 注入上下文

### 6.2 上传与合并

流程:

1. 客户端计算文件 MD5
2. 调用 `/api/v1/upload/chunk` 上传分片
3. 服务端记录:
   - `file_upload`
   - `chunk_info`
   - Redis bitmap 状态
   - MinIO `chunks/<fileMd5>/<chunkIndex>`
4. 调用 `/api/v1/upload/merge`
5. 服务端合并 MinIO 对象，生成合并文件
6. 更新上传状态并投递 Kafka 任务

### 6.3 文件异步处理

流程:

1. Kafka consumer 消费 `FileProcessingTask`
2. 从 MinIO 获取合并后文件
3. 使用 Tika 提取纯文本
4. 对文本清洗并切块
5. 调用 Embedding 服务生成向量
6. 将 chunk 与向量写入 Elasticsearch
7. 同步维护 `document_vectors` 元数据
8. 更新 `file_upload.status`

### 6.4 检索

流程:

1. 用户发起 `/api/v1/search`
2. 解析用户权限边界:
   - 自己上传的文档
   - 公开文档
   - 所属组织标签文档
3. 生成 query embedding
4. 在 ES 中进行向量召回
5. 结合关键词相关性做混合检索
6. 返回排序后的命中结果

### 6.5 聊天

流程:

1. 客户端通过 WebSocket 发消息
2. 服务端从 token 解析用户身份
3. 从 Redis 读取当前会话历史
4. 调用搜索服务获取 topK 上下文
5. 构建 Prompt 和引用片段
6. 调用 DeepSeek-compatible 流式接口
7. 将响应块推送给客户端
8. 会话完成后写回 Redis

---

## 7. 关键技术取舍

### 7.1 必须保留的设计

为了忠实复刻 PaiSmart 主链路，Phase 1 必须保留:

- `Spring Boot 3.x + Java 17 + Maven`
- `MySQL + Redis + MinIO + Elasticsearch + Kafka`
- `Spring Security + JWT`
- `Apache Tika`
- `WebSocket` 作为正式聊天入口
- `上传 -> Kafka -> 解析 -> 切块 -> Embedding -> ES -> 检索 -> Chat` 的主流程
- `Redis Bitmap` 这类上传状态思路

### 7.2 本阶段不主动改动的地方

Phase 1 不做以下替换:

- 不把 Kafka 改成同步任务
- 不把 WebSocket 改成 REST 聊天
- 不把 Elasticsearch 替换成其他向量库
- 不抢先上复杂记忆、多路召回、重排器
- 不先行拆微服务

原因是: 这些改动都会让 AtlasKB 偏离“忠实复刻 PaiSmart 主链路”的学习目标。

### 7.3 允许做的重构

本阶段允许且应该做的重构只有:

- 命名体系统一替换
- 按领域重组代码结构
- 补齐更清晰的 README 和联调文档
- 将提交粒度拆成更真实的小阶段

---

## 8. 开发节奏与提交规则

### 8.1 开发原则

开发时必须遵守以下规则:

1. 不一次性抄完整个大模块
2. 每次只推进一个可验证的小阶段
3. 当前阶段未验证通过，不进入下一个阶段
4. 优先抄主链路与关键策略，不追求逐文件照搬
5. 后端优先，先把 `/search` 跑通，再做聊天

### 8.2 推荐开发顺序

1. 初始化工程
2. 基础设施与配置分层
3. 公共基础层
4. 用户/组织标签与登录鉴权
5. 文档元数据模型
6. 分片上传、进度查询、合并
7. Kafka 异步处理链路
8. 文档解析与切块
9. Embedding 与索引
10. 混合检索
11. WebSocket 聊天
12. 文档管理、README、联调文档、回归

### 8.3 推荐提交历史

推荐至少形成以下提交:

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

## 9. 风险与规避

### 9.1 基础设施拖慢节奏

规避方式:

- 使用 `docker compose`
- 先做环境连通性，再做业务逻辑
- 每个阶段只引入一种新复杂度

### 9.2 复刻过程中变成照抄

规避方式:

- 先按业务流理解，再映射到自己的包结构
- 统一替换命名、配置和文档
- 保持提交粒度清晰，体现真实推导过程

### 9.3 聊天功能调试复杂

规避方式:

- 先独立做 `/api/v1/search`
- 用固定样例文档和标准问题回归
- 聊天阶段只消费已经验证过的检索服务

---

## 10. 结论

AtlasKB Phase 1 的设计已经明确:

- 目标是尽快忠实复刻 PaiSmart 主链路
- 不做前端，不做魔改
- 根包统一为 `io.hwan.atlaskb`
- 采用单体后端 + 按领域分包
- 保留 Kafka、MinIO、Elasticsearch、WebSocket 等企业级边界
- 通过小阶段、小提交的方式逐步完成实现

后续实现计划必须严格围绕这份设计展开，不能自行扩大范围。
