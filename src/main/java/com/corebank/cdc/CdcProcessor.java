package com.corebank.cdc;

import com.corebank.account.BalanceCacheEntry;
import com.corebank.account.BalanceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CdcProcessor {
    private static final Logger log = LoggerFactory.getLogger(CdcProcessor.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final BalanceService balanceService;

    @Value("${corebank.cdc.enabled:true}") private boolean enabled;
    @Value("${corebank.cdc.stream:corebank.public.account}") private String stream;
    @Value("${corebank.cdc.group:corebank-balance-cache}") private String group;
    @Value("${corebank.cdc.consumer:corebank-1}") private String consumer;
    @Value("${corebank.cdc.batch-size:100}") private int batchSize;

    @PostConstruct
    void initGroup() {
        if (!enabled) {
            log.info("CDC processor disabled");
            return;
        }
        log.info("Initializing CDC Redis stream group stream={} group={} consumer={}", stream, group, consumer);
        try {
            redis.execute((RedisConnection connection) -> {
                connection.execute("XGROUP", bytes("CREATE"), bytes(stream), bytes(group), bytes("$") , bytes("MKSTREAM"));
                return null;
            });
        } catch (Exception e) {
            log.debug("CDC Redis consumer group already exists or could not be created stream={} group={}", stream, group);
        }
    }

    @Scheduled(fixedDelayString = "${corebank.cdc.block-ms:2000}")
    void consume() {
        if (!enabled) return;
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                Consumer.from(group, consumer),
                StreamReadOptions.empty().count(batchSize).block(java.time.Duration.ofMillis(1500)),
                StreamOffset.create(stream, ReadOffset.lastConsumed()));

        if (records == null) return;
        for (MapRecord<String, Object, Object> record : records) {
            try {
                log.debug("CDC event received stream={} recordId={}", stream, record.getId());
                process(record.getValue());
                redis.opsForStream().acknowledge(stream, group, record.getId());
            } catch (Exception e) {
                log.error("CDC processing failed stream={} recordId={}; leaving record unacknowledged for retry",
                        stream, record.getId(), e);
            }
        }
    }

    private void process(Map<Object, Object> value) throws Exception {
        log.debug("CDC raw Redis record={}", value);

        /*
         * Debezium Redis sink with compact message format stores the
         * record as a single Redis Stream field. The payload JSON can
         * therefore be the value of the first field instead of a field
         * literally named "value".
         */
        Object raw = value.get("id");

        if (raw == null && !value.isEmpty()) {
            raw = value.values().iterator().next();
            log.debug("CDC payload found in first Redis Stream field");
        }

        if (raw == null) {
            log.warn("CDC record does not contain a payload. Available fields={}", value.keySet());
            return;
        }

        log.debug("CDC raw value type={} value={}",
                raw.getClass().getName(),
                raw);

        JsonNode root = objectMapper.readTree(raw.toString());

        log.debug("CDC parsed JSON={}", root);

        if (root.isNull()) {
            log.warn("CDC parsed JSON is null");
            return;
        }

        String op = root.path("__op").asText(root.path("op").asText("u"));
        JsonNode after = root.path("after");

        if (after.isMissingNode()) {
            after = root;
        }

        log.debug("CDC account event operation={} accountId={}",
                op,
                idForLog(after, root));

        if ("d".equals(op)) {
            JsonNode id = root.path("id");

            if (!id.isMissingNode()) {
                log.info("CDC deleting balance cache accountId={}", id.asText());
                redis.delete("balance:" + id.asText());
            } else {
                log.warn("CDC delete event received without account id");
            }

            return;
        }

        JsonNode id = after.path("id");
        JsonNode balance = after.path("balance");
        JsonNode version = after.path("version");

        if (id.isMissingNode() || balance.isMissingNode() || version.isMissingNode()) {
            log.warn("CDC account event missing required fields. idPresent={} balancePresent={} versionPresent={} payload={}",
                    !id.isMissingNode(),
                    !balance.isMissingNode(),
                    !version.isMissingNode(),
                    after);
            return;
        }

        UUID accountId = UUID.fromString(id.asText());
        BigDecimal amount = new BigDecimal(balance.asText());
        long ver = version.asLong();

        BalanceCacheEntry entry =
                new BalanceCacheEntry(accountId, amount, ver, Instant.now());

        log.debug("CDC updating balance cache accountId={} version={} balance={}",
                accountId, ver, amount);

        balanceService.putCache(entry);

        log.debug("CDC balance cache updated successfully accountId={} version={}",
                accountId, ver);
    }

    private String idForLog(JsonNode after, JsonNode root) {
        JsonNode id = after.path("id");
        if (!id.isMissingNode()) return id.asText();
        id = root.path("id");
        return id.isMissingNode() ? "unknown" : id.asText();
    }

    private byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
