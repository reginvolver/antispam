# 风控引擎框架 (Antispam) 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个基于事件驱动 DAG 的通用实时风控引擎框架，支持因子、套餐、处罚、响应式图执行四大核心能力。

**Architecture:** 采用 Maven 多模块分层架构，核心为 GraphExecutor（拓扑并发调度器），以 CompletableFuture 驱动因子 DAG 异步执行；业务逻辑通过 Factor / PolicyPackage / Punishment 三个 SPI 扩展；Spring Boot AutoConfiguration 完成自动装配。

**Tech Stack:** Java 17+, Spring Boot 3.x, Spring MVC, Aviator 5.x, Redis (Lettuce), Kafka (spring-kafka), MySQL (MyBatis-Plus), Micrometer, JUnit 5, Mockito

## Global Constraints

- Java 版本：17（LTS），不使用 JDK 19+ 预览特性
- Spring Boot：3.2.x
- Aviator：5.4.x（aviator groupId: com.googlecode.aviator）
- Redis 客户端：spring-boot-starter-data-redis（Lettuce）
- Kafka 客户端：spring-kafka
- ORM：MyBatis-Plus 3.5.x
- 所有 SPI 接口位于 `antispam-api` 模块，无 Spring 依赖
- 所有模块包名前缀：`com.antispam`
- Redis Key 格式：`antispam:{功能}:{维度}`，例如 `antispam:login_freq:{userId}`
- Kafka Topic：`antispam.punishment.events` / `antispam.audit.logs`
- 单元测试不启动 Spring 上下文（纯 JUnit5 + Mockito）
- 集成测试使用 `@SpringBootTest`（在 antispam-starter 模块）

---

## Task 1: Maven 多模块项目骨架

**Files:**
- Create: `pom.xml`（根 POM）
- Create: `antispam-api/pom.xml`
- Create: `antispam-core/pom.xml`
- Create: `antispam-factor/pom.xml`
- Create: `antispam-policy/pom.xml`
- Create: `antispam-punishment/pom.xml`
- Create: `antispam-infra/pom.xml`
- Create: `antispam-starter/pom.xml`

**Interfaces:**
- Produces: 可编译的多模块项目骨架，所有后续任务在此基础上添加代码

- [ ] **Step 1: 创建根 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.antispam</groupId>
    <artifactId>antispam</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>Antispam Risk Engine</name>

    <modules>
        <module>antispam-api</module>
        <module>antispam-infra</module>
        <module>antispam-core</module>
        <module>antispam-factor</module>
        <module>antispam-policy</module>
        <module>antispam-punishment</module>
        <module>antispam-starter</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-boot.version>3.2.5</spring-boot.version>
        <aviator.version>5.4.1</aviator.version>
        <mybatis-plus.version>3.5.7</mybatis-plus.version>
        <lombok.version>1.18.32</lombok.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- 内部模块 -->
            <dependency>
                <groupId>com.antispam</groupId>
                <artifactId>antispam-api</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.antispam</groupId>
                <artifactId>antispam-infra</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.antispam</groupId>
                <artifactId>antispam-core</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.antispam</groupId>
                <artifactId>antispam-factor</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.antispam</groupId>
                <artifactId>antispam-policy</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.antispam</groupId>
                <artifactId>antispam-punishment</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- 第三方 -->
            <dependency>
                <groupId>com.googlecode.aviator</groupId>
                <artifactId>aviator</artifactId>
                <version>${aviator.version}</version>
            </dependency>
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>
            <dependency>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
                <scope>provided</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.13.0</version>
                    <configuration>
                        <source>17</source>
                        <target>17</target>
                        <annotationProcessorPaths>
                            <path>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                                <version>${lombok.version}</version>
                            </path>
                        </annotationProcessorPaths>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 2: 创建各子模块目录和 pom.xml**

依次创建以下 7 个子模块目录和对应的 `pom.xml`：

**antispam-api/pom.xml**（无 Spring 依赖，纯接口）：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.antispam</groupId>
        <artifactId>antispam</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>antispam-api</artifactId>
    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

**antispam-infra/pom.xml**：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.antispam</groupId>
        <artifactId>antispam</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>antispam-infra</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.antispam</groupId>
            <artifactId>antispam-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

**antispam-core/pom.xml**：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.antispam</groupId>
        <artifactId>antispam</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>antispam-core</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.antispam</groupId>
            <artifactId>antispam-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

**antispam-factor/pom.xml**：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.antispam</groupId>
        <artifactId>antispam</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>antispam-factor</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.antispam</groupId>
            <artifactId>antispam-api</artifactId>
        </dependency>
        <dependency>
            <groupId>com.antispam</groupId>
            <artifactId>antispam-infra</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

**antispam-policy/pom.xml**：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.antispam</groupId>
        <artifactId>antispam</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>antispam-policy</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.antispam</groupId>
            <artifactId>antispam-api</artifactId>
        </dependency>
        <dependency>
            <groupId>com.googlecode.aviator</groupId>
            <artifactId>aviator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

**antispam-punishment/pom.xml**：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.antispam</groupId>
        <artifactId>antispam</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>antispam-punishment</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.antispam</groupId>
            <artifactId>antispam-api</artifactId>
        </dependency>
        <dependency>
            <groupId>com.antispam</groupId>
            <artifactId>antispam-infra</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

**antispam-starter/pom.xml**：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.antispam</groupId>
        <artifactId>antispam</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>antispam-starter</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.antispam</groupId>
            <artifactId>antispam-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.antispam</groupId>
            <artifactId>antispam-factor</artifactId>
        </dependency>
        <dependency>
            <groupId>com.antispam</groupId>
            <artifactId>antispam-policy</artifactId>
        </dependency>
        <dependency>
            <groupId>com.antispam</groupId>
            <artifactId>antispam-punishment</artifactId>
        </dependency>
        <dependency>
            <groupId>com.antispam</groupId>
            <artifactId>antispam-infra</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: 创建所有模块的 src 目录结构**

```bash
# 在项目根目录执行
for module in antispam-api antispam-infra antispam-core antispam-factor antispam-policy antispam-punishment antispam-starter; do
  mkdir -p $module/src/main/java/com/antispam
  mkdir -p $module/src/main/resources
  mkdir -p $module/src/test/java/com/antispam
done
```

- [ ] **Step 4: 验证 Maven 项目可编译**

```bash
cd /Users/xiaowenzhuo/Desktop/antispam
mvn compile -q
```

Expected: BUILD SUCCESS（各模块仅有空 src 目录，无源码但结构正确）

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: scaffold maven multi-module project structure"
```

---

## Task 2: antispam-api — SPI 接口与领域模型

**Files:**
- Create: `antispam-api/src/main/java/com/antispam/api/model/RiskContext.java`
- Create: `antispam-api/src/main/java/com/antispam/api/model/RiskResponse.java`
- Create: `antispam-api/src/main/java/com/antispam/api/model/RiskLevel.java`
- Create: `antispam-api/src/main/java/com/antispam/api/model/FactorResult.java`
- Create: `antispam-api/src/main/java/com/antispam/api/model/FactorMap.java`
- Create: `antispam-api/src/main/java/com/antispam/api/model/PolicyResult.java`
- Create: `antispam-api/src/main/java/com/antispam/api/model/PunishmentContext.java`
- Create: `antispam-api/src/main/java/com/antispam/api/model/PunishmentResult.java`
- Create: `antispam-api/src/main/java/com/antispam/api/model/PunishmentType.java`
- Create: `antispam-api/src/main/java/com/antispam/api/spi/Factor.java`
- Create: `antispam-api/src/main/java/com/antispam/api/spi/PolicyPackage.java`
- Create: `antispam-api/src/main/java/com/antispam/api/spi/Punishment.java`
- Create: `antispam-api/src/main/java/com/antispam/api/spi/RiskEngine.java`
- Test: `antispam-api/src/test/java/com/antispam/api/model/FactorMapTest.java`

**Interfaces:**
- Produces:
  - `RiskContext` — `String businessType, userId, deviceId, ip, eventType; Map<String,Object> attributes; long timestamp`
  - `RiskLevel` — enum `PASS, REVIEW, BLOCK`
  - `FactorMap` — `void put(String factorId, FactorResult result)`, `Optional<Object> getValue(String factorId)`, `Map<String,Object> toValueMap()`
  - `Factor` — `String factorId()`, `List<String> dependencies()`, `FactorResult compute(RiskContext ctx, FactorMap upstream)`
  - `PolicyPackage` — `String policyId()`, `String businessType()`, `List<String> requiredFactors()`, `PolicyResult evaluate(FactorMap facts)`
  - `Punishment` — `String punishmentId()`, `PunishmentType type()`, `PunishmentResult execute(PunishmentContext ctx)`
  - `RiskEngine` — `RiskResponse evaluate(RiskContext context)`

- [ ] **Step 1: 创建领域枚举和基础模型**

`antispam-api/src/main/java/com/antispam/api/model/RiskLevel.java`:
```java
package com.antispam.api.model;

public enum RiskLevel {
    PASS,
    REVIEW,
    BLOCK;

    /**
     * 取两个级别中更严重的一个
     */
    public RiskLevel max(RiskLevel other) {
        return this.ordinal() >= other.ordinal() ? this : other;
    }
}
```

`antispam-api/src/main/java/com/antispam/api/model/PunishmentType.java`:
```java
package com.antispam.api.model;

public enum PunishmentType {
    /** 引擎内部直接执行 */
    INTERNAL,
    /** 通过 Webhook 通知外部系统 */
    WEBHOOK
}
```

`antispam-api/src/main/java/com/antispam/api/model/RiskContext.java`:
```java
package com.antispam.api.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
@Builder
@ToString
public class RiskContext {
    /** 业务种类，用于路由到对应套餐，例如 "ECOMMERCE" */
    private final String businessType;
    /** 用户唯一标识 */
    private final String userId;
    /** 设备唯一标识 */
    private final String deviceId;
    /** 客户端 IP */
    private final String ip;
    /** 事件类型，例如 "LOGIN"、"PAY"、"REGISTER" */
    private final String eventType;
    /** 扩展属性，可携带业务方自定义字段 */
    @Builder.Default
    private final Map<String, Object> attributes = new HashMap<>();
    /** 请求时间戳（毫秒） */
    @Builder.Default
    private final long timestamp = System.currentTimeMillis();

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }
}
```

`antispam-api/src/main/java/com/antispam/api/model/FactorResult.java`:
```java
package com.antispam.api.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class FactorResult {
    /** 计算成功的结果值（数字/布尔/字符串） */
    private final Object value;
    /** 是否成功计算 */
    private final boolean success;
    /** 计算失败时使用的 fallback 值，不为 null */
    private final Object fallbackValue;
    /** 失败原因（可选） */
    private final String errorMessage;

    /** 获取有效值：成功返回 value，失败返回 fallbackValue */
    public Object effectiveValue() {
        return success ? value : fallbackValue;
    }

    public static FactorResult success(Object value) {
        return FactorResult.builder().success(true).value(value).fallbackValue(value).build();
    }

    public static FactorResult failure(Object fallbackValue, String reason) {
        return FactorResult.builder().success(false).value(null)
                .fallbackValue(fallbackValue).errorMessage(reason).build();
    }
}
```

`antispam-api/src/main/java/com/antispam/api/model/FactorMap.java`:
```java
package com.antispam.api.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 因子计算结果聚合容器，线程安全。
 * GraphExecutor 在执行过程中持续写入；规则引擎在所有因子完成后读取。
 */
