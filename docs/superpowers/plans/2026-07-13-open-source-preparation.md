# 开源配套 (Open-Source Preparation) 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成风控引擎框架（Antispam）的开源化改造设计，添加 LICENSE、Dockerfile、docker-compose 编排、GitHub Actions 自动化 CI 流及 README.md，并更新动态配置以支持本地/Docker一键拉起。

**Architecture:** 采用混合 Docker Compose 编排模式分离基础设施开发调试与全服务一键拉起部署；在 application.yml 中采用配置环境变量占位符，支持免修改代码的运行环境自适应。

**Tech Stack:** Java 17, Spring Boot 3.2.x, Docker, Docker Compose, GitHub Actions, Apache 2.0 License

## Global Constraints

- 所有新增配置文件位于项目根目录 `/Users/xiaowenzhuo/Desktop/antispam/`
- Java 编译及运行时版本统一为 Java 17
- 不得硬编码外部依赖（Redis, Kafka, MySQL）网络连接 IP 或者是 Host
- 所有配置文件及脚本需通过验证，无语法/逻辑错误

---

### Task 1: 动态配置修改

**Files:**
- Modify: `antispam-starter/src/main/resources/application.yml`

**Interfaces:**
- Consumes: None
- Produces: 支持通过环境变量自定义的 Redis / Kafka / MySQL 地址配置

- [ ] **Step 1: 修改 application.yml 配置文件**

利用占位符替换原本写死的 `localhost`，使服务能自动从环境变量读取 Docker 容器的主机名称。

修改 `antispam-starter/src/main/resources/application.yml` 的内容：

```yaml
server:
  port: 8080

spring:
  application:
    name: antispam-engine
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/antispam?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${SPRING_DATASOURCE_USERNAME:root}
    password: ${SPRING_DATASOURCE_PASSWORD:password}
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:localhost}
      port: ${SPRING_DATA_REDIS_PORT:6379}
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
  kafka:
    bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: 1
      retries: 3

antispam:
  engine:
    timeout-ms: ${ANTISPAM_ENGINE_TIMEOUT_MS:200}
    thread-pool:
      core-size: 20
      max-size: 50
      queue-capacity: 1000

logging:
  level:
    com.antispam: INFO
```

- [ ] **Step 2: 执行已有的集成测试以确保配置解析未损坏**

运行命令：
```bash
cd /Users/xiaowenzhuo/Desktop/antispam && mvn test -pl antispam-starter -am -Dtest=RiskEngineIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: BUILD SUCCESS (3/3 integration tests pass)

- [ ] **Step 3: Commit**

```bash
git add antispam-starter/src/main/resources/application.yml
git commit -m "chore(config): dynamic env vars for redis, kafka, and datasource in application.yml"
```

---

### Task 2: 添加 LICENSE

**Files:**
- Create: `LICENSE`

**Interfaces:**
- Consumes: None
- Produces: 项目根目录下的 Apache 2.0 开源许可协议文本

- [ ] **Step 1: 新建 LICENSE 文件**

在 `/Users/xiaowenzhuo/Desktop/antispam/` 目录下创建 [LICENSE](file:///Users/xiaowenzhuo/Desktop/antispam/LICENSE) 文件并写入 Apache 2.0 官方协议原文。

- [ ] **Step 2: Commit**

```bash
git add LICENSE
git commit -m "docs: add Apache 2.0 License"
```

---

### Task 3: 创建 Dockerfile

**Files:**
- Create: `Dockerfile`

**Interfaces:**
- Consumes: None
- Produces: 基于多阶段构建的项目 Dockerfile，可由 `docker build` 调用构建镜像

- [ ] **Step 1: 编写 Dockerfile**

在 `/Users/xiaowenzhuo/Desktop/antispam/` 下创建 [Dockerfile](file:///Users/xiaowenzhuo/Desktop/antispam/Dockerfile)：

```dockerfile
# Stage 1: Build project
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Copy Maven descriptor files and source files
COPY pom.xml .
COPY antispam-api/pom.xml antispam-api/
COPY antispam-infra/pom.xml antispam-infra/
COPY antispam-core/pom.xml antispam-core/
COPY antispam-factor/pom.xml antispam-factor/
COPY antispam-policy/pom.xml antispam-policy/
COPY antispam-punishment/pom.xml antispam-punishment/
COPY antispam-starter/pom.xml antispam-starter/

# Download dependencies offline to cache them
RUN mvn dependency:go-offline -B -pl antispam-starter -am

# Copy source code and build project
COPY . .
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime JRE
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built jar to runtime image
COPY --from=builder /build/antispam-starter/target/antispam-starter-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: 验证 Dockerfile 编译正确性（不生成物理镜像）**

运行命令：
```bash
cd /Users/xiaowenzhuo/Desktop/antispam && docker build --target builder -t antispam-build-test .
```
*(注意：如果本地 Docker 未运行，可以跳过构建，只验证 Dockerfile 语法符合 Docker 官方规范即可)*

