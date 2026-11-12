package com.ansu.seed;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.ansu.common.TimeUtil;

@Component
@Order(10)
public class BaselineSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BaselineSeeder.class);
    private final JdbcTemplate jdbc;

    public BaselineSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM load_record WHERE source = 'seed'", Long.class);
        if (existing != null && existing > 0) {
            return;
        }

        List<Long> dormIds = jdbc.query("SELECT id FROM dorm ORDER BY id",
                (rs, rowNum) -> rs.getLong(1));
        if (dormIds.isEmpty()) {
            return;
        }

        LocalDateTime end = LocalDateTime.now(TimeUtil.ZONE).withSecond(0).withNano(0);
        LocalDateTime start = end.minusHours(6);
        List<Object[]> batch = new ArrayList<>();
        for (long dormId : dormIds) {
            Random random = new Random(20261110L + dormId);
            int baseW = dormId == 314 ? 32 : 40 + random.nextInt(80);
            for (int i = 0; i < 360; i++) {
                LocalDateTime ts = start.plusMinutes(i);
                double jitter = dormId == 314 ? 0 : random.nextInt(8) - 4;
                batch.add(new Object[]{
                        dormId, ts, (baseW + jitter) / 1000.0, 0.98, 5, 28.0
                });
            }
        }
        jdbc.batchUpdate("""
                INSERT IGNORE INTO load_record
                (dorm_id, ts, p_active_kw, pf, leakage_ma, temp_c, source)
                VALUES (?, ?, ?, ?, ?, ?, 'seed')
                """, batch);
        log.info("baseline seeded: {} load rows", batch.size());
    }
}
