package com.ansu.safety;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ThresholdVersionInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ThresholdVersionInitializer.class);

    private final JdbcTemplate jdbc;
    private final ThresholdService thresholds;

    public ThresholdVersionInitializer(JdbcTemplate jdbc, ThresholdService thresholds) {
        this.jdbc = jdbc;
        this.thresholds = thresholds;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String version = thresholds.version();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM threshold_version WHERE version = ?", Integer.class, version);
        if (count != null && count > 0) {
            log.info("threshold version already present: {}", version);
            return;
        }

        String checksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(thresholds.yamlText().getBytes(StandardCharsets.UTF_8)));

        jdbc.update("UPDATE threshold_version SET active = 0 WHERE active = 1");
        jdbc.update("INSERT INTO threshold_version (version, yaml_text, checksum, active) VALUES (?, ?, ?, 1)",
                version, thresholds.yamlText(), checksum);
        log.info("threshold version activated: {} checksum={}", version, checksum);
    }
}
