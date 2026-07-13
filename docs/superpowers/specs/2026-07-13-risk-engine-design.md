# 风控引擎框架设计文档

**日期**: 2026-07-13  
**作者**: Antigravity  
**状态**: 已批准，待实现

---

## 1. 背景与目标

本项目旨在构建一个**通用实时风控引擎框架**，支持多业务线复用（电商反欺诈、金融风控、内容安全、游戏反作弊等）。

核心目标：
- 毫秒级实时决策（登录/支付等关键路径拦截）
- 高度可扩展的因子、套餐、处罚插件体系
- 基于事件驱动 DAG 的响应式图并行执行（RAG = Reactive Async Graph）
- 返回统一的 `RiskLevel`（PASS / REVIEW / BLOCK）

---

## 2. 整体架构

### 2.1 请求处理全链路

```
调用方（业务服务）
    │  POST /api/risk/evaluate
    │  { RiskRequest }
    ▼
RiskEngine.evaluate(RiskContext)
    │
    ├─ [1] 加载套餐(PolicyPackage) — 按 businessType 路由
    │
    ├─ [2] 构建 DAG 执行图
    │       节点类型：FactorNode / RuleNode
    │       边：依赖关系（上游完成后自动触发下游）
    │
    ├─ [3] GraphExecutor 拓扑并发执行
    │       入度为 0 的节点立即并发提交到 ThreadPoolExecutor
    │       每个节点完成后 → 触发下游入度递减 → 入度变0则提交
    │       全局 timeout-ms 配置，超时后立即用当前结果降级返回
    │
    ├─ [4] Aviator 规则求值
    │       基于已计算的 FactorMap 对套餐中的规则表达式求值
    │
    ├─ [5] 套餐命中 → PunishmentExecutor.execute()
    │       内部处罚：同步或异步执行（BanAccount / RateLimit / Captcha）
    │       外部处罚：Webhook 异步回调
    │       Kafka：推送处罚事件 + 审计日志
    │
    └─ [6] 返回 RiskResponse
            { level, matchedPolicies, appliedPunishments, factorValues, elapsed }
```

### 2.2 技术栈

| 组件 | 选型 |
|------|------|
| Web 框架 | Spring Boot 3.x + Spring MVC |
| 并发模型 | `CompletableFuture` + 自定义 `ThreadPoolExecutor` |
| 规则引擎 | Aviator Expression Engine |
| 因子存储 | Redis 集群（滑动窗口计数、黑名单、白名单） |
| 配置持久化 | MySQL（套餐、处罚、因子配置） |
| 消息中间件 | Kafka（处罚事件 + 审计日志异步写入） |
| 构建工具 | Maven 多模块 |
| 超时/降级 | 全局 `timeout-ms` 配置，超时后降级返回已有结果 |

---

## 3. 模块划分（Maven 多模块）

```
antispam/
├── antispam-api          # 对外接口定义（无 Spring 依赖）
│   ├── RiskRequest
│   ├── RiskContext
│   ├── RiskResponse
│   ├── RiskLevel (PASS/REVIEW/BLOCK)
│   ├── Factor (SPI 接口)
│   ├── Punishment (SPI 接口)
│   └── PolicyPackage (SPI 接口)
│
├── antispam-core         # 核心引擎实现
│   ├── RiskEngine
│   ├── GraphExecutor      (DAG 拓扑执行调度器)
│   ├── GraphNode          (FactorNode / RuleNode)
│   ├── FactorMap          (因子值聚合)
│   └── TimeoutPolicy      (全局超时 + 降级)
│
├── antispam-factor       # 因子 SPI 实现
│   ├── RedisWindowCounter (滑动窗口计数基础设施)
│   ├── LoginFreqFactor    (示例: 1分钟内登录频次)
│   ├── DeviceCountFactor  (示例: 24小时内设备数)
│   └── IpRiskFactor       (示例: IP 风险评分查询)
│
├── antispam-policy       # 套餐 + Aviator 规则
│   ├── PolicyRegistry     (套餐注册中心)
│   ├── AviatorRuleEvaluator
│   ├── PolicyLoader       (从 MySQL/配置中心加载)
│   └── LoginRiskPolicy    (示例: 登录风险套餐)
│
├── antispam-punishment   # 处罚 SPI 实现
│   ├── PunishmentExecutor
│   ├── PunishmentRegistry
│   ├── BanAccountPunishment   (内部执行 - 封号)
│   ├── RateLimitPunishment    (内部执行 - 限流)
│   ├── CaptchaPunishment      (内部执行 - 弹验证码)
│   └── WebhookPunishment      (外部回调)
│
├── antispam-infra        # 基础设施封装
│   ├── RedisClient        (Lettuce/Redisson 封装)
│   ├── KafkaProducer      (处罚事件/审计日志)
│   └── MetricsCollector   (Micrometer 指标)
│
└── antispam-starter      # Spring Boot AutoConfiguration
    ├── RiskEngineAutoConfiguration
    ├── RiskEngineProperties (yml 配置绑定)
    └── RiskEngineController (REST 入口)
```