public class FactorMap {
    private final ConcurrentHashMap<String, FactorResult> results = new ConcurrentHashMap<>();

    public void put(String factorId, FactorResult result) {
        results.put(factorId, result);
    }

    public Optional<FactorResult> getResult(String factorId) {
        return Optional.ofNullable(results.get(factorId));
    }

    public Optional<Object> getValue(String factorId) {
        return getResult(factorId).map(FactorResult::effectiveValue);
    }

    /** 将所有因子的有效值展平为 Map<factorId, effectiveValue>，供 Aviator 直接使用 */
    public Map<String, Object> toValueMap() {
        Map<String, Object> map = new HashMap<>();
        results.forEach((k, v) -> map.put(k, v.effectiveValue()));
        return Collections.unmodifiableMap(map);
    }

    public boolean contains(String factorId) {
        return results.containsKey(factorId);
    }
}
```

`antispam-api/src/main/java/com/antispam/api/model/PolicyResult.java`:
```java
package com.antispam.api.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
@ToString
public class PolicyResult {
    /** 是否命中套餐 */
    private final boolean matched;
    /** 建议的风险级别 */
    @Builder.Default
    private final RiskLevel suggestedLevel = RiskLevel.PASS;
    /** 需要执行的处罚 ID 列表 */
    @Builder.Default
    private final List<String> punishmentIds = Collections.emptyList();
    /** 命中的规则描述（调试用） */
    private final String matchedRule;

    public static PolicyResult noMatch() {
        return PolicyResult.builder().matched(false).build();
    }
}
```

`antispam-api/src/main/java/com/antispam/api/model/PunishmentResult.java`:
```java
package com.antispam.api.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class PunishmentResult {
    private final String punishmentId;
    private final boolean executed;
    private final String message;

    public static PunishmentResult success(String punishmentId) {
        return PunishmentResult.builder().punishmentId(punishmentId).executed(true)
                .message("executed successfully").build();
    }

    public static PunishmentResult failure(String punishmentId, String reason) {
        return PunishmentResult.builder().punishmentId(punishmentId).executed(false)
                .message(reason).build();
    }
}
```

`antispam-api/src/main/java/com/antispam/api/model/PunishmentContext.java`:
```java
package com.antispam.api.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

@Getter
@Builder
public class PunishmentContext {
    private final RiskContext riskContext;
    private final RiskLevel level;
    /** 处罚配置参数，来自套餐配置（如 banDuration、webhookUrl 等） */
    @Builder.Default
    private final Map<String, Object> config = Collections.emptyMap();
}
```

`antispam-api/src/main/java/com/antispam/api/model/RiskResponse.java`:
```java
package com.antispam.api.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@ToString
public class RiskResponse {
    /** 最终风险级别 */
    private final RiskLevel level;
    /** 命中的套餐 ID 列表 */
    @Builder.Default
    private final List<String> matchedPolicies = Collections.emptyList();
    /** 已触发/执行的处罚结果 */
    @Builder.Default
    private final List<PunishmentResult> punishments = Collections.emptyList();
    /** 所有因子的计算结果（调试用） */
    @Builder.Default
    private final Map<String, Object> factorValues = Collections.emptyMap();
    /** 总耗时（毫秒） */
    private final long elapsedMs;
    /** 是否触发了全局超时降级 */
    private final boolean timedOut;
}
```

- [ ] **Step 2: 创建 SPI 接口**

`antispam-api/src/main/java/com/antispam/api/spi/Factor.java`:
```java
package com.antispam.api.spi;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.FactorResult;
import com.antispam.api.model.RiskContext;

import java.util.Collections;
import java.util.List;

/**
 * 因子 SPI。每个因子是 DAG 中的一个节点。
 * 实现类需注册为 Spring Bean（@Component）以自动被 FactorRegistry 发现。
 */
public interface Factor {
    /** 因子唯一 ID，需全局唯一，例如 "loginFreq1Min" */
    String factorId();

    /**
     * 该因子依赖的上游因子 ID 列表。
     * 返回空列表表示无依赖，可在图执行开始时立即执行。
     * 注意：不得形成循环依赖，否则 GraphExecutor 在构建时会抛出异常。
     */
    default List<String> dependencies() {
        return Collections.emptyList();
    }

    /**
     * 计算因子值。
     * @param ctx      当前请求上下文
     * @param upstream 已完成的上游因子结果（依赖列表中的所有因子均已完成）
     * @return 计算结果，不得返回 null；失败时使用 FactorResult.failure() 返回 fallback
     */
    FactorResult compute(RiskContext ctx, FactorMap upstream);
}
```

`antispam-api/src/main/java/com/antispam/api/spi/PolicyPackage.java`:
```java
package com.antispam.api.spi;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.PolicyResult;

import java.util.List;

/**
 * 套餐 SPI。描述一组规则条件及其命中后的处罚行为。
 * 实现类需注册为 Spring Bean（@Component）以自动被 PolicyRegistry 发现。
 */
public interface PolicyPackage {
    /** 套餐唯一 ID */
    String policyId();

    /** 绑定的业务种类，对应 RiskContext.businessType */
    String businessType();

    /**
     * 此套餐需要的因子 ID 列表。
     * GraphExecutor 将确保这些因子在规则求值前全部完成（或超时降级）。
     */
    List<String> requiredFactors();

    /**
     * 基于已计算的因子值评估是否命中，并给出风险级别和处罚动作。
     * @param facts 包含所有已计算因子结果的 FactorMap
     * @return 评估结果；未命中时返回 PolicyResult.noMatch()
     */
    PolicyResult evaluate(FactorMap facts);
}
```

`antispam-api/src/main/java/com/antispam/api/spi/Punishment.java`:
```java
package com.antispam.api.spi;

import com.antispam.api.model.PunishmentContext;
import com.antispam.api.model.PunishmentResult;
import com.antispam.api.model.PunishmentType;

/**
 * 处罚 SPI。实现具体的处罚执行逻辑。
 * 实现类需注册为 Spring Bean（@Component）以自动被 PunishmentRegistry 发现。
 */
public interface Punishment {
    /** 处罚唯一 ID，例如 "captcha"、"banAccount"、"rateLimit" */
    String punishmentId();

    /** 处罚类型：INTERNAL（引擎内部执行）或 WEBHOOK（通知外部系统）*/
    PunishmentType type();

    /**
     * 执行处罚。
     * INTERNAL 类型：直接执行并返回结果（同步）
     * WEBHOOK 类型：将事件推入 Kafka 后立即返回（异步）
     * 实现不得抛出未检查异常，所有异常需捕获并返回 PunishmentResult.failure()
     */
    PunishmentResult execute(PunishmentContext ctx);
}
```

`antispam-api/src/main/java/com/antispam/api/spi/RiskEngine.java`:
```java
package com.antispam.api.spi;

import com.antispam.api.model.RiskContext;
import com.antispam.api.model.RiskResponse;

/**
 * 风控引擎主入口 SPI。
 * 调用方通过此接口发起风险评估请求。
 */
