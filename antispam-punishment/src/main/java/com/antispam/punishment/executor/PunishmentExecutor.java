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
