package com.antispam.api.spi;

import com.antispam.api.model.PunishmentContext;
import com.antispam.api.model.PunishmentResult;
import com.antispam.api.model.PunishmentType;

/**
 * 处罚 SPI。实现具体的处罚执行逻辑。
 *
 * <p>实现类需注册为 Spring Bean（@Component）以被 PunishmentRegistry 自动发现。
 *
 * <p>契约要求：
 * <ul>
 *   <li>INTERNAL 类型：直接执行并返回结果（可同步或内部异步，不阻塞调用方超过合理时间）</li>
 *   <li>WEBHOOK 类型：将事件推入 Kafka 后立即返回（真正的 HTTP 调用由下游消费者完成）</li>
 *   <li>所有实现均不得向外抛出未检查异常，所有异常需捕获并返回 {@link PunishmentResult#failure}</li>
 * </ul>
 */
public interface Punishment {

    /**
     * 处罚唯一 ID，例如 "captcha"、"banAccount"、"rateLimit"。
     * PolicyPackage 通过此 ID 引用要执行的处罚。
     */
    String punishmentId();

    /** 处罚类型：INTERNAL（引擎内部执行）或 WEBHOOK（通知外部系统）*/
    PunishmentType type();

    /**
     * 执行处罚。
     *
     * @param ctx 处罚上下文（含原始 RiskContext、风险级别、处罚配置参数）
     * @return 执行结果，不得返回 null
     */
    PunishmentResult execute(PunishmentContext ctx);
}