public interface RiskEngine {
    /**
     * 对给定上下文执行风险评估。
     * 该方法是同步调用，但内部 DAG 图执行是并发的。
     * @param context 请求上下文，不得为 null
     * @return 风险评估结果，不得返回 null
     */
    RiskResponse evaluate(RiskContext context);
}
```

- [ ] **Step 3: 为 FactorMap 写失败测试**

`antispam-api/src/test/java/com/antispam/api/model/FactorMapTest.java`:
```java
package com.antispam.api.model;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FactorMapTest {

    @Test
    void put_and_getValue_returnsEffectiveValue() {
        FactorMap map = new FactorMap();
        map.put("loginFreq", FactorResult.success(5L));

        Optional<Object> value = map.getValue("loginFreq");
        assertTrue(value.isPresent());
        assertEquals(5L, value.get());
    }

    @Test
    void getValue_forFailedFactor_returnsFallback() {
        FactorMap map = new FactorMap();
        map.put("loginFreq", FactorResult.failure(0L, "redis timeout"));

        Optional<Object> value = map.getValue("loginFreq");
        assertTrue(value.isPresent());
        assertEquals(0L, value.get()); // fallback value
    }

    @Test
    void getValue_forMissingFactor_returnsEmpty() {
        FactorMap map = new FactorMap();
        assertTrue(map.getValue("nonExistent").isEmpty());
    }

    @Test
    void toValueMap_containsAllEffectiveValues() {
        FactorMap map = new FactorMap();
        map.put("a", FactorResult.success(1L));
        map.put("b", FactorResult.failure(0L, "err"));

        Map<String, Object> valueMap = map.toValueMap();
        assertEquals(1L, valueMap.get("a"));
        assertEquals(0L, valueMap.get("b")); // fallback
    }

    @Test
    void contains_returnsTrue_afterPut() {
        FactorMap map = new FactorMap();
        assertFalse(map.contains("x"));
        map.put("x", FactorResult.success("val"));
        assertTrue(map.contains("x"));
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd /Users/xiaowenzhuo/Desktop/antispam
mvn test -pl antispam-api -q
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add antispam-api/
git commit -m "feat(api): add SPI interfaces and domain models"
```

---

## Task 3: antispam-core — GraphExecutor DAG 调度引擎

**Files:**
- Create: `antispam-core/src/main/java/com/antispam/core/graph/GraphNode.java`
- Create: `antispam-core/src/main/java/com/antispam/core/graph/GraphExecutor.java`
- Create: `antispam-core/src/main/java/com/antispam/core/registry/FactorRegistry.java`
- Create: `antispam-core/src/main/java/com/antispam/core/engine/DefaultRiskEngine.java`
- Test: `antispam-core/src/test/java/com/antispam/core/graph/GraphExecutorTest.java`

**Interfaces:**
- Consumes:
  - `Factor` — `factorId()`, `dependencies()`, `compute(RiskContext, FactorMap)`
  - `FactorMap` — `put()`, `getValue()`
  - `FactorResult` — `success()`, `failure()`, `effectiveValue()`
- Produces:
  - `GraphExecutor.execute(List<Factor> factors, RiskContext ctx, long timeoutMs) -> FactorMap`
  - `FactorRegistry.getFactorById(String id) -> Optional<Factor>`
  - `FactorRegistry.getAll() -> List<Factor>`
  - `DefaultRiskEngine implements RiskEngine`

- [ ] **Step 1: 写 GraphExecutor 失败测试**

`antispam-core/src/test/java/com/antispam/core/graph/GraphExecutorTest.java`:
```java
package com.antispam.core.graph;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.FactorResult;
import com.antispam.api.model.RiskContext;
import com.antispam.api.spi.Factor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GraphExecutorTest {

    private ExecutorService executor;
    private GraphExecutor graphExecutor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(4);
        graphExecutor = new GraphExecutor(executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    private RiskContext ctx() {
        return RiskContext.builder().businessType("TEST").userId("u1").build();
    }

    @Test
    void execute_independentFactors_allCompleteConcurrently() {
        AtomicInteger counter = new AtomicInteger(0);

        Factor a = new Factor() {
            public String factorId() { return "a"; }
            public List<String> dependencies() { return List.of(); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                counter.incrementAndGet();
                return FactorResult.success(1L);
            }
        };

        Factor b = new Factor() {
            public String factorId() { return "b"; }
            public List<String> dependencies() { return List.of(); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                counter.incrementAndGet();
                return FactorResult.success(2L);
            }
        };

        FactorMap result = graphExecutor.execute(List.of(a, b), ctx(), 1000);

        assertEquals(2, counter.get());
        assertEquals(1L, result.getValue("a").orElseThrow());
        assertEquals(2L, result.getValue("b").orElseThrow());
    }

    @Test
    void execute_dependentFactor_usesUpstreamResult() {
        // b 依赖 a，b 的计算结果为 a 的值 + 10
        Factor a = new Factor() {
            public String factorId() { return "a"; }
            public List<String> dependencies() { return List.of(); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                return FactorResult.success(5L);
            }
        };

        Factor b = new Factor() {
            public String factorId() { return "b"; }
            public List<String> dependencies() { return List.of("a"); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                long aVal = (Long) upstream.getValue("a").orElse(0L);
                return FactorResult.success(aVal + 10);
            }
        };

        FactorMap result = graphExecutor.execute(List.of(a, b), ctx(), 1000);

        assertEquals(5L, result.getValue("a").orElseThrow());
        assertEquals(15L, result.getValue("b").orElseThrow()); // 5 + 10
    }

    @Test
    void execute_factorThrowsException_usesFallback() {
        Factor bad = new Factor() {
            public String factorId() { return "bad"; }
            public List<String> dependencies() { return List.of(); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                throw new RuntimeException("Redis down");
            }
        };

        // 不抛出异常，返回 fallback（0）
        FactorMap result = graphExecutor.execute(List.of(bad), ctx(), 1000);
        // 因为 compute 本身抛出异常，GraphExecutor 应捕获并写入 failure result
        assertTrue(result.contains("bad"));
        // effectiveValue 应为 fallback（0L）
        assertEquals(0L, result.getValue("bad").orElse(0L));
    }

    @Test
    void execute_timeout_returnsPartialResults() throws InterruptedException {
        Factor slow = new Factor() {
            public String factorId() { return "slow"; }
            public List<String> dependencies() { return List.of(); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return FactorResult.success(99L);
            }
        };
        Factor fast = new Factor() {
            public String factorId() { return "fast"; }
            public List<String> dependencies() { return List.of(); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) {
                return FactorResult.success(1L);
            }
        };

        // 超时 100ms，slow 因子来不及完成
        FactorMap result = graphExecutor.execute(List.of(slow, fast), ctx(), 100);

        // fast 应该完成
        assertTrue(result.contains("fast"));
        assertEquals(1L, result.getValue("fast").orElseThrow());
        // slow 可能未完成或使用 fallback
        // 关键：不抛出异常，方法正常返回
    }

    @Test
    void execute_circularDependency_throwsIllegalStateException() {
        Factor a = new Factor() {
            public String factorId() { return "a"; }
            public List<String> dependencies() { return List.of("b"); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) { return FactorResult.success(1L); }
        };
        Factor b = new Factor() {
            public String factorId() { return "b"; }
            public List<String> dependencies() { return List.of("a"); }
            public FactorResult compute(RiskContext ctx, FactorMap upstream) { return FactorResult.success(2L); }
        };

        assertThrows(IllegalStateException.class,
                () -> graphExecutor.execute(List.of(a, b), ctx(), 1000));
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd /Users/xiaowenzhuo/Desktop/antispam
mvn test -pl antispam-core -q 2>&1 | head -20
```

Expected: 编译错误（GraphExecutor 类不存在）

- [ ] **Step 3: 实现 GraphNode**

`antispam-core/src/main/java/com/antispam/core/graph/GraphNode.java`:
```java
package com.antispam.core.graph;

import com.antispam.api.spi.Factor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DAG 中的一个节点，对应一个 Factor。
 */
@Getter
class GraphNode {
    private final Factor factor;
    /** 该节点的入度（还需等待多少个上游节点完成） */
    private final AtomicInteger inDegree;
    /** 以该节点为上游的下游节点列表 */
    private final List<GraphNode> downstreams = new ArrayList<>();
    /** 该节点的执行 Future，由 GraphExecutor 赋值 */
    private volatile CompletableFuture<Void> future;

    GraphNode(Factor factor, int inDegree) {
        this.factor = factor;
        this.inDegree = new AtomicInteger(inDegree);
    }

    void setFuture(CompletableFuture<Void> future) {
        this.future = future;
    }

    /**
     * 将 downstream 添加为该节点的下游节点
     */
    void addDownstream(GraphNode downstream) {
        downstreams.add(downstream);
    }

    /**
     * 上游节点完成时调用，将入度减 1。
     * @return 减 1 后的入度值（0 表示所有上游均已完成，可以开始执行）
     */
    int decrementInDegree() {
        return inDegree.decrementAndGet();
    }
}
```

- [ ] **Step 4: 实现 GraphExecutor**

`antispam-core/src/main/java/com/antispam/core/graph/GraphExecutor.java`:
```java
package com.antispam.core.graph;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.FactorResult;
import com.antispam.api.model.RiskContext;
import com.antispam.api.spi.Factor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 响应式异步图执行器（Reactive Async Graph）。
 * 使用拓扑排序驱动 DAG 中的因子并行执行。
 * 核心算法：Kahn's Algorithm（基于入度的 BFS 拓扑排序）。
 */
@Slf4j
@RequiredArgsConstructor
public class GraphExecutor {

    private final ExecutorService threadPool;

    /**
     * 执行因子 DAG。
     *
     * @param factors   需要执行的因子列表
     * @param ctx       请求上下文
     * @param timeoutMs 全局超时毫秒数；超时后用当前已有结果降级返回
     * @return 包含所有已完成因子结果的 FactorMap
     * @throws IllegalStateException 如果因子依赖形成循环
     */
    public FactorMap execute(List<Factor> factors, RiskContext ctx, long timeoutMs) {
        if (factors == null || factors.isEmpty()) {
            return new FactorMap();
        }

        // Step 1: 建立 factorId -> GraphNode 映射
        Map<String, GraphNode> nodeMap = new HashMap<>();
        for (Factor factor : factors) {
            nodeMap.put(factor.factorId(), new GraphNode(factor, 0));
        }

        // Step 2: 根据 dependencies() 建立有向边，计算入度
        for (Factor factor : factors) {
            GraphNode downstream = nodeMap.get(factor.factorId());
            for (String depId : factor.dependencies()) {
                GraphNode upstream = nodeMap.get(depId);
                if (upstream == null) {
                    throw new IllegalStateException(
                            "Factor [" + factor.factorId() + "] depends on unknown factor [" + depId + "]");
                }
                upstream.addDownstream(downstream);
                downstream.getInDegree().incrementAndGet();
            }
        }

        // Step 3: 检测环形依赖（拓扑排序验证）
        validateNoCycle(nodeMap, factors.size());

        // Step 4: 执行
        FactorMap factorMap = new FactorMap();
        List<CompletableFuture<Void>> allFutures = new ArrayList<>();

        for (GraphNode node : nodeMap.values()) {
            if (node.getInDegree().get() == 0) {
                CompletableFuture<Void> future = submitNode(node, ctx, factorMap, nodeMap, allFutures);
                node.setFuture(future);
                allFutures.add(future);
            }
        }

        // Step 5: 等待所有节点完成或超时
        try {
            CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0]))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("[GraphExecutor] Global timeout {}ms reached, returning partial results for context: {}",
                    timeoutMs, ctx.getUserId());
            // 超时：取消未完成节点（best effort），返回当前已有结果
            allFutures.forEach(f -> f.cancel(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[GraphExecutor] Interrupted while waiting for graph execution");
        } catch (ExecutionException e) {
            // 单节点异常已在 submitNode 内部处理，不会传播到这里
            log.error("[GraphExecutor] Unexpected execution error", e.getCause());
        }

        return factorMap;
    }

    private CompletableFuture<Void> submitNode(
            GraphNode node,
            RiskContext ctx,
            FactorMap factorMap,
            Map<String, GraphNode> nodeMap,
            List<CompletableFuture<Void>> allFutures) {

        return CompletableFuture.runAsync(() -> {
            Factor factor = node.getFactor();
            FactorResult result;
            try {
                result = factor.compute(ctx, factorMap);
                if (result == null) {
                    result = FactorResult.failure(0L, "factor returned null");
                }
            } catch (Exception e) {
                log.warn("[GraphExecutor] Factor [{}] threw exception: {}", factor.factorId(), e.getMessage());
                result = FactorResult.failure(0L, e.getMessage());
            }
            factorMap.put(factor.factorId(), result);

            // 通知下游节点
            for (GraphNode downstream : node.getDownstreams()) {
                int remaining = downstream.decrementInDegree();
                if (remaining == 0) {
                    CompletableFuture<Void> downstreamFuture =
                            submitNode(downstream, ctx, factorMap, nodeMap, allFutures);
                    downstream.setFuture(downstreamFuture);
                    synchronized (allFutures) {
                        allFutures.add(downstreamFuture);
                    }
                }
            }
        }, threadPool);
    }

    /**
     * 使用 Kahn's Algorithm 检测环形依赖。
     * 如果拓扑排序后仍有节点未被处理，说明存在环。
     */
    private void validateNoCycle(Map<String, GraphNode> nodeMap, int totalFactors) {
        // 计算每个节点的入度副本
        Map<String, Integer> inDegrees = new HashMap<>();
        nodeMap.forEach((id, node) -> inDegrees.put(id, node.getInDegree().get()));

        Queue<String> queue = new LinkedList<>();
        inDegrees.forEach((id, degree) -> {
            if (degree == 0) queue.add(id);
        });

        int processed = 0;
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            processed++;
            for (GraphNode downstream : nodeMap.get(nodeId).getDownstreams()) {
                String downId = downstream.getFactor().factorId();
                int newDeg = inDegrees.merge(downId, -1, Integer::sum);
                if (newDeg == 0) {
                    queue.add(downId);
                }
            }
        }

        if (processed < totalFactors) {
            throw new IllegalStateException(
                    "[GraphExecutor] Circular dependency detected among factors. " +
                    "Processed " + processed + " of " + totalFactors + " factors.");
        }
    }
}
```

- [ ] **Step 5: 实现 FactorRegistry**

`antispam-core/src/main/java/com/antispam/core/registry/FactorRegistry.java`:
```java
package com.antispam.core.registry;

import com.antispam.api.spi.Factor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 因子注册中心。收集所有 Spring 容器中的 Factor Bean，并按 factorId 索引。
 * 通过构造注入自动发现所有实现了 Factor 接口的 Bean。
 */
@Slf4j
@Component
public class FactorRegistry implements InitializingBean {

    private final List<Factor> allFactors;
    private Map<String, Factor> factorMap;

    public FactorRegistry(List<Factor> allFactors) {
        this.allFactors = allFactors == null ? Collections.emptyList() : allFactors;
    }

    @Override
    public void afterPropertiesSet() {
        Map<String, Factor> map = new HashMap<>();
        for (Factor factor : allFactors) {
            String id = factor.factorId();
            if (map.containsKey(id)) {
                throw new IllegalStateException("Duplicate factorId detected: " + id);
            }
            map.put(id, factor);
        }
        this.factorMap = Collections.unmodifiableMap(map);
        log.info("[FactorRegistry] Registered {} factors: {}", map.size(), map.keySet());
    }

    public Optional<Factor> getFactorById(String factorId) {
        return Optional.ofNullable(factorMap.get(factorId));
    }

    public List<Factor> getFactorsByIds(List<String> factorIds) {
        return factorIds.stream()
                .map(id -> getFactorById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown factorId: " + id)))
                .collect(Collectors.toList());
    }

    public List<Factor> getAll() {
        return allFactors;
    }
}
```

- [ ] **Step 6: 实现 DefaultRiskEngine（骨架，完整逻辑在 Task 5 补充）**

`antispam-core/src/main/java/com/antispam/core/engine/DefaultRiskEngine.java`:
```java
package com.antispam.core.engine;

import com.antispam.api.model.*;
import com.antispam.api.spi.*;
import com.antispam.core.graph.GraphExecutor;
import com.antispam.core.registry.FactorRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * RiskEngine 的默认实现，协调 GraphExecutor、PolicyRegistry、PunishmentExecutor。
 * 完整的 PolicyRegistry 和 PunishmentExecutor 注入在 Task 5/6 完成。
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class DefaultRiskEngine implements RiskEngine {

    private final GraphExecutor graphExecutor;
    private final FactorRegistry factorRegistry;
    private final long timeoutMs;

    @Override
    public RiskResponse evaluate(RiskContext context) {
        long start = System.currentTimeMillis();
        Objects.requireNonNull(context, "RiskContext must not be null");

        // 此处仅执行因子计算（Policy 和 Punishment 由 Task 5/6 接入）
        List<Factor> allFactors = factorRegistry.getAll();
        FactorMap factorMap = graphExecutor.execute(allFactors, context, timeoutMs);

        long elapsed = System.currentTimeMillis() - start;
        boolean timedOut = elapsed >= timeoutMs;

        return RiskResponse.builder()
                .level(RiskLevel.PASS)
                .factorValues(factorMap.toValueMap())
                .elapsedMs(elapsed)
                .timedOut(timedOut)
                .matchedPolicies(Collections.emptyList())
                .punishments(Collections.emptyList())
                .build();
    }
}
```

- [ ] **Step 7: 运行 GraphExecutor 测试，确认通过**

```bash
cd /Users/xiaowenzhuo/Desktop/antispam
mvn test -pl antispam-core -q
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 8: Commit**

```bash
git add antispam-core/
git commit -m "feat(core): implement GraphExecutor with DAG topology-driven parallel execution"
```

---

## Task 4: antispam-infra — Redis 和 Kafka 基础设施

**Files:**
- Create: `antispam-infra/src/main/java/com/antispam/infra/redis/RedisWindowCounter.java`
- Create: `antispam-infra/src/main/java/com/antispam/infra/redis/RedisKeyHelper.java`
- Create: `antispam-infra/src/main/java/com/antispam/infra/kafka/AuditEvent.java`
- Create: `antispam-infra/src/main/java/com/antispam/infra/kafka/PunishmentEvent.java`
- Create: `antispam-infra/src/main/java/com/antispam/infra/kafka/RiskKafkaProducer.java`
- Test: `antispam-infra/src/test/java/com/antispam/infra/redis/RedisKeyHelperTest.java`

**Interfaces:**
- Produces:
  - `RedisWindowCounter.count(String key, long windowStartMs, long windowEndMs) -> long`
  - `RedisWindowCounter.addEvent(String key, String member, long score, long ttlSeconds)`
  - `RedisKeyHelper.loginFreqKey(String userId) -> String`
  - `RedisKeyHelper.deviceCountKey(String userId) -> String`
  - `RedisKeyHelper.banKey(String userId) -> String`
  - `RedisKeyHelper.captchaKey(String userId) -> String`
  - `RiskKafkaProducer.sendPunishmentEvent(PunishmentEvent event)`
  - `RiskKafkaProducer.sendAuditLog(AuditEvent event)`

- [ ] **Step 1: 写 RedisKeyHelper 测试（失败）**

`antispam-infra/src/test/java/com/antispam/infra/redis/RedisKeyHelperTest.java`:
```java
package com.antispam.infra.redis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RedisKeyHelperTest {

    @Test
    void loginFreqKey_containsUserIdAndPrefix() {
        String key = RedisKeyHelper.loginFreqKey("user123");
        assertEquals("antispam:login_freq:user123", key);
    }

    @Test
    void deviceCountKey_containsUserIdAndPrefix() {
        String key = RedisKeyHelper.deviceCountKey("user123");
        assertEquals("antispam:device_count:user123", key);
    }

    @Test
    void banKey_containsUserIdAndPrefix() {
        String key = RedisKeyHelper.banKey("user123");
        assertEquals("antispam:ban:user123", key);
    }

    @Test
    void captchaKey_containsUserIdAndPrefix() {
        String key = RedisKeyHelper.captchaKey("user123");
        assertEquals("antispam:captcha:user123", key);
    }
}
```

- [ ] **Step 2: 实现 RedisKeyHelper**

`antispam-infra/src/main/java/com/antispam/infra/redis/RedisKeyHelper.java`:
```java
package com.antispam.infra.redis;

/**
 * Redis Key 命名规范：antispam:{功能}:{维度}
 */
public final class RedisKeyHelper {
    private static final String PREFIX = "antispam";

    private RedisKeyHelper() {}

    public static String loginFreqKey(String userId) {
        return PREFIX + ":login_freq:" + userId;
    }

    public static String deviceCountKey(String userId) {
        return PREFIX + ":device_count:" + userId;
    }

    public static String banKey(String userId) {
        return PREFIX + ":ban:" + userId;
    }

    public static String captchaKey(String userId) {
        return PREFIX + ":captcha:" + userId;
    }

    public static String rateLimitKey(String userId) {
        return PREFIX + ":rate_limit:" + userId;
    }
}
```

- [ ] **Step 3: 实现 RedisWindowCounter（ZSet 滑动窗口）**

`antispam-infra/src/main/java/com/antispam/infra/redis/RedisWindowCounter.java`:
```java
package com.antispam.infra.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis ZSet 的滑动窗口计数器。
 * Score = 事件时间戳（毫秒），Member = 唯一事件 ID。
 * 使用 ZADD + ZCOUNT + ZREMRANGEBYSCORE 实现精准滑动窗口。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RedisWindowCounter {

    private final StringRedisTemplate redisTemplate;

    /**
     * 统计 [windowStartMs, windowEndMs] 时间范围内的事件数。
     *
     * @param key            Redis ZSet Key（由 RedisKeyHelper 生成）
     * @param windowStartMs  窗口起始时间戳（毫秒）
     * @param windowEndMs    窗口结束时间戳（毫秒）
     * @return 窗口内事件数，Redis 不可用时返回 0
     */
    public long count(String key, long windowStartMs, long windowEndMs) {
        try {
            Long count = redisTemplate.opsForZSet().count(key,
                    (double) windowStartMs, (double) windowEndMs);
            return count == null ? 0L : count;
        } catch (Exception e) {
            log.warn("[RedisWindowCounter] Failed to count key={}: {}", key, e.getMessage());
            return 0L;
        }
    }

    /**
     * 记录一次事件到滑动窗口。
     *
     * @param key        Redis ZSet Key
     * @param score      事件时间戳（毫秒），作为 ZSet score
     * @param ttlSeconds ZSet 的 TTL（秒），防止 key 永久增长
     */
    public void addEvent(String key, long score, long ttlSeconds) {
        try {
            String member = UUID.randomUUID().toString();
            redisTemplate.opsForZSet().add(key, member, (double) score);
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            // 清理比 TTL 更旧的数据（防止 ZSet 无限增长）
            long windowStart = score - ttlSeconds * 1000;
            redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, windowStart);
        } catch (Exception e) {
            log.warn("[RedisWindowCounter] Failed to add event to key={}: {}", key, e.getMessage());
        }
    }

    /**
     * 向 Redis Set 中添加一个成员（用于设备去重统计）。
     *
     * @param key        Redis Set Key
     * @param member     要添加的成员（如 deviceId）
     * @param ttlSeconds Set 的 TTL（秒）
     */
    public void addToSet(String key, String member, long ttlSeconds) {
        try {
            redisTemplate.opsForSet().add(key, member);
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[RedisWindowCounter] Failed to add to set key={}: {}", key, e.getMessage());
        }
    }

    /**
     * 统计 Redis Set 的成员数（用于设备数量统计）。
     */
    public long countSet(String key) {
        try {
            Long size = redisTemplate.opsForSet().size(key);
            return size == null ? 0L : size;
        } catch (Exception e) {
            log.warn("[RedisWindowCounter] Failed to count set key={}: {}", key, e.getMessage());
            return 0L;
        }
    }
}
```

- [ ] **Step 4: 实现 Kafka 事件 DTO 和 Producer**

`antispam-infra/src/main/java/com/antispam/infra/kafka/PunishmentEvent.java`:
```java
package com.antispam.infra.kafka;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class PunishmentEvent {
    private final String requestId;
    private final String userId;
    private final String punishmentId;
    private final String riskLevel;
    private final String businessType;
    private final long timestamp;
}
```

`antispam-infra/src/main/java/com/antispam/infra/kafka/AuditEvent.java`:
```java
package com.antispam.infra.kafka;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

@Getter
@Builder
@ToString
public class AuditEvent {
    private final String requestId;
    private final String userId;
    private final String businessType;
    private final String eventType;
    private final String riskLevel;
    private final boolean timedOut;
    private final long elapsedMs;
    private final Map<String, Object> factorValues;
    private final long timestamp;
}
```

`antispam-infra/src/main/java/com/antispam/infra/kafka/RiskKafkaProducer.java`:
```java
package com.antispam.infra.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 消息生产者。发送处罚事件和审计日志到对应 Topic。
 * 使用异步发送（send 返回 Future），不阻塞主流程。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RiskKafkaProducer {

    static final String PUNISHMENT_TOPIC = "antispam.punishment.events";
    static final String AUDIT_TOPIC = "antispam.audit.logs";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 异步发送处罚事件，按 userId 分区。
     */
    public void sendPunishmentEvent(PunishmentEvent event) {
        kafkaTemplate.send(PUNISHMENT_TOPIC, event.getUserId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[RiskKafkaProducer] Failed to send punishment event for user={}: {}",
                                event.getUserId(), ex.getMessage());
                    } else {
                        log.debug("[RiskKafkaProducer] Punishment event sent for user={}", event.getUserId());
                    }
                });
    }

    /**
     * 异步发送审计日志。
     */
    public void sendAuditLog(AuditEvent event) {
        kafkaTemplate.send(AUDIT_TOPIC, event.getRequestId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[RiskKafkaProducer] Failed to send audit log for requestId={}: {}",
                                event.getRequestId(), ex.getMessage());
                    }
                });
    }
}
```

- [ ] **Step 5: 运行 infra 模块测试**

```bash
cd /Users/xiaowenzhuo/Desktop/antispam
mvn test -pl antispam-infra -q
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`（只有 RedisKeyHelperTest，Redis/Kafka 相关类不写单元测试，集成测试在 starter 模块）

- [ ] **Step 6: Commit**

```bash
git add antispam-infra/
git commit -m "feat(infra): add Redis sliding window counter and Kafka producer"
```

---

## Task 5: antispam-factor — 因子实现（LoginFreqFactor、DeviceCountFactor）

**Files:**
- Create: `antispam-factor/src/main/java/com/antispam/factor/LoginFreqFactor.java`
- Create: `antispam-factor/src/main/java/com/antispam/factor/DeviceCountFactor.java`
- Test: `antispam-factor/src/test/java/com/antispam/factor/LoginFreqFactorTest.java`
- Test: `antispam-factor/src/test/java/com/antispam/factor/DeviceCountFactorTest.java`

**Interfaces:**
- Consumes:
  - `RedisWindowCounter.count(String key, long windowStartMs, long windowEndMs) -> long`
  - `RedisWindowCounter.countSet(String key) -> long`
  - `RedisKeyHelper.loginFreqKey(String userId) -> String`
  - `RedisKeyHelper.deviceCountKey(String userId) -> String`
  - `RiskContext` — `getUserId()`, `getDeviceId()`, `getTimestamp()`
  - `FactorResult.success(Object value)`, `FactorResult.failure(Object fallback, String reason)`
- Produces:
  - `LoginFreqFactor` — `factorId()` = `"loginFreq1Min"`, `dependencies()` = `[]`, 返回 1 分钟内登录次数（Long）
  - `DeviceCountFactor` — `factorId()` = `"deviceCount24h"`, `dependencies()` = `[]`, 返回 24 小时内设备数（Long）

- [ ] **Step 1: 写 LoginFreqFactor 失败测试**

`antispam-factor/src/test/java/com/antispam/factor/LoginFreqFactorTest.java`:
```java
package com.antispam.factor;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.FactorResult;
import com.antispam.api.model.RiskContext;
import com.antispam.infra.redis.RedisWindowCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginFreqFactorTest {

    @Mock
    private RedisWindowCounter redisWindowCounter;

    private LoginFreqFactor factor;

    @BeforeEach
    void setUp() {
        factor = new LoginFreqFactor(redisWindowCounter);
    }

    @Test
    void factorId_isLoginFreq1Min() {
        assertEquals("loginFreq1Min", factor.factorId());
    }

    @Test
    void dependencies_isEmpty() {
        assertTrue(factor.dependencies().isEmpty());
    }

    @Test
    void compute_returnsLoginCount() {
        when(redisWindowCounter.count(eq("antispam:login_freq:user1"), anyLong(), anyLong()))
                .thenReturn(3L);

        RiskContext ctx = RiskContext.builder().userId("user1").build();
        FactorResult result = factor.compute(ctx, new FactorMap());

        assertTrue(result.isSuccess());
        assertEquals(3L, result.getValue());
    }

    @Test
    void compute_whenRedisReturnsZero_returnsZero() {
        when(redisWindowCounter.count(anyString(), anyLong(), anyLong())).thenReturn(0L);

        RiskContext ctx = RiskContext.builder().userId("u2").build();
        FactorResult result = factor.compute(ctx, new FactorMap());

        assertTrue(result.isSuccess());
        assertEquals(0L, result.getValue());
    }
}
```

- [ ] **Step 2: 写 DeviceCountFactor 失败测试**

`antispam-factor/src/test/java/com/antispam/factor/DeviceCountFactorTest.java`:
```java
package com.antispam.factor;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.FactorResult;
import com.antispam.api.model.RiskContext;
import com.antispam.infra.redis.RedisWindowCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceCountFactorTest {

    @Mock
    private RedisWindowCounter redisWindowCounter;

    private DeviceCountFactor factor;

    @BeforeEach
    void setUp() {
        factor = new DeviceCountFactor(redisWindowCounter);
    }

    @Test
    void factorId_isDeviceCount24h() {
        assertEquals("deviceCount24h", factor.factorId());
    }

    @Test
    void dependencies_isEmpty() {
        assertTrue(factor.dependencies().isEmpty());
    }

    @Test
    void compute_returnsDeviceCount() {
        when(redisWindowCounter.countSet("antispam:device_count:user1")).thenReturn(2L);

        RiskContext ctx = RiskContext.builder().userId("user1").deviceId("dev1").build();
        FactorResult result = factor.compute(ctx, new FactorMap());

        assertTrue(result.isSuccess());
        assertEquals(2L, result.getValue());
    }
}
```

- [ ] **Step 3: 实现 LoginFreqFactor**

`antispam-factor/src/main/java/com/antispam/factor/LoginFreqFactor.java`:
```java
package com.antispam.factor;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.FactorResult;
import com.antispam.api.model.RiskContext;
import com.antispam.api.spi.Factor;
import com.antispam.infra.redis.RedisKeyHelper;
import com.antispam.infra.redis.RedisWindowCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 登录频次因子：统计指定用户最近 1 分钟内的登录事件次数。
 * 依赖 Redis ZSet 滑动窗口，Redis 不可用时降级返回 0。
 */
@Component
@RequiredArgsConstructor
public class LoginFreqFactor implements Factor {

    /** 滑动窗口大小：1 分钟（毫秒） */
    private static final long WINDOW_MS = 60_000L;

    private final RedisWindowCounter redisWindowCounter;

    @Override
    public String factorId() {
        return "loginFreq1Min";
    }

    @Override
    public List<String> dependencies() {
        return List.of(); // 无上游依赖
    }

    @Override
    public FactorResult compute(RiskContext ctx, FactorMap upstream) {
        String key = RedisKeyHelper.loginFreqKey(ctx.getUserId());
        long now = ctx.getTimestamp();
        long windowStart = now - WINDOW_MS;

        // 记录本次登录事件到滑动窗口（调用方在风控前主动埋点时可省略此步）
        // redisWindowCounter.addEvent(key, now, 120); // 可选：埋点

        long count = redisWindowCounter.count(key, windowStart, now);
        return FactorResult.success(count);
    }
}
```

- [ ] **Step 4: 实现 DeviceCountFactor**

`antispam-factor/src/main/java/com/antispam/factor/DeviceCountFactor.java`:
```java
package com.antispam.factor;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.FactorResult;
import com.antispam.api.model.RiskContext;
import com.antispam.api.spi.Factor;
import com.antispam.infra.redis.RedisKeyHelper;
import com.antispam.infra.redis.RedisWindowCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 设备数量因子：统计指定用户最近 24 小时内使用过的不同设备数量。
 * 使用 Redis Set 存储设备 ID，SCARD 获取集合大小。
 * Redis 不可用时降级返回 0。
 */
@Component
@RequiredArgsConstructor
public class DeviceCountFactor implements Factor {

    /** 统计窗口：24 小时（秒） */
    private static final long WINDOW_SECONDS = 86_400L;

    private final RedisWindowCounter redisWindowCounter;

    @Override
    public String factorId() {
        return "deviceCount24h";
    }

    @Override
    public List<String> dependencies() {
        return List.of(); // 无上游依赖
    }

    @Override
    public FactorResult compute(RiskContext ctx, FactorMap upstream) {
        String key = RedisKeyHelper.deviceCountKey(ctx.getUserId());

        // 记录本次使用的设备（调用方在风控前主动埋点时可省略此步）
        // if (ctx.getDeviceId() != null) {
        //     redisWindowCounter.addToSet(key, ctx.getDeviceId(), WINDOW_SECONDS);
        // }

        long count = redisWindowCounter.countSet(key);
        return FactorResult.success(count);
    }
}
```

- [ ] **Step 5: 运行因子测试**

```bash
cd /Users/xiaowenzhuo/Desktop/antispam
mvn test -pl antispam-factor -q
```

Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add antispam-factor/
git commit -m "feat(factor): implement LoginFreqFactor and DeviceCountFactor with Redis sliding window"
```

---

## Task 6: antispam-policy — 套餐注册和 Aviator 规则评估

**Files:**
- Create: `antispam-policy/src/main/java/com/antispam/policy/registry/PolicyRegistry.java`
- Create: `antispam-policy/src/main/java/com/antispam/policy/aviator/AviatorRuleEvaluator.java`
- Create: `antispam-policy/src/main/java/com/antispam/policy/aviator/PolicyRule.java`
- Create: `antispam-policy/src/main/java/com/antispam/policy/example/LoginRiskPolicy.java`
- Test: `antispam-policy/src/test/java/com/antispam/policy/aviator/AviatorRuleEvaluatorTest.java`
- Test: `antispam-policy/src/test/java/com/antispam/policy/example/LoginRiskPolicyTest.java`

**Interfaces:**
- Consumes:
  - `PolicyPackage` — `policyId()`, `businessType()`, `requiredFactors()`, `evaluate(FactorMap)`
  - `FactorMap.toValueMap() -> Map<String, Object>`
  - `PolicyResult.noMatch()`, `PolicyResult.builder()`
  - `RiskLevel.PASS`, `REVIEW`, `BLOCK`
- Produces:
  - `PolicyRegistry.getByBusinessType(String businessType) -> List<PolicyPackage>`
  - `AviatorRuleEvaluator.evaluate(String expression, Map<String,Object> variables) -> boolean`
  - `PolicyRule` — `expression: String`, `level: RiskLevel`, `punishmentIds: List<String>`
  - `LoginRiskPolicy implements PolicyPackage`

- [ ] **Step 1: 写 AviatorRuleEvaluator 失败测试**

`antispam-policy/src/test/java/com/antispam/policy/aviator/AviatorRuleEvaluatorTest.java`:
```java
package com.antispam.policy.aviator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AviatorRuleEvaluatorTest {

    private AviatorRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AviatorRuleEvaluator();
    }

    @Test
    void evaluate_simpleComparison_returnsTrue() {
        boolean result = evaluator.evaluate("loginFreq1Min > 5",
                Map.of("loginFreq1Min", 6L));
        assertTrue(result);
    }

    @Test
    void evaluate_simpleComparison_returnsFalse() {
        boolean result = evaluator.evaluate("loginFreq1Min > 5",
                Map.of("loginFreq1Min", 3L));
        assertFalse(result);
    }

    @Test
    void evaluate_andExpression_requiresBothConditions() {
        boolean result = evaluator.evaluate(
                "loginFreq1Min > 5 && deviceCount24h > 3",
                Map.of("loginFreq1Min", 6L, "deviceCount24h", 4L));
        assertTrue(result);

        boolean resultFalse = evaluator.evaluate(
                "loginFreq1Min > 5 && deviceCount24h > 3",
                Map.of("loginFreq1Min", 6L, "deviceCount24h", 2L));
        assertFalse(resultFalse);
    }

    @Test
    void evaluate_invalidExpression_returnsFalse() {
        // 不抛出异常，安全降级
        boolean result = evaluator.evaluate("INVALID_EXPR ###", Map.of());
        assertFalse(result);
    }
}
```

- [ ] **Step 2: 写 LoginRiskPolicy 失败测试**

`antispam-policy/src/test/java/com/antispam/policy/example/LoginRiskPolicyTest.java`:
```java
package com.antispam.policy.example;

import com.antispam.api.model.FactorMap;
import com.antispam.api.model.FactorResult;
import com.antispam.api.model.PolicyResult;
import com.antispam.api.model.RiskLevel;
import com.antispam.policy.aviator.AviatorRuleEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginRiskPolicyTest {

    private LoginRiskPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new LoginRiskPolicy(new AviatorRuleEvaluator());
    }

    @Test
    void evaluate_normalUser_returnsPass() {
        FactorMap factorMap = new FactorMap();
        factorMap.put("loginFreq1Min", FactorResult.success(1L));
        factorMap.put("deviceCount24h", FactorResult.success(1L));

        PolicyResult result = policy.evaluate(factorMap);
        assertFalse(result.isMatched());
        assertEquals(RiskLevel.PASS, result.getSuggestedLevel());
    }

    @Test
    void evaluate_highLoginFreqAndManyDevices_returnsReview() {
        FactorMap factorMap = new FactorMap();
        factorMap.put("loginFreq1Min", FactorResult.success(6L)); // > 5
        factorMap.put("deviceCount24h", FactorResult.success(4L)); // > 3

        PolicyResult result = policy.evaluate(factorMap);
        assertTrue(result.isMatched());
        assertEquals(RiskLevel.REVIEW, result.getSuggestedLevel());
        assertTrue(result.getPunishmentIds().contains("captcha"));
    }

    @Test
    void evaluate_extremeLoginFreq_returnsBlock() {
        FactorMap factorMap = new FactorMap();
        factorMap.put("loginFreq1Min", FactorResult.success(11L)); // > 10
        factorMap.put("deviceCount24h", FactorResult.success(1L));

        PolicyResult result = policy.evaluate(factorMap);
        assertTrue(result.isMatched());
        assertEquals(RiskLevel.BLOCK, result.getSuggestedLevel());
        assertTrue(result.getPunishmentIds().contains("banAccount"));
    }

    @Test
    void policyId_andBusinessType_areSet() {
        assertEquals("ECOMMERCE", policy.businessType());
        assertEquals("loginRiskPolicy", policy.policyId());
    }

    @Test
    void requiredFactors_containsBothFactors() {
        assertTrue(policy.requiredFactors().contains("loginFreq1Min"));
        assertTrue(policy.requiredFactors().contains("deviceCount24h"));
    }
}
```

- [ ] **Step 3: 实现 AviatorRuleEvaluator**

`antispam-policy/src/main/java/com/antispam/policy/aviator/AviatorRuleEvaluator.java`:
```java
package com.antispam.policy.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Options;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 基于 Aviator 的规则表达式求值器。
 * Aviator 表达式示例：
 *   "loginFreq1Min > 5 && deviceCount24h > 3"
 *   "ipRiskScore >= 80"
 * 表达式中的变量名对应 FactorMap.toValueMap() 中的 key。
 */
@Slf4j
@Component
public class AviatorRuleEvaluator {

    private final AviatorEvaluatorInstance aviator;

    public AviatorRuleEvaluator() {
        this.aviator = AviatorEvaluator.newInstance();
        // 允许表达式访问 null 变量（未计算的因子默认为 null）
        this.aviator.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, false);
    }

    /**
     * 对给定变量环境求值 Aviator 表达式。
     *
     * @param expression Aviator 布尔表达式字符串
     * @param variables  变量 Map（通常来自 FactorMap.toValueMap()）
     * @return 表达式结果为 true 时返回 true；任何错误（包括解析失败）返回 false
     */
    public boolean evaluate(String expression, Map<String, Object> variables) {
        try {
            Object result = aviator.execute(expression, variables);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("[AviatorRuleEvaluator] Expression evaluation failed: [{}], error: {}",
                    expression, e.getMessage());
            return false; // 安全降级：规则求值失败不触发处罚
        }
    }
}
```

- [ ] **Step 4: 实现 PolicyRule（规则描述对象）**

`antispam-policy/src/main/java/com/antispam/policy/aviator/PolicyRule.java`:
```java
package com.antispam.policy.aviator;

import com.antispam.api.model.RiskLevel;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 单条规则：Aviator 表达式 + 命中后的风险级别 + 处罚 ID 列表。
 * 一个套餐（PolicyPackage）通常包含多条规则，按顺序（严重程度降序）评估。
 */
@Getter
@Builder
public class PolicyRule {
    /** Aviator 布尔表达式 */
    private final String expression;
    /** 命中时建议的风险级别 */
    private final RiskLevel level;
    /** 命中时需要执行的处罚 ID 列表 */
    private final List<String> punishmentIds;
    /** 规则描述（调试用） */
    private final String description;
}
```

- [ ] **Step 5: 实现 LoginRiskPolicy（示例套餐）**

`antispam-policy/src/main/java/com/antispam/policy/example/LoginRiskPolicy.java`:
```java
package com.antispam.policy.example;

import com.antispam.api.model.*;
import com.antispam.api.spi.PolicyPackage;
import com.antispam.policy.aviator.AviatorRuleEvaluator;
import com.antispam.policy.aviator.PolicyRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 示例套餐：登录风险策略。
 * 业务种类：ECOMMERCE，事件类型：LOGIN。
 *
 * 规则（按严重程度降序评估，第一条命中即返回）：
 *   1. loginFreq1Min > 10                                → BLOCK  + banAccount
 *   2. loginFreq1Min > 5 && deviceCount24h > 3          → REVIEW + captcha
 *   3. deviceCount24h > 5                               → REVIEW + captcha + rateLimit
 */
@Component
@RequiredArgsConstructor
public class LoginRiskPolicy implements PolicyPackage {

    private static final List<PolicyRule> RULES = List.of(
            PolicyRule.builder()
                    .expression("loginFreq1Min > 10")
                    .level(RiskLevel.BLOCK)
                    .punishmentIds(List.of("banAccount"))
                    .description("极高频登录直接封号")
                    .build(),
            PolicyRule.builder()
                    .expression("loginFreq1Min > 5 && deviceCount24h > 3")
                    .level(RiskLevel.REVIEW)
                    .punishmentIds(List.of("captcha"))
                    .description("高频登录且多设备，弹验证码")
                    .build(),
            PolicyRule.builder()
                    .expression("deviceCount24h > 5")
                    .level(RiskLevel.REVIEW)
                    .punishmentIds(List.of("captcha", "rateLimit"))
                    .description("设备数异常，弹验证码+限速")
                    .build()
    );

    private final AviatorRuleEvaluator ruleEvaluator;

    @Override
    public String policyId() {
        return "loginRiskPolicy";
    }

    @Override
    public String businessType() {
        return "ECOMMERCE";
    }

    @Override
    public List<String> requiredFactors() {
        return List.of("loginFreq1Min", "deviceCount24h");
    }

    @Override
    public PolicyResult evaluate(FactorMap facts) {
        Map<String, Object> variables = facts.toValueMap();

        for (PolicyRule rule : RULES) {
            if (ruleEvaluator.evaluate(rule.getExpression(), variables)) {
                return PolicyResult.builder()
                        .matched(true)
                        .suggestedLevel(rule.getLevel())
                        .punishmentIds(rule.getPunishmentIds())
                        .matchedRule(rule.getDescription())
                        .build();
            }
        }

        return PolicyResult.noMatch();
    }
}
```

- [ ] **Step 6: 实现 PolicyRegistry**

`antispam-policy/src/main/java/com/antispam/policy/registry/PolicyRegistry.java`:
```java
package com.antispam.policy.registry;

import com.antispam.api.spi.PolicyPackage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 套餐注册中心。收集所有 Spring 容器中的 PolicyPackage Bean，按 businessType 索引。
 */
@Slf4j
@Component
public class PolicyRegistry implements InitializingBean {

    private final List<PolicyPackage> allPolicies;
    private Map<String, List<PolicyPackage>> byBusinessType;

    public PolicyRegistry(List<PolicyPackage> allPolicies) {
        this.allPolicies = allPolicies == null ? Collections.emptyList() : allPolicies;
    }

    @Override
    public void afterPropertiesSet() {
        Map<String, List<PolicyPackage>> map = new HashMap<>();
        for (PolicyPackage policy : allPolicies) {
            map.computeIfAbsent(policy.businessType(), k -> new ArrayList<>()).add(policy);
        }
        this.byBusinessType = Collections.unmodifiableMap(map);
        log.info("[PolicyRegistry] Registered {} policies across {} business types: {}",
                allPolicies.size(), map.size(), map.keySet());
    }

    /**
     * 获取指定业务种类下的所有套餐（按注册顺序）。
     */
    public List<PolicyPackage> getByBusinessType(String businessType) {
        return byBusinessType.getOrDefault(businessType, Collections.emptyList());
    }
}
```

- [ ] **Step 7: 运行 policy 模块测试**

```bash
cd /Users/xiaowenzhuo/Desktop/antispam
mvn test -pl antispam-policy -q
```

Expected: `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 8: Commit**

```bash
git add antispam-policy/
git commit -m "feat(policy): implement AviatorRuleEvaluator, PolicyRegistry, and LoginRiskPolicy"
```

---

## Task 7: antispam-punishment — 处罚执行器

**Files:**
- Create: `antispam-punishment/src/main/java/com/antispam/punishment/registry/PunishmentRegistry.java`
- Create: `antispam-punishment/src/main/java/com/antispam/punishment/executor/PunishmentExecutor.java`
- Create: `antispam-punishment/src/main/java/com/antispam/punishment/impl/CaptchaPunishment.java`
- Create: `antispam-punishment/src/main/java/com/antispam/punishment/impl/BanAccountPunishment.java`
- Test: `antispam-punishment/src/test/java/com/antispam/punishment/impl/CaptchaPunishmentTest.java`
- Test: `antispam-punishment/src/test/java/com/antispam/punishment/impl/BanAccountPunishmentTest.java`

**Interfaces:**
- Consumes:
  - `Punishment` — `punishmentId()`, `type()`, `execute(PunishmentContext)`
  - `PunishmentContext` — `getRiskContext()`, `getLevel()`, `getConfig()`
  - `RiskContext` — `getUserId()`
  - `RedisWindowCounter` — `countSet()` (used for redis operations)
  - `StringRedisTemplate` — `opsForValue().set(...)` (for ban/captcha keys)
  - `RiskKafkaProducer.sendPunishmentEvent(PunishmentEvent)`
- Produces:
  - `PunishmentRegistry.getByIds(List<String> ids) -> List<Punishment>`
  - `PunishmentExecutor.execute(List<String> punishmentIds, PunishmentContext ctx) -> List<PunishmentResult>`
  - `CaptchaPunishment.punishmentId()` = `"captcha"`, TTL = 5 分钟
  - `BanAccountPunishment.punishmentId()` = `"banAccount"`, TTL = 24 小时（可配置）

- [ ] **Step 1: 写 CaptchaPunishment 失败测试**

`antispam-punishment/src/test/java/com/antispam/punishment/impl/CaptchaPunishmentTest.java`:
```java
package com.antispam.punishment.impl;

import com.antispam.api.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaptchaPunishmentTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private CaptchaPunishment punishment;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        punishment = new CaptchaPunishment(redisTemplate);
    }

    @Test
    void punishmentId_isCaptcha() {
        assertEquals("captcha", punishment.punishmentId());
    }

    @Test
    void type_isInternal() {
        assertEquals(PunishmentType.INTERNAL, punishment.type());
    }

    @Test
    void execute_writesRedisKeyWithTtl() {
        RiskContext ctx = RiskContext.builder().userId("user1").build();
        PunishmentContext pCtx = PunishmentContext.builder()
                .riskContext(ctx).level(RiskLevel.REVIEW).build();

        PunishmentResult result = punishment.execute(pCtx);

        verify(valueOps).set(eq("antispam:captcha:user1"), eq("1"), eq(300L), eq(TimeUnit.SECONDS));
        assertTrue(result.isExecuted());
        assertEquals("captcha", result.getPunishmentId());
    }

    @Test
    void execute_whenRedisThrows_returnsFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("Redis down")).when(valueOps)
                .set(anyString(), anyString(), anyLong(), any());

        RiskContext ctx = RiskContext.builder().userId("user1").build();
        PunishmentContext pCtx = PunishmentContext.builder()
                .riskContext(ctx).level(RiskLevel.REVIEW).build();

        PunishmentResult result = punishment.execute(pCtx);

        assertFalse(result.isExecuted());
    }
}
```

- [ ] **Step 2: 写 BanAccountPunishment 失败测试**

`antispam-punishment/src/test/java/com/antispam/punishment/impl/BanAccountPunishmentTest.java`:
```java
package com.antispam.punishment.impl;

import com.antispam.api.model.*;
import com.antispam.infra.kafka.PunishmentEvent;
import com.antispam.infra.kafka.RiskKafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BanAccountPunishmentTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private RiskKafkaProducer kafkaProducer;

    private BanAccountPunishment punishment;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        punishment = new BanAccountPunishment(redisTemplate, kafkaProducer);
    }

    @Test
    void punishmentId_isBanAccount() {
        assertEquals("banAccount", punishment.punishmentId());
    }

    @Test
    void type_isInternal() {
        assertEquals(PunishmentType.INTERNAL, punishment.type());
    }

    @Test
    void execute_writesBanKeyAndSendsKafkaEvent() {
        RiskContext ctx = RiskContext.builder()
                .userId("user1").businessType("ECOMMERCE").build();
        PunishmentContext pCtx = PunishmentContext.builder()
                .riskContext(ctx).level(RiskLevel.BLOCK).build();

        PunishmentResult result = punishment.execute(pCtx);

        // 验证写入 Redis ban key（24 小时）
        verify(valueOps).set(eq("antispam:ban:user1"), eq("BLOCK"), eq(86400L), eq(TimeUnit.SECONDS));

        // 验证推送 Kafka 事件
        ArgumentCaptor<PunishmentEvent> captor = ArgumentCaptor.forClass(PunishmentEvent.class);
        verify(kafkaProducer).sendPunishmentEvent(captor.capture());
        assertEquals("user1", captor.getValue().getUserId());
        assertEquals("banAccount", captor.getValue().getPunishmentId());

        assertTrue(result.isExecuted());
    }
}
```

- [ ] **Step 3: 实现 CaptchaPunishment**

`antispam-punishment/src/main/java/com/antispam/punishment/impl/CaptchaPunishment.java`:
```java
package com.antispam.punishment.impl;

import com.antispam.api.model.*;
import com.antispam.api.spi.Punishment;
import com.antispam.infra.redis.RedisKeyHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 验证码处罚：在 Redis 中为用户打标，下次请求时触发验证码验证。
 * Key: antispam:captcha:{userId}，TTL: 5 分钟（300 秒）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaptchaPunishment implements Punishment {

    private static final long TTL_SECONDS = 300L; // 5 分钟

    private final StringRedisTemplate redisTemplate;

    @Override
    public String punishmentId() {
        return "captcha";
    }

    @Override
    public PunishmentType type() {
        return PunishmentType.INTERNAL;
    }

    @Override
    public PunishmentResult execute(PunishmentContext ctx) {
        String userId = ctx.getRiskContext().getUserId();
        String key = RedisKeyHelper.captchaKey(userId);
        try {
            redisTemplate.opsForValue().set(key, "1", TTL_SECONDS, TimeUnit.SECONDS);
            log.info("[CaptchaPunishment] Captcha flag set for userId={}, ttl={}s", userId, TTL_SECONDS);
            return PunishmentResult.success(punishmentId());
        } catch (Exception e) {
            log.error("[CaptchaPunishment] Failed to set captcha flag for userId={}: {}", userId, e.getMessage());
            return PunishmentResult.failure(punishmentId(), e.getMessage());
        }
    }
}
```

- [ ] **Step 4: 实现 BanAccountPunishment**

`antispam-punishment/src/main/java/com/antispam/punishment/impl/BanAccountPunishment.java`:
```java
package com.antispam.punishment.impl;

import com.antispam.api.model.*;
import com.antispam.api.spi.Punishment;
import com.antispam.infra.kafka.PunishmentEvent;
import com.antispam.infra.kafka.RiskKafkaProducer;
import com.antispam.infra.redis.RedisKeyHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 封号处罚：将用户 ID 写入 Redis 黑名单，同时推送 Kafka 事件供下游系统消费。
 * Key: antispam:ban:{userId}，TTL: 24 小时（86400 秒，默认）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BanAccountPunishment implements Punishment {

    private static final long DEFAULT_BAN_SECONDS = 86_400L; // 24 小时

    private final StringRedisTemplate redisTemplate;
    private final RiskKafkaProducer kafkaProducer;

    @Override
    public String punishmentId() {
        return "banAccount";
    }

    @Override
    public PunishmentType type() {
        return PunishmentType.INTERNAL;
    }

    @Override
    public PunishmentResult execute(PunishmentContext ctx) {
        String userId = ctx.getRiskContext().getUserId();
        String key = RedisKeyHelper.banKey(userId);
        long banSeconds = (Long) ctx.getConfig().getOrDefault("banDurationSeconds", DEFAULT_BAN_SECONDS);

        try {
            // 1. 写 Redis 黑名单
            redisTemplate.opsForValue().set(key, ctx.getLevel().name(), banSeconds, TimeUnit.SECONDS);
            log.info("[BanAccountPunishment] Banned userId={} for {}s", userId, banSeconds);

            // 2. 异步推 Kafka
            kafkaProducer.sendPunishmentEvent(PunishmentEvent.builder()
                    .requestId(userId + "-" + System.currentTimeMillis())
                    .userId(userId)
                    .punishmentId(punishmentId())
                    .riskLevel(ctx.getLevel().name())
                    .businessType(ctx.getRiskContext().getBusinessType())
                    .timestamp(System.currentTimeMillis())
                    .build());

            return PunishmentResult.success(punishmentId());
        } catch (Exception e) {
            log.error("[BanAccountPunishment] Failed to ban userId={}: {}", userId, e.getMessage());
            return PunishmentResult.failure(punishmentId(), e.getMessage());
        }
    }
}
```

- [ ] **Step 5: 实现 PunishmentRegistry 和 PunishmentExecutor**

`antispam-punishment/src/main/java/com/antispam/punishment/registry/PunishmentRegistry.java`:
```java
package com.antispam.punishment.registry;

import com.antispam.api.spi.Punishment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PunishmentRegistry implements InitializingBean {

    private final List<Punishment> allPunishments;
    private Map<String, Punishment> punishmentMap;

    public PunishmentRegistry(List<Punishment> allPunishments) {
        this.allPunishments = allPunishments == null ? Collections.emptyList() : allPunishments;
    }

    @Override
    public void afterPropertiesSet() {
        Map<String, Punishment> map = new HashMap<>();
        for (Punishment p : allPunishments) {
            if (map.containsKey(p.punishmentId())) {
                throw new IllegalStateException("Duplicate punishmentId: " + p.punishmentId());
            }
            map.put(p.punishmentId(), p);
        }
        this.punishmentMap = Collections.unmodifiableMap(map);
        log.info("[PunishmentRegistry] Registered {} punishments: {}", map.size(), map.keySet());
    }

    public Optional<Punishment> getById(String punishmentId) {
        return Optional.ofNullable(punishmentMap.get(punishmentId));
    }

    public List<Punishment> getByIds(List<String> punishmentIds) {
        return punishmentIds.stream()
                .map(id -> getById(id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
```

`antispam-punishment/src/main/java/com/antispam/punishment/executor/PunishmentExecutor.java`:
```java
package com.antispam.punishment.executor;

import com.antispam.api.model.PunishmentContext;
import com.antispam.api.model.PunishmentResult;
import com.antispam.api.spi.Punishment;
import com.antispam.punishment.registry.PunishmentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 处罚执行协调器。
 * 按处罚 ID 列表顺序执行所有处罚，任意一个失败不影响其他处罚执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PunishmentExecutor {

    private final PunishmentRegistry punishmentRegistry;

    /**
     * 执行处罚列表。
     *
     * @param punishmentIds 需要执行的处罚 ID 列表（由 PolicyResult 提供）
     * @param ctx           处罚上下文
     * @return 每个处罚的执行结果
     */
    public List<PunishmentResult> execute(List<String> punishmentIds, PunishmentContext ctx) {
        List<Punishment> punishments = punishmentRegistry.getByIds(punishmentIds);
        List<PunishmentResult> results = new ArrayList<>();

        for (Punishment punishment : punishments) {
            try {
                PunishmentResult result = punishment.execute(ctx);
                results.add(result);
                log.debug("[PunishmentExecutor] Punishment [{}] executed: {}", punishment.punishmentId(), result);
            } catch (Exception e) {
                // 不应进入此分支（Punishment 契约要求不抛出异常），兜底处理
                log.error("[PunishmentExecutor] Unexpected error from punishment [{}]: {}",
                        punishment.punishmentId(), e.getMessage());
                results.add(PunishmentResult.failure(punishment.punishmentId(), e.getMessage()));
            }
        }

        return results;
    }
}
```

- [ ] **Step 6: 运行 punishment 模块测试**

```bash
cd /Users/xiaowenzhuo/Desktop/antispam
mvn test -pl antispam-punishment -q
```

Expected: `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add antispam-punishment/
git commit -m "feat(punishment): implement CaptchaPunishment, BanAccountPunishment, PunishmentExecutor"
```

---

## Task 8: antispam-starter — 组装、AutoConfiguration 和 REST 入口

**Files:**
- Create: `antispam-starter/src/main/java/com/antispam/AntispamApplication.java`
- Create: `antispam-starter/src/main/java/com/antispam/config/RiskEngineProperties.java`
- Create: `antispam-starter/src/main/java/com/antispam/config/RiskEngineAutoConfiguration.java`
- Create: `antispam-starter/src/main/java/com/antispam/web/RiskEngineController.java`
- Create: `antispam-starter/src/main/java/com/antispam/web/RiskRequest.java`
- Create: `antispam-starter/src/main/resources/application.yml`
- Create: `antispam-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `antispam-core/src/main/java/com/antispam/core/engine/DefaultRiskEngine.java`（接入 PolicyRegistry 和 PunishmentExecutor）
- Test: `antispam-starter/src/test/java/com/antispam/web/RiskEngineControllerTest.java`

**Interfaces:**
- Consumes:
  - `GraphExecutor`, `FactorRegistry`, `PolicyRegistry`, `PunishmentRegistry`, `PunishmentExecutor`
  - `RiskEngineProperties` — `timeoutMs`, `threadPool.coreSize`, `threadPool.maxSize`
- Produces: 可运行的 Spring Boot 应用，`POST /api/risk/evaluate` 接受请求并返回 `RiskResponse`

- [ ] **Step 1: 创建 RiskEngineProperties**

`antispam-starter/src/main/java/com/antispam/config/RiskEngineProperties.java`:
```java
package com.antispam.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "antispam.engine")
public class RiskEngineProperties {
    private long timeoutMs = 200;
    private ThreadPoolProperties threadPool = new ThreadPoolProperties();