---

## 4. 核心接口定义

### 4.1 对外 API

```java
// 入口
public interface RiskEngine {
    RiskResponse evaluate(RiskContext context);
}

// 请求上下文
public class RiskContext {
    private String businessType;   // 业务种类，用于路由套餐
    private String userId;
    private String deviceId;
    private String ip;
    private String eventType;      // 事件类型（LOGIN / PAY / REGISTER ...）
    private Map<String, Object> attributes; // 扩展属性
    private long timestamp;
}

// 响应
public class RiskResponse {
    private RiskLevel level;                       // PASS / REVIEW / BLOCK
    private List<String> matchedPolicies;          // 命中的套餐名称
    private List<PunishmentResult> punishments;    // 已执行/触发的处罚
    private Map<String, Object> factorValues;      // 因子计算结果（调试用）
    private long elapsedMs;                        // 耗时
    private boolean timedOut;                      // 是否触发了降级
}

// 风险级别
public enum RiskLevel { PASS, REVIEW, BLOCK }
```

### 4.2 因子 SPI

```java
public interface Factor {
    String factorId();                      // 唯一标识
    List<String> dependencies();            // 依赖的其他 factorId（构成 DAG 边）
    FactorResult compute(RiskContext ctx, FactorMap upstream);
}

public class FactorResult {
    private Object value;          // 计算结果（数字/布尔/字符串）
    private boolean success;       // 是否成功计算
    private Object fallbackValue;  // 失败时的 fallback 值
}
```

### 4.3 套餐 SPI

```java
public interface PolicyPackage {
    String policyId();
    String businessType();          // 绑定的业务种类
    List<String> requiredFactors(); // 需要计算的因子列表
    PolicyResult evaluate(FactorMap facts);
}

public class PolicyResult {
    private boolean matched;
    private RiskLevel suggestedLevel;
    private List<String> punishmentIds; // 命中后需要执行的处罚 ID 列表
}
```

### 4.4 处罚 SPI

```java
public interface Punishment {
    String punishmentId();
    PunishmentType type();           // INTERNAL / WEBHOOK
    PunishmentResult execute(PunishmentContext ctx);
}

public enum PunishmentType { INTERNAL, WEBHOOK }

public class PunishmentContext {
    private RiskContext riskContext;
    private RiskLevel level;
    private Map<String, Object> config; // 处罚参数（webhook url / ban duration 等）
}
```

---

## 5. DAG 图执行器设计（GraphExecutor）

### 5.1 执行流程

```
1. buildGraph(requiredFactors)
   → 对每个 Factor 的 dependencies() 建有向边
   → 计算每个节点的入度 in-degree

2. 初始化：将所有 in-degree == 0 的节点提交到 ThreadPool

3. 每个节点完成后（onComplete callback）：
   → 将结果写入 FactorMap（线程安全 ConcurrentHashMap）
   → 遍历所有以该节点为上游的下游节点
   → 下游入度 -1（AtomicInteger）；若入度 == 0 则立即提交执行

4. 全局超时（CompletableFuture.allOf(...).get(timeoutMs, MILLISECONDS)）
   → 超时后取消未完成节点
   → 使用已有 FactorResult 进行规则求值（timedOut = true）

5. 异常处理：
   → 单个节点异常 → 使用该 Factor 的 fallbackValue，继续执行下游
   → 不抛出异常，不阻断整体流程
```

### 5.2 并发配置

