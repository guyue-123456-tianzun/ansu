package com.ansu.safety;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.stereotype.Service;

@Service
public class ThresholdService {

    private final Map<String, Object> config;
    private final String yamlText;

    public ThresholdService() {
        try {
            ClassPathResource resource = new ClassPathResource("thresholds.yaml");
            this.yamlText = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("thresholds", resource);
            Map<String, Object> merged = new LinkedHashMap<>();
            for (PropertySource<?> source : sources) {
                if (source instanceof EnumerablePropertySource<?> enumerable) {
                    for (String name : enumerable.getPropertyNames()) {
                        merged.put(name, enumerable.getProperty(name));
                    }
                }
            }
            if (merged.isEmpty()) {
                throw new IllegalStateException("thresholds.yaml 为空或加载失败");
            }
            this.config = Collections.unmodifiableMap(merged);
        } catch (IOException e) {
            throw new IllegalStateException("无法加载 thresholds.yaml", e);
        }
    }

    public String version() {
        Object value = config.get("version");
        return value == null ? "unknown" : String.valueOf(value);
    }

    public Map<String, Object> asMap() {
        return config;
    }

    public String yamlText() {
        return yamlText;
    }
}
