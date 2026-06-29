# RAG Multimodal Chat

一个基于 Spring Boot 的对话 + 多模态 + RAG 知识库项目。项目可以在没有模型 API Key 的情况下本地演示上传、切分、检索和引用展示；配置 OpenAI 兼容接口后，会启用真实聊天、视觉和 embedding 模型。

## 功能

- 对话工作台：支持上下文历史、RAG 开关、引用片段展示。
- 多模态输入：前端可上传图片，聊天接口按 OpenAI 兼容 `image_url` 格式发送。
- 知识库：支持 txt、md、csv、json、pdf 上传，自动解析、分块、向量化。
- 检索测试：可单独测试知识库召回结果。
- 本地 fallback：未配置 API Key 时使用本地哈希向量，方便快速跑通。

## 运行

项目需要 Java 17。macOS 上如果默认 `java -version` 还是 1.8，可以先执行：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
```

```bash
mvn spring-boot:run
```

打开：

```text
http://localhost:8080
```

如果你的 Maven 全局镜像临时不可用，可以用项目内临时 settings：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -gs .mvn/central-settings.xml -s .mvn/central-settings.xml spring-boot:run
```

## 配置模型

设置环境变量后启动：

```bash
export APP_AI_API_KEY="你的 API Key"
export APP_AI_BASE_URL="https://api.openai.com/v1"
export APP_AI_CHAT_MODEL="gpt-4.1-mini"
export APP_AI_VISION_MODEL="gpt-4.1-mini"
export APP_AI_EMBEDDING_MODEL="text-embedding-3-small"
mvn spring-boot:run
```

也可以接入任何 OpenAI 兼容网关，只要支持 `/chat/completions` 和 `/embeddings`。

## API

- `GET /api/status`：查看服务和模型配置状态。
- `POST /api/chat`：发送聊天请求。
- `POST /api/knowledge/documents`：上传知识库文档，表单字段名为 `file`。
- `GET /api/knowledge/documents`：查看文档列表。
- `DELETE /api/knowledge/documents/{documentId}`：删除文档。
- `GET /api/knowledge/search?q=关键词&topK=4`：测试检索。

## 目录

```text
src/main/java/org/example
  config      配置
  controller  REST API
  model       请求、响应和知识库模型
  service     聊天、向量和知识库服务
src/main/resources/static
  index.html  前端工作台
  styles.css  样式
  app.js      交互逻辑
```

## 后续可增强

- 将内存索引替换为 pgvector、Milvus、Qdrant 或 Elasticsearch。
- 增加 docx、xlsx、网页抓取等知识源。
- 增加用户、会话持久化和权限隔离。
- 增加流式输出和 SSE。
