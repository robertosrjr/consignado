---
name: lgpd-sre-compliance-skill
description: "Use when auditing or implementing LGPD privacy controls for Java/Spring applications, including telemetry minimization, PII redaction, retention, backups, access control, and least privilege."
---

# LGPD SRE Compliance

## Scope

Use this skill for privacy-by-design review of code, telemetry, deployment, backups, and operational access. It is an engineering control guide, not legal advice.

## Workflow

1. Search for PII fields, secrets, tokens, credentials, raw payloads, and identifiers in logs, traces, metrics, backups, and test data.
2. Minimize collection at the source; do not rely on downstream redaction alone.
3. Define masking or pseudonymization only when the diagnostic value justifies retaining a derived value.
4. Review retention, access, auditability, incident response, and environment separation.
5. Validate with static search, focused tests, and a telemetry field inventory.

## Rules

- Never log request/response bodies or financial payloads by default.
- Never use PII or high-cardinality identifiers as metric tags.
- Treat technical IDs as potentially personal data when linkable to a person.
- Require least privilege and auditable break-glass access in operations.
- Coordinate implementation with logging, metrics, tracing, and resilience specialist skills.

## Reference

See `references/lgpd-sre-compliance-skill.md` for the detailed audit workflow and examples.