- [ ] **Step 3: Commit**

```bash
git add Dockerfile
git commit -m "deploy: add multi-stage build Dockerfile"
```

---

### Task 4: 创建混合 Docker Compose 编排

**Files:**
- Create: `docker-compose.yml`
- Create: `docker-compose-app.yml`

**Interfaces:**
- Consumes: `Dockerfile`
- Produces: `docker-compose` 编排配置，支持一键拉起依赖或全系统运行

- [ ] **Step 1: 新建基础依赖 docker-compose.yml**

在 `/Users/xiaowenzhuo/Desktop/antispam/` 下创建 [docker-compose.yml](file:///Users/xiaowenzhuo/Desktop/antispam/docker-compose.yml)：

```yaml
version: '3.8'

services:
  redis:
    image: redis:7.2-alpine
    container_name: antispam-redis
    ports:
      - "6379:6379"
    restart: unless-stopped
    command: redis-server --appendonly yes

  mysql:
    image: mysql:8.0
    container_name: antispam-mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: antispam
    volumes:
      - mysql_data:/var/lib/mysql
    restart: unless-stopped

  zookeeper:
    image: bitnami/zookeeper:3.9
    container_name: antispam-zookeeper
    ports:
      - "2181:2181"
    environment:
      - ALLOW_ANONYMOUS_LOGIN=yes
    restart: unless-stopped

  kafka:
    image: bitnami/kafka:3.6
    container_name: antispam-kafka
    ports:
      - "9092:9092"
    environment:
      - KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181
      - ALLOW_PLAINTEXT_LISTENER=yes
      - KAFKA_CFG_LISTENERS=PLAINTEXT://:9092
      - KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092
    depends_on:
      - zookeeper
    restart: unless-stopped

volumes:
  mysql_data:
```

- [ ] **Step 2: 新建全应用部署 docker-compose-app.yml**

在 `/Users/xiaowenzhuo/Desktop/antispam/` 下创建 [docker-compose-app.yml](file:///Users/xiaowenzhuo/Desktop/antispam/docker-compose-app.yml)：

```yaml
version: '3.8'

services:
  redis:
    image: redis:7.2-alpine
    container_name: antispam-redis
    ports:
      - "6379:6379"
    restart: unless-stopped
    command: redis-server --appendonly yes

  mysql:
    image: mysql:8.0
    container_name: antispam-mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: antispam
    volumes:
      - mysql_data:/var/lib/mysql
    restart: unless-stopped

  zookeeper:
    image: bitnami/zookeeper:3.9
    container_name: antispam-zookeeper
    ports:
      - "2181:2181"
    environment:
      - ALLOW_ANONYMOUS_LOGIN=yes
    restart: unless-stopped

  kafka:
    image: bitnami/kafka:3.6
    container_name: antispam-kafka
    ports:
      - "9092:9092"
    environment:
      - KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181
      - ALLOW_PLAINTEXT_LISTENER=yes
      - KAFKA_CFG_LISTENERS=PLAINTEXT://:9092
      - KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092
    depends_on:
      - zookeeper
    restart: unless-stopped

  antispam-engine:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: antispam-engine
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATA_REDIS_HOST=redis
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/antispam?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    depends_on:
      - redis
      - kafka
      - mysql
    restart: unless-stopped

volumes:
  mysql_data:
```

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml docker-compose-app.yml
git commit -m "deploy: add compose configs for infrastructure and full stack run"
```

---

### Task 5: 配置 GitHub Actions 自动化 CI 工作流

**Files:**
- Create: `.github/workflows/maven.yml`

**Interfaces:**
- Consumes: None
- Produces: GitHub Actions 流程定义文件，在推送或 PR 时运行单元测试

- [ ] **Step 1: 创建 YAML 文件**

在 `/Users/xiaowenzhuo/Desktop/antispam/` 下创建 [.github/workflows/maven.yml](file:///Users/xiaowenzhuo/Desktop/antispam/.github/workflows/maven.yml)：

```yaml
name: Java CI with Maven

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: maven

    - name: Build and Test with Maven
      run: mvn clean test -Dsurefire.failIfNoSpecifiedTests=false
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/maven.yml
git commit -m "ci: add GitHub Actions workflow for Maven build and test validation"
```

---

### Task 6: 编写 README.md 引导说明书

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: None
- Produces: 项目根目录下的 README.md 说明书

- [ ] **Step 1: 创建 README.md 文件**

在 `/Users/xiaowenzhuo/Desktop/antispam/` 下创建 [README.md](file:///Users/xiaowenzhuo/Desktop/antispam/README.md) 并写入完整的中文/英文项目快速上手、架构拓扑说明、以及测试 cURL 用例。

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: create comprehensive README.md with architecture and E2E guide"
```
