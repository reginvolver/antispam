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
    private Map<String, Punishment> punishmentMap = Collections.emptyMap();

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