    @Data
    public static class ThreadPoolProperties {
        private int coreSize = 20;
        private int maxSize = 50;
        private int queueCapacity = 1000;
    }
}
```

- [ ] **Step 2: 创建 AutoConfiguration**

`antispam-starter/src/main/java/com/antispam/config/RiskEngineAutoConfiguration.java`:
```java
package com.antispam.config;

import com.antispam.core.graph.GraphExecutor;
import com.antispam.core.registry.FactorRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
@EnableConfigurationProperties(RiskEngineProperties.class)
@ComponentScan(basePackages = {
        "com.antispam.core",
        "com.antispam.factor",
        "com.antispam.policy",
        "com.antispam.punishment",
        "com.antispam.infra"
})
public class RiskEngineAutoConfiguration {

    @Bean
    public ExecutorService riskEngineThreadPool(RiskEngineProperties props) {
        RiskEngineProperties.ThreadPoolProperties tp = props.getThreadPool();
        return new ThreadPoolExecutor(
                tp.getCoreSize(),
                tp.getMaxSize(),
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(tp.getQueueCapacity()),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("risk-graph-" + t.getId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时，调用方线程执行（背压）
        );
    }

    @Bean
    public GraphExecutor graphExecutor(ExecutorService riskEngineThreadPool) {
        return new GraphExecutor(riskEngineThreadPool);
    }
}
```

- [ ] **Step 3: 更新 DefaultRiskEngine 接入 Policy 和 Punishment**

修改 `antispam-core/src/main/java/com/antispam/core/engine/DefaultRiskEngine.java`：
```java
package com.antispam.core.engine;

import com.antispam.api.model.*;
import com.antispam.api.spi.*;
import com.antispam.core.graph.GraphExecutor;
import com.antispam.core.registry.FactorRegistry;
import com.antispam.policy.registry.PolicyRegistry;
import com.antispam.punishment.executor.PunishmentExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class DefaultRiskEngine implements RiskEngine {

    private final GraphExecutor graphExecutor;
    private final FactorRegistry factorRegistry;
    private final PolicyRegistry policyRegistry;
    private final PunishmentExecutor punishmentExecutor;
    private final long timeoutMs;

    @Override
    public RiskResponse evaluate(RiskContext context) {
        long start = System.currentTimeMillis();
        Objects.requireNonNull(context, "RiskContext must not be null");

        // 1. 加载此业务种类对应的所有套餐
        List<PolicyPackage> policies = policyRegistry.getByBusinessType(context.getBusinessType());

        // 2. 收集所有需要的因子（去重）
        Set<String> requiredFactorIds = new LinkedHashSet<>();
        policies.forEach(p -> requiredFactorIds.addAll(p.requiredFactors()));
        List<Factor> factors = factorRegistry.getFactorsByIds(new ArrayList<>(requiredFactorIds));

        // 3. 执行 DAG 因子图
        boolean timedOut = false;
        FactorMap factorMap;
        try {
            factorMap = graphExecutor.execute(factors, context, timeoutMs);
        } catch (Exception e) {
            log.error("[DefaultRiskEngine] Graph execution failed: {}", e.getMessage());
            factorMap = new FactorMap();
            timedOut = true;
        }

        long graphElapsed = System.currentTimeMillis() - start;
        if (graphElapsed >= timeoutMs) {
            timedOut = true;
        }

        // 4. 评估套餐
        RiskLevel finalLevel = RiskLevel.PASS;
        List<String> matchedPolicies = new ArrayList<>();
        List<String> allPunishmentIds = new ArrayList<>();

        for (PolicyPackage policy : policies) {
            PolicyResult result = policy.evaluate(factorMap);
            if (result.isMatched()) {
                matchedPolicies.add(policy.policyId());
                finalLevel = finalLevel.max(result.getSuggestedLevel());
                allPunishmentIds.addAll(result.getPunishmentIds());
                log.info("[DefaultRiskEngine] Policy [{}] matched for userId={}, level={}",
                        policy.policyId(), context.getUserId(), result.getSuggestedLevel());
            }
        }

        // 5. 执行处罚
        List<PunishmentResult> punishmentResults = Collections.emptyList();
        if (!allPunishmentIds.isEmpty()) {
            PunishmentContext punishmentContext = PunishmentContext.builder()
                    .riskContext(context)
                    .level(finalLevel)
                    .build();
            punishmentResults = punishmentExecutor.execute(allPunishmentIds, punishmentContext);
        }

        long elapsed = System.currentTimeMillis() - start;

        return RiskResponse.builder()
                .level(finalLevel)
                .matchedPolicies(matchedPolicies)
                .punishments(punishmentResults)
                .factorValues(factorMap.toValueMap())
                .elapsedMs(elapsed)
                .timedOut(timedOut)
                .build();
    }
}
```

- [ ] **Step 4: 创建 REST 入口 Controller**

`antispam-starter/src/main/java/com/antispam/web/RiskRequest.java`:
```java
package com.antispam.web;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/** REST 接口入参，对应 RiskContext 字段 */
@Data
public class RiskRequest {
    private String businessType;
    private String userId;
    private String deviceId;
    private String ip;
    private String eventType;
    private Map<String, Object> attributes = new HashMap<>();
}
```

`antispam-starter/src/main/java/com/antispam/web/RiskEngineController.java`:
```java
package com.antispam.web;

import com.antispam.api.model.RiskContext;
import com.antispam.api.model.RiskResponse;
import com.antispam.api.spi.RiskEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 风控引擎 REST 入口。
 * POST /api/risk/evaluate
 */
@Slf4j
@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskEngineController {

    private final RiskEngine riskEngine;

    @PostMapping("/evaluate")
    public ResponseEntity<RiskResponse> evaluate(@RequestBody RiskRequest request) {
        RiskContext ctx = RiskContext.builder()
                .businessType(request.getBusinessType())
                .userId(request.getUserId())
                .deviceId(request.getDeviceId())
                .ip(request.getIp())
                .eventType(request.getEventType())
                .attributes(request.getAttributes())
                .timestamp(System.currentTimeMillis())
                .build();

        log.info("[RiskEngineController] Evaluating risk for userId={}, businessType={}",
                ctx.getUserId(), ctx.getBusinessType());

        RiskResponse response = riskEngine.evaluate(ctx);

        log.info("[RiskEngineController] Result: level={}, elapsedMs={}, timedOut={}",
                response.getLevel(), response.getElapsedMs(), response.isTimedOut());

        return ResponseEntity.ok(response);
    }
}
```

- [ ] **Step 5: 创建 AntispamApplication 主类**

`antispam-starter/src/main/java/com/antispam/AntispamApplication.java`:
```java
package com.antispam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AntispamApplication {
    public static void main(String[] args) {
        SpringApplication.run(AntispamApplication.class, args);
    }
}
```

- [ ] **Step 6: 创建 application.yml**

`antispam-starter/src/main/resources/application.yml`:
```yaml
server:
  port: 8080

spring:
  application:
    name: antispam-engine
  datasource:
    url: jdbc:mysql://localhost:3306/antispam?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: localhost
      port: 6379
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: 1
      retries: 3

antispam:
  engine:
    timeout-ms: 200
    thread-pool:
      core-size: 20
      max-size: 50
      queue-capacity: 1000

logging:
  level:
    com.antispam: INFO
```

- [ ] **Step 7: 写 Controller 单元测试（Mock RiskEngine）**

`antispam-starter/src/test/java/com/antispam/web/RiskEngineControllerTest.java`:
```java
package com.antispam.web;

import com.antispam.api.model.RiskLevel;
import com.antispam.api.model.RiskResponse;
import com.antispam.api.spi.RiskEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RiskEngineController.class)
class RiskEngineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RiskEngine riskEngine;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void evaluate_returnsPassResponse() throws Exception {
        when(riskEngine.evaluate(any())).thenReturn(
                RiskResponse.builder()
                        .level(RiskLevel.PASS)
                        .elapsedMs(15L)
                        .timedOut(false)
                        .matchedPolicies(Collections.emptyList())
                        .punishments(Collections.emptyList())
                        .factorValues(Collections.emptyMap())
                        .build()
        );

