# Antispam Risk Engine (实时风控引擎框架)

[![Java CI with Maven](https://github.com/your-username/antispam/actions/workflows/maven.yml/badge.svg)](https://github.com/your-username/antispam/actions/workflows/maven.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Antispam 是一个基于事件驱动 DAG (有向无环图) 并行计算架构的高性能实时风控引擎框架。它旨在解决互联网高并发场景下的账户登录、注册、支付等关键业务场景的防刷、防撞库以及欺诈行为识别需求。

---

## 核心技术特点

1. **并行 DAG 调度引擎 (`GraphExecutor`)**
   * 基于 Kahn 算法在运行期对关联因子进行拓扑排序与依赖性环路检测。
   * 利用 Java `CompletableFuture` 实现多线程无阻塞并行执行，大幅降低复杂指标计算的整体响应延迟。
   * 支持针对单个节点配置超时时间与兜底降级方案，即便部分因子异常，风控引擎仍能保证高可用响应。

2. **多维度规则评估与决策**
   * 使用高效的 Aviator 规则执行器，支持嵌套布尔逻辑表达式的动态求值与规则编译缓存。
   * 支持通过规则套餐匹配对不同业务种类（如 `ECOMMERCE`, `FINANCE` 等）设置相互隔离的处置机制。

3. **双重处置触发 (验证与阻断)**
   * 实现不同强度的处置手段：软拦截（弹验证码 `captcha` 并记录状态至 Redis）和硬拦截（冻结账号 `banAccount` 并向 Kafka 广播推送阻断事件）。

4. **开箱即用的基础设施对接**
   * 基于 Redis ZSet 提供滑动窗口频率计数。
   * 基于 Redis Set 提供去重设备数计算。
   * 提供基于 Kafka 的风控处罚和审计日志双流输出。

---

## 目录分层结构

整个项目采用 Maven 多模块架构，业务逻辑分层清晰：

```text
├── antispam-api         # 风控模型及 SPI 核心定义 (无外部依赖，便于二次扩展)
├── antispam-infra       # 基础设施层：Redis 计数器、Kafka 事件总线
├── antispam-core        # 核心逻辑层：DAG 拓扑执行器、DefaultRiskEngine 引擎编排
├── antispam-factor      # 因子实现库：登录频次、去重设备数等因子
├── antispam-policy      # 策略与规则引擎：Aviator 表达式编译及规则套餐
├── antispam-punishment  # 处罚执行器：验证码标记、黑名单封号
├── antispam-starter     # 启动引导与 REST Controller
```

---

## 快速开始

### 方式 A：本地直接运行 (推荐用于开发调试)

1. **启动本地依赖环境 (Redis / Kafka / MySQL)**
   在项目根目录下通过 Compose 仅拉起数据库与中间件依赖：
   ```bash
   docker compose up -d
   ```

2. **启动风控微服务**
   ```bash
   mvn spring-boot:run -pl antispam-starter
   ```

### 方式 B：容器化一键部署 (支持全服务隔离)

如果您本地未安装 Java/Maven，可以通过以下命令直接基于 Docker 内部多阶段编译并联同中间件一键运行：
```bash
docker compose -f docker-compose-app.yml up -d --build
```
服务成功拉起后，会在 `8080` 端口对外暴露 REST 风控接口。

---

## API 调用与测试样例

服务提供了统一的 POST 风控评估端点：
`POST http://localhost:8080/api/risk/evaluate`

### 1. PASS（正常用户登录）
*   **请求（JSON）**：
    ```json
    {
      "businessType": "ECOMMERCE",
      "userId": "user_normal",
      "deviceId": "device_1",
      "ip": "127.0.0.1",
      "eventType": "LOGIN"
    }
    ```
*   **模拟因子计算数据**：最近 1 分钟登录次数 = 2，最近 24 小时设备数 = 1。
*   **响应**：
    ```json
    {
      "level": "PASS",
      "matchedPolicies": [],
      "punishments": [],
      "factorValues": {
        "loginFreq1Min": 2,
        "deviceCount24h": 1
      },
      "elapsedMs": 2,
      "timedOut": false
    }
    ```

### 2. REVIEW（高频异常，弹验证码）
*   **请求（JSON）**：
    ```json
    {
      "businessType": "ECOMMERCE",
      "userId": "user_suspicious",
      "deviceId": "device_multiple",
      "ip": "127.0.0.1",
      "eventType": "LOGIN"
    }
    ```
*   **模拟因子计算数据**：最近 1 分钟登录次数 = 6 (>5)，最近 24 小时设备数 = 4 (>3)。
*   **响应**（命中策略：弹出验证码拦截）：
    ```json
    {
      "level": "REVIEW",
      "matchedPolicies": [
        "loginRiskPolicy"
      ],
      "punishments": [
        {
          "punishmentId": "captcha",
          "executed": true,
          "message": "executed successfully"
        }
      ],
      "factorValues": {
        "loginFreq1Min": 6,
        "deviceCount24h": 4
      },
      "elapsedMs": 3,
      "timedOut": false
    }
    ```

### 3. BLOCK（极高频黑客攻击，直接封号）
*   **请求（JSON）**：
    ```json
    {
      "businessType": "ECOMMERCE",
      "userId": "user_hacker",
      "deviceId": "device_hack",
      "ip": "8.8.8.8",
      "eventType": "LOGIN"
    }
    ```
*   **模拟因子计算数据**：最近 1 分钟登录次数 = 12 (>10)。
*   **响应**（账号自动封锁，写入黑名单，并广播通知 Kafka 消费方）：
    ```json
    {
      "level": "BLOCK",
      "matchedPolicies": [
        "loginRiskPolicy"
      ],
      "punishments": [
        {
          "punishmentId": "banAccount",
          "executed": true,
          "message": "executed successfully"
        }
      ],
      "factorValues": {
        "loginFreq1Min": 12,
        "deviceCount24h": 2
      },
      "elapsedMs": 2,
      "timedOut": false
    }
    ```

---

## 运行单元测试

项目包含覆盖各类边缘情况（并发、环路依赖检测、网络异常降级等）的单元测试与集成测试，运行命令：
```bash
mvn clean test
```

---

## 开源许可

本项目根据 [Apache 2.0 许可证](LICENSE) 授权许可。
