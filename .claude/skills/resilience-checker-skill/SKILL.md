---
name: resilience-checker-skill
description: "Use when reviewing or implementing resilience in Java/Spring, including idempotency, retries, timeouts, circuit breakers, rate limiting, bulkheads, fallbacks, caching, or failure handling."
---

# Resilience Checker

## Scope

Use this skill to identify failure boundaries and apply only the resilience patterns justified by real dependencies and workload.

## Workflow

1. Map external services, databases, queues, shared resources, and process-local state.
2. Classify failures as transient, permanent, overload, timeout, or duplicate execution.
3. Apply retry only to safe idempotent operations, with explicit exception lists, exponential backoff, and jitter.
4. Use circuit breakers, timeouts, bulkheads, and rate limits at the dependency boundary they protect.
5. Define fallback semantics and observability before implementation.
6. Add focused failure, concurrency, replay, and load tests.

## Rules

- Never add blind retry to financial writes.
- Do not add Circuit Breaker without a dependency that can fail independently.
- Bound local state and queues.
- Document whether protection is process-local or distributed.
- Coordinate metrics/logs with the observability skills and privacy with `lgpd-sre-compliance-skill`.

## Reference

See `references/resilience-checker-skill.md` for pattern details and chaos-validation guidance.
