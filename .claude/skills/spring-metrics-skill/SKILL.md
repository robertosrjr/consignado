---
name: spring-metrics-skill
description: "Use when designing or implementing Micrometer metrics in Java/Spring, including Counters, Gauges, Timers, Actuator exposure, JVM metrics, business metrics, or cardinality controls. Do not use for log format or trace propagation design."
---

# Spring Metrics

## Scope

Use this skill for Micrometer and Spring Boot Actuator instrumentation. Define metric names, types, descriptions, tags, and alerting value before editing code.

## Workflow

1. Inventory existing registries, Actuator endpoints, meters, and exported backends.
2. Map the signal: traffic, latency, errors, or saturation.
3. Choose `Counter` for events, `Gauge` for current state, and `Timer` for duration.
4. Use stable names and low-cardinality tags; never tag by user, UUID, email, raw query, or payload.
5. Register meters through the injected `MeterRegistry`.
6. Validate with compilation, focused tests, and meter inspection where available.

## Rules

- Avoid duplicate custom meters for signals already provided by Spring Boot.
- Document whether a gauge is process-local, in-memory, or backed by durable storage.
- Do not create a metric solely because a field exists; it must support diagnosis or an SLO.
- Defer logs to `spring-logging-skill` and trace propagation to `spring-tracing-skill`.

## Reference

See `references/spring-metrics-skill.md` for extended Micrometer examples.
