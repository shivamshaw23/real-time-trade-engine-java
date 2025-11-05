package com.tradeengine.config;

import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Spring Boot Actuator endpoints
 * Prometheus metrics endpoint will be available at /actuator/prometheus
 */
@Configuration
public class ActuatorConfig {
    // PrometheusMetricsExportAutoConfiguration is automatically enabled
    // when spring-boot-starter-actuator and micrometer-registry-prometheus are on classpath
}