```yaml
antispam:
  engine:
    timeout-ms: 200          # 全局图执行超时
    thread-pool:
      core-size: 20
      max-size: 50
      queue-capacity: 1000
```

---

## 6. Aviator 规则示例

套餐中的每条规则是一个 Aviator 表达式字符串，因子值通过 FactorMap 注入为变量：

```java
// 示例套餐规则（登录风险）
"loginFreq1Min > 5 && deviceCount24h > 3"   // REVIEW
"ipRiskScore >= 80"                           // REVIEW
"loginFreq1Min > 10"                          // BLOCK（高频直接拦截）
```

规则与 RiskLevel、处罚的映射存储在 MySQL 中，PolicyLoader 在启动时加载并缓存。

---

## 7. 处罚执行设计

### 7.1 执行矩阵

| 处罚类型 | 实现类 | 执行方式 | 是否同步 | 触发级别 |
|---------|-------|---------|---------|---------|
| 验证码拦截 | CaptchaPunishment | 写 Redis 标记 | 同步 | REVIEW |
| 限速限流 | RateLimitPunishment | 更新 Redis 计数器 | 同步 | REVIEW |
| 封禁账号 | BanAccountPunishment | 写 Redis 黑名单 | 异步 | BLOCK |
| 外部回调 | WebhookPunishment | Kafka → 消费者发 HTTP | 异步 | 可配置 |

### 7.2 Kafka Topic 设计

| Topic | 用途 | Key | 分区策略 |
|-------|------|-----|---------|
| `antispam.punishment.events` | 所有处罚执行事件 | userId | 按 userId 分区 |
| `antispam.audit.logs` | 每次风控决策审计日志 | requestId | 随机 |

---

## 8. 示例实现清单

### 8.1 示例因子（2 个）
- **`LoginFreqFactor`**：查询 Redis 滑动窗口（ZSet + ZCOUNT），统计 1 分钟内 userId 的登录次数；无上游依赖
- **`DeviceCountFactor`**：统计 24 小时内该 userId 使用过的不同 deviceId 数量（Redis Set）；无上游依赖

### 8.2 示例套餐（1 个）
- **`LoginRiskPolicy`**：绑定 `businessType=ECOMMERCE`、`eventType=LOGIN`，`requiredFactors=[loginFreq1Min, deviceCount24h]`；Aviator 规则映射三个级别的表达式

### 8.3 示例处罚（2 个）
- **`CaptchaPunishment`**：写入 Redis `captcha:{userId}` key，TTL 5 分钟（REVIEW 触发）
- **`BanAccountPunishment`**：写入 Redis `ban:{userId}` key + 推 Kafka 审计日志（BLOCK 触发）

---

## 9. 非功能性需求

| 需求 | 设计决策 |
|------|---------|
| 延迟目标 | P99 < 200ms，全局 timeout-ms 兜底降级 |
| 高可用降级 | 超时返回已有结果，timedOut=true 标记；因子失败用 fallback |
| 可观测性 | Micrometer 指标（因子耗时 histogram、规则命中率 counter、处罚执行 counter）|
| 扩展性 | Factor / Punishment / PolicyPackage 均为 SPI，@Component 自动注册到各自 Registry |
| 测试友好 | 每个模块可独立单测，无需启动完整 Spring 上下文 |
| 无认证 | 第一期不需要 API 鉴权，最小化依赖 |

---

## 10. 验证计划

### 自动化测试
```bash
# 单元测试
mvn test -pl antispam-core          # GraphExecutor 拓扑执行、超时降级
mvn test -pl antispam-factor        # 因子计算逻辑
mvn test -pl antispam-policy        # Aviator 规则求值
mvn test -pl antispam-punishment    # 处罚执行路径

# 集成测试
mvn test -pl antispam-starter       # 端到端 REST API 测试
```

### 手动验证
1. 启动 Spring Boot 应用（需 Redis + Kafka + MySQL）
2. `POST /api/risk/evaluate` 构造登录场景请求
3. 验证 PASS / REVIEW / BLOCK 三条路径响应正确
4. 验证 Redis 中 captcha / ban key 被正确写入
5. 验证 Kafka Topic 中审计日志消息正确推送

---

*本文档由 brainstorming 阶段产出，已经用户确认，将进入 writing-plans 实现计划阶段。*
