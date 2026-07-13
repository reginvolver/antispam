# 风控引擎框架 (Antispam) 开源化改造设计

## 1. 目标与背景

为了将风控引擎（Antispam）以企业级开源项目的标准推向 GitHub，我们需要补充完整的工程治理工具和文档配套。这包括：
1. **容器化支持**：多阶段构建的 `Dockerfile`，用于快速构建独立微服务镜像。
2. **多模式容器编排**：支持混合配置的 `docker-compose`。开发时只拉起外部依赖（MySQL, Redis, Kafka），一键部署时可联同应用本身一起拉起。
3. **开源许可协议**：添加 Apache 2.0 开源证书。
4. **自动化 CI 流程**：配置 GitHub Actions 工作流，以便在提交 PR 或 Push 时自动执行 Maven 编译与单元测试。
5. **动态配置改造**：支持通过环境变量定制组件（Redis, Kafka, MySQL）连接地址，实现无缝的本地开发和容器部署切换。
6. **项目使用文档**：撰写内容翔实、排版规范的 `README.md` 引导外部开发者。

---

## 2. 详细设计

### 2.1 动态配置支持 (`application.yml`)
修改 [application.yml](file:///Users/xiaowenzhuo/Desktop/antispam/antispam-starter/src/main/resources/application.yml)，将硬编码的网络地址修改为基于环境变量占位符的值：

- Redis 主机地址：由 `localhost` 改为 `${SPRING_DATA_REDIS_HOST:localhost}`
- Kafka 连接地址：由 `localhost:9092` 改为 `${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`
- MySQL 连接地址：由 `localhost:3306` 改为 `${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/antispam?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}`

### 2.2 Dockerfile 容器化构建
在根目录下新建 [Dockerfile](file:///Users/xiaowenzhuo/Desktop/antispam/Dockerfile)，采用多阶段构建（Multi-stage Build）：
- **第一阶段（Maven Build）**：
  使用 `maven:3.9.6-eclipse-temurin-17-alpine` 镜像作为基础。
  将本地项目根目录挂载并执行 `mvn clean package -DskipTests` 生成 JAR。
- **第二阶段（Runtime JRE）**：
  使用轻量级的高性价比镜像 `eclipse-temurin:17-jre-alpine`。
  将打包生成的 `antispam-starter/target/antispam-starter-1.0.0-SNAPSHOT.jar` 复制到运行时镜像。
  暴露 `8080` 端口，并将入口命令设为：`java -jar /app.jar`。

### 2.3 混合 Docker Compose 编排
- **`docker-compose.yml`**（基础设施栈）：
  - **MySQL 8.0**：容器名 `mysql`，暴露端口 `3306`，配置初始数据库 `antispam`，持久化数据到 `mysql_data` 卷。
  - **Redis 7.0**：容器名 `redis`，暴露端口 `6379`，配置 Lettuce 客户端所需的最大连接池数。
  - **Kafka (Apache Bitnami)**：包含单节点 Kafka 服务，配置内置 Zookeeper，暴露端口 `9092`。
- **`docker-compose-app.yml`**（全容器栈）：
  - 基于上述基础依赖服务进行继承 (`extends: file: docker-compose.yml`)。
  - 补充 `antispam-engine` 容器：使用根目录下 `Dockerfile` 现场构建，并将上述组件容器的 Hostname 作为环境变量注入：
    - `SPRING_DATA_REDIS_HOST=redis`
    - `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092`
    - `SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/antispam?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai`

### 2.4 GitHub Actions 工作流
在项目根目录新建 [.github/workflows/maven.yml](file:///Users/xiaowenzhuo/Desktop/antispam/.github/workflows/maven.yml) 文件，配置基本的 CI 流：
- **触发分支**：对 `main` 分支的 Push 和 PR。
- **运行环境**：`ubuntu-latest`。
- **步骤**：
  1. 拉取代码 (`actions/checkout@v4`)。
  2. 设置 Java 17 运行环境 (`actions/setup-java@v4`)，启用 Maven 依赖缓存。
  3. 执行编译与测试：`mvn clean test -Dsurefire.failIfNoSpecifiedTests=false`。

### 2.5 README.md 与开源协议说明
- **`LICENSE`**：添加标准的 Apache 2.0 授权许可协议。
- **`README.md`**：使用规范的 Markdown 样式，包含：
  - 核心架构和各层 SPI 介绍。
  - DAG 图执行设计和 Kahn 算法图解。
  - 快速入门：启动本地 compose 或全容器服务。
  - 用例请求演示：用 `curl` 分别调用 PASS、REVIEW、BLOCK 接口，以及对应的响应结果字段解释。