        RiskRequest request = new RiskRequest();
        request.setUserId("user1");
        request.setBusinessType("ECOMMERCE");
        request.setEventType("LOGIN");
        request.setDeviceId("dev1");
        request.setIp("1.2.3.4");

        mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("PASS"))
                .andExpect(jsonPath("$.timedOut").value(false));
    }

    @Test
    void evaluate_returnsBlockResponse() throws Exception {
        when(riskEngine.evaluate(any())).thenReturn(
                RiskResponse.builder()
                        .level(RiskLevel.BLOCK)
                        .elapsedMs(80L)
                        .timedOut(false)
                        .matchedPolicies(java.util.List.of("loginRiskPolicy"))
                        .punishments(Collections.emptyList())
                        .factorValues(Collections.emptyMap())
                        .build()
        );

        RiskRequest request = new RiskRequest();
        request.setUserId("bot_user");
        request.setBusinessType("ECOMMERCE");

        mockMvc.perform(post("/api/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("BLOCK"))
                .andExpect(jsonPath("$.matchedPolicies[0]").value("loginRiskPolicy"));
    }
}
```

- [ ] **Step 8: 运行 starter 测试**

```bash
cd /Users/xiaowenzhuo/Desktop/antispam
mvn test -pl antispam-starter -q
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 9: 完整构建验证**

```bash
cd /Users/xiaowenzhuo/Desktop/antispam
mvn package -DskipTests -q
```

Expected: BUILD SUCCESS，在 `antispam-starter/target/` 下生成可执行 JAR

- [ ] **Step 10: Commit**

```bash
git add .
git commit -m "feat(starter): wire up AutoConfiguration, REST controller, and complete DefaultRiskEngine"
```

---

## 验证总结

### 各模块测试矩阵

| 模块 | 测试类 | 覆盖的关键行为 |
|------|--------|--------------|
| antispam-api | FactorMapTest | put/get/fallback/toValueMap/contains |
| antispam-core | GraphExecutorTest | 并发/依赖/异常降级/超时/环检测 |
| antispam-infra | RedisKeyHelperTest | Key 格式规范 |
| antispam-factor | LoginFreqFactorTest, DeviceCountFactorTest | Redis mock 返回值 |
| antispam-policy | AviatorRuleEvaluatorTest, LoginRiskPolicyTest | 规则求值/PASS/REVIEW/BLOCK |
| antispam-punishment | CaptchaPunishmentTest, BanAccountPunishmentTest | Redis 写入 + Kafka 发送 |
| antispam-starter | RiskEngineControllerTest | REST 入口 + JSON 序列化 |

### 手动集成测试（需要 Redis + Kafka + MySQL）

```bash
# 1. 启动依赖（Docker）
docker run -d --name redis -p 6379:6379 redis:7
docker run -d --name kafka -p 9092:9092 \
  -e KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181 \
  bitnami/kafka:latest

# 2. 启动应用
mvn spring-boot:run -pl antispam-starter

# 3. 测试 PASS 场景（正常用户）
curl -X POST http://localhost:8080/api/risk/evaluate \
  -H "Content-Type: application/json" \
  -d '{"businessType":"ECOMMERCE","userId":"normal_user","deviceId":"dev1","ip":"1.2.3.4","eventType":"LOGIN"}'

# 期望响应：{"level":"PASS",...}

# 4. 测试 BLOCK 场景
# 先在 Redis 中模拟高频登录数据，再发请求观察是否返回 BLOCK
```
