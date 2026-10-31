# Product vision

Status: Phase 0 proposal, 2026-10-31. [Index](README.md).

Atelier AR should let a restaurant present food beautifully, offer the appropriate level of 3D/AR, and help guests communicate an order or request assistance through WhatsApp. Owners manage one coherent restaurant workspace. Platform operators configure supported capabilities and limits through audited controls. Prices describe dishes and estimated requests; the product never collects payments or bills subscriptions.

## Current foundation

[GuestMenuPage](../frontend/src/pages/GuestMenuPage.jsx), [StudioPage](../frontend/src/pages/StudioPage.jsx), [AdminPage](../frontend/src/pages/AdminPage.jsx) and [HomePage](../frontend/src/pages/HomePage.jsx) already implement substantial surfaces. [MediaService](../backend/src/main/java/com/atelier/media/service/MediaService.java) and [ModelRequestService](../backend/src/main/java/com/atelier/media/service/ModelRequestService.java) supply direct uploads and human concierge operations. Existing contact links, branding and venue QR are useful foundations, not a complete ordering or subscription system.

## Proposed product contract

| Audience | Outcome | Honest product promise |
|---|---|---|
| Guest | Browse photos; inspect a model; launch supported AR; prepare/share an order image | Guest sharing is an order request; delivery and acceptance are unconfirmed |
| Restaurant owner | Manage menu, contact details, tables, models, appearance and engagement | Controls operate within effective entitlements and backend limits |
| Super admin | Provision restaurants, assign plans, configure permitted overrides, supervise models | Flexibility never overrides account checks, tenant isolation or audit integrity |

Exactly three primary modes share the same menu: SIMPLE, THREE_D, AR. Ordering and waiter assistance are independent features. Preserve photo-only dishes and printed `/r/{slug}` links. Keep Java 21/Spring Boot, PostgreSQL/Flyway, React 18/Vite/Tailwind, custom routing/client, S3 direct upload and model-viewer.

Quality means reliable publication, truthful analytics and graceful fallbacks as well as visual polish. Commercial readiness must be demonstrated with isolated persistence tests, browser journeys, responsive/accessibility checks and actual supported AR devices. A successful build is not that demonstration.

## Boundaries, dependencies and decisions

Security/stability precedes capability expansion. Entitlements precede public mode selection and table/order controls. New providers, infrastructure, skill installation and production release each require separately approved scope. No public signup is assumed; retain admin provisioning and the existing contact/demo flow until onboarding changes are approved.

Open commercial decisions include initial plan definitions, limits, public marketing visibility, supported currencies/regions and retention. [Decision register](DECISIONS_AND_OPEN_QUESTIONS.md) owns those questions.

## Acceptance and tests

- A provisioned restaurant can select only allowed modes; a guest always retains a usable photo menu.
- Owner A cannot alter or read owner B’s resources through any new workflow.
- Sharing, analytics labels and marketing never imply unsupported delivery, physical scans, AR success or payment revenue.
- Existing menu, upload/replacement, categories, authentication and concierge journeys remain regression gates.
- Launch readiness requires evidence from the [roadmap](IMPLEMENTATION_ROADMAP.md); no current production readiness claim is made.

Security implication: configuration adds privileged operations and public actions. Every capability needs a backend policy, ownership checks, bounded input and an explicit failure state; visual hiding alone provides no enforcement.
