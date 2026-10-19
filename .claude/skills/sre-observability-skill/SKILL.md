---
name: sre-observability-skill
description: "Review or improve SRE observability and service-level measurement for synchronous APIs and asynchronous journeys: SLIs/SLOs, metrics, logs, traces, dashboards, and actionable alerts. Use for reliability evidence and telemetry design, rather than unrelated application changes."
---

# SRE Observability

## Scope

Use this skill to connect user-visible reliability with telemetry and verifiable evidence. Keep the work within the requested review, implementation, or documentation scope. Read only the relevant sections of the [review reference](references/sre-observability-skill.md).

## Workflow

1. Establish the journey and scope of evidence. Inspect code, tests, telemetry configuration, and relevant documentation; distinguish proposed, implemented, locally tested, and remotely validated behavior.
2. For service-level work, define the user outcome, good/eligible events, identity, exclusions, time window, observation point, and missing-data policy before choosing queries or targets. Separate commercial KPIs, SLIs, SLOs, and external SLAs.
3. Map latency, traffic, errors, and saturation to the journey. For asynchronous work, distinguish acceptance from decision and include pending/overdue items independently of successful consumers.
4. Check metric types and aggregation, freshness, retention, privacy, and correlation across logs and trace boundaries. Verify the actual exported labels and buckets before writing queries.
5. Choose the smallest change that closes the material gap. Check whether load, downstream capacity, retries, and class allocation can meet proposed deadlines; do not present a target as a measured guarantee.
6. Validate the changed layer with appropriate focused checks. Report what changed, the evidence obtained, and remaining limitations; dashboard JSON or local propagation tests alone do not prove remote operation.

For detection-and-recovery demonstrations, read section 7 of the review reference. Check that alert timing is compatible with circuit transitions, retry exhaustion and redrive; define the preserved state, abort conditions and rollback before designing the exercise. A planning request does not authorize running it.

## Rules

- A successful acceptance response is not proof of terminal business success. Retries, canonical operations, and financial effects have different denominators.
- No demand and incomplete collection are distinct from success. Avoid filling an undefined SLI with a healthy number or color.
- Keep experiment snapshots separate from continuous operational SLOs. Do not apply counter functions to snapshot gauges or use sampled traces as an exhaustive denominator.
- Use stable, bounded metric dimensions. Do not add personal data, credentials, payloads, fingerprints, or per-request/run identifiers as labels. Authorized aggregate business values can be metric values without exposing individual records.
- Reuse framework instrumentation; add manual spans only for uncovered boundaries. Keep business identity independent of trace identity.
- Preserve the user's local/remote boundary. Preparing files does not imply publishing, starting infrastructure, sending alerts, or clearing history; use the authorization already present for actual external actions.

## Specialist guidance

When the task requires framework-specific detail, consult the available specialist skill: [metrics](../spring-metrics-skill/SKILL.md), [logging](../spring-logging-skill/SKILL.md), [tracing](../spring-tracing-skill/SKILL.md), [resilience](../resilience-checker-skill/SKILL.md), or [privacy](../lgpd-sre-compliance-skill/SKILL.md). These are supporting instructions, not a requirement to spawn agents or load every skill.
