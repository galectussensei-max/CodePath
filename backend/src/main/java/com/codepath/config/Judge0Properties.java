package com.codepath.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code codepath.judge0}. Only {@code base-url} existed before Phase 3; the rest have
 * defaults that suit a local Judge0 CE 1.13.1 started with {@code docker compose up -d}.
 *
 * @param baseUrl                Judge0 server URL
 * @param javaLanguageId         Judge0 language id for Java (62 = Java OpenJDK 13.0.1 in CE 1.13.1)
 * @param javaStartupAllowanceMs extra CPU time added to a question's limit because JVM start-up and JIT
 *                               count against the limit; it keeps correct solutions from timing out on a cold JVM
 * @param pollIntervalMs         delay between result polls
 * @param pollTimeoutMs          give up waiting for a verdict after this long
 * @param connectTimeoutMs       HTTP connect timeout
 * @param readTimeoutMs          HTTP read timeout
 */
@ConfigurationProperties(prefix = "codepath.judge0")
public record Judge0Properties(
        @DefaultValue("http://localhost:2358") String baseUrl,
        @DefaultValue("62") int javaLanguageId,
        @DefaultValue("1000") int javaStartupAllowanceMs,
        @DefaultValue("250") long pollIntervalMs,
        @DefaultValue("30000") long pollTimeoutMs,
        @DefaultValue("3000") int connectTimeoutMs,
        @DefaultValue("15000") int readTimeoutMs) {
}
