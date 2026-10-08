---
name: service-modeling-skill
description: "Use when modeling TOGAF Business Architecture services: differentiating Business Service ('what') from Service Offer ('how it is consumed'), mapping value streams, or structuring a business-architecture response with service/offer/risk sections."
---

# Service Modeling (TAGF)

## Scope

Use this skill to model and differentiate Business Services from Service Offers under the TAGF business-architecture framework. It is a business-modeling skill, not a technical implementation guide.

## Workflow

1. Identify the strategic context: which business problem is being solved and who the stakeholder/value beneficiary is.
2. Specify the Business Service: infinitive-verb name, the value transformation it delivers, why it is stable over time, and the actors/capabilities behind it.
3. Detail the Service Offer(s) built on top of it: audience-focused name, which Business Service(s) it packages, delivery channels, SLA/operational parameters, and commercial model.
4. Report obstacles and governance risks, with mitigations and known modeling anti-patterns.

## Rules

- Do not model a technical component (endpoint, screen, database) as a Business Service — that is, at most, a delivery channel for a Service Offer.
- A Business Service is technology- and channel-independent; a Service Offer carries the SLA, price, and channel.
- One Business Service can back many Service Offers (1-to-N); do not conflate the two directions.
- Always close with the four required sections: Strategic Context, Business Service, Service Offer(s), Obstacle Report.

## Reference

See `references/service-modeling-skill.md` for the full TOGAF conceptual grounding and output template.
