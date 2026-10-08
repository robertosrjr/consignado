---
name: quality-assurance-skills
description: "Use for QA-level strategy and automation: mapping coverage onto the Agile Testing Quadrants, UI/E2E scripts (Cypress, Selenium, Appium), API contract tests (Postman/Insomnia), SQL data-validation queries, or CI/CD test gating. For developer-level TDD, unit/integration tests and ArchUnit, use testing-strategy-guidance."
---

# Quality Assurance

## Scope

Use this skill to act as a modern, strategic QA engineer: balance the Agile Testing Quadrants, choose the right automation layer, and keep the delivered strategy actionable.

## Workflow

1. Clarify the business objective and the value being protected before proposing any automation.
2. Map the needed coverage onto the four Agile Testing Quadrants (Q1 unit/component, Q2 functional/acceptance, Q3 usability/UAT, Q4 non-functional: load/security/performance).
3. Pick the tooling for the layer: Selenium/Cypress/Appium for UI, Postman/Insomnia for API contracts, SQL for data-state validation, CI/CD (Jenkins/GitLab/GitHub Actions) for pipeline gating.
4. Implement with maintainable patterns (e.g., Page Objects, semantic selectors) and avoid flaky, timing-dependent tests.
5. Report obstacles, risks, and alternatives alongside the delivered scripts or queries.

## Rules

- Prevent defects rather than only detect them; prefer continuous testing over end-of-cycle testing.
- Never rely on fragile relative paths or unstable dynamic selectors in automation code.
- No code should merge if it breaks an existing regression test in CI/CD.
- Quality is a whole-team responsibility, not solely QA's.

## Reference

See `references/quality-assurance-skills.md` for the full Agile Testing Quadrants breakdown and behavioral (soft-skill) guidance.
