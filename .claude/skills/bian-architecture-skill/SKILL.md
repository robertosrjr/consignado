---
name: bian-architecture-skill
description: "Use when modeling, designing, or reviewing banking and financial systems against BIAN (Banking Industry Architecture Network) v14: mapping capabilities to the Service Landscape (Business Area, Business Domain, Service Domain), defining Control Records, Behavior Qualifiers, Service Operations and Action Terms, aligning APIs with BIAN Semantic APIs (OpenAPI/AsyncAPI, BOM, ISO 20022), turning Service Domains into bounded contexts, or checking BIAN conformance of a financial project."
---

# BIAN Architecture

## Scope

Use this skill to align financial projects with the BIAN reference architecture (version 14.0). It covers business-to-landscape mapping, service contracts, and the translation of BIAN concepts into Hexagonal/DDD code. It does not replace the official BIAN portal: confirm each Service Domain definition there before treating it as a contract.

## Workflow

1. Set the context: business event or capability, actors, scope, and the BIAN version in use (default `v14.0`).
2. Locate Service Domains in `references/service-landscape-v14.md` by asset and behavior, not by current system names. Drop obsolete or renamed SDs.
3. For each SD, name its Functional Pattern, Control Record, and the Behavior Qualifiers involved.
4. Describe the Business Scenario as Service Exchanges between SDs (caller → callee, Action Term, purpose), preferably as a Mermaid `sequenceDiagram`.
5. Define contracts: Service Operations by Action Term, the reference Semantic API, payloads from BOM or ISO 20022, and `x-bian-*` OpenAPI extensions.
6. Map to implementation: SD → bounded context, CR → aggregate root, Service Operation → use case port, Service Exchange → outbound port/adapter, BOM → edge DTO/ACL.
7. Report gaps (local extensions), intentional deviations, coupling risks, and conformance checklist results.

## Rules

- Use official SD names in English, exactly as published; never rename or translate them in code.
- One Service Domain controls exactly one Control Record; flag any CR owned by two components.
- Service Domains interact only through Service Operations: no shared database, no direct state reads.
- Infrastructure (queues, caches, gateways, dispatchers) and end-to-end processes are never Service Domains.
- Do not use SDs marked obsolete in v14.0 (e.g. Payment Execution, Payment Instruction, Payment Order, ACH Operations).
- Record gaps as documented local extensions linked to the nearest official SD; justify deviations in an ADR.
- Mark anything not confirmed in the official portal as a working assumption.
- Always close with the five required sections: Context and Scope, Service Landscape Mapping, Business Scenario, Contracts and Implementation, Gaps/Deviations/Risks.

## References

- `references/bian-architecture-skill.md`: metamodel, golden rules, Functional Patterns, Action Terms and API conventions, code mapping, output template, consignado example, official sources, checklist, and anti-patterns.
- `references/service-landscape-v14.md`: full v14.0 catalog (5 Business Areas, 38 Business Domains, Service Domains) with version changes.
