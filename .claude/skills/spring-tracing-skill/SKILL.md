---
name: spring-tracing-skill
description: "Use when implementing or reviewing Micrometer Tracing, OpenTelemetry, OTLP export, W3C trace context, Observation spans, HTTP client propagation, or trace-log correlation in Java/Spring."
---

# Spring Tracing

## Scope

Use this skill for distributed tracing and operation spans. Prefer Micrometer Observation and the OpenTelemetry bridge managed by Spring Boot.

## Workflow

1. Inspect tracing dependencies, sampling, exporters, propagation, and MDC correlation.
2. Identify inbound request boundaries, outbound HTTP/messaging boundaries, and critical business operations.
3. Use stable span names and low-cardinality attributes.
4. Propagate W3C context through Spring-managed HTTP client builders; never instantiate uninstrumented clients when propagation is required.
5. Exclude payloads, credentials, financial values, and personal data from span attributes.
6. Validate compilation and focused behavior tests without requiring a live collector.

## Rules

- Make sampling configurable by environment.
- Treat the OTLP collector as optional for local tests.
- Do not duplicate HTTP server instrumentation already supplied by Spring Boot.
- Coordinate log fields with `spring-logging-skill` and privacy review with `lgpd-sre-compliance-skill`.

## Reference

See `references/spring-tracing-skill.md` for extended Observation and OTLP examples.
