# Atelier AR product evolution

Phase 0 blueprint, 2026-10-31. **Planning only; no future phase is authorized by these documents.** The user’s supplied master brief governs this proposal. There are no payment systems, subscription billing, payment links or payment-dependent order states.

The product direction is a configurable restaurant experience platform: three menu modes, guest-prepared WhatsApp order images, table and venue QR, human model operations, curated brand controls and trustworthy engagement analytics. Extend the inherited application; preserve its working features and uncommitted security fixes.

## Reading order and authority

1. [Repository verification](REPOSITORY_VERIFICATION.md): current source evidence, actual checks and limitations.
2. [Product vision](PRODUCT_VISION.md) and [requirements](PRODUCT_REQUIREMENTS.md).
3. [Architecture](SYSTEM_ARCHITECTURE_PROPOSAL.md), [database/API plan](DATABASE_AND_API_CHANGE_PLAN.md) and [entitlements](PLANS_AND_ENTITLEMENTS_SPEC.md).
4. [Security plan](SECURITY_AND_RELIABILITY_PLAN.md), [roadmap and first milestone](IMPLEMENTATION_ROADMAP.md), [decisions](DECISIONS_AND_OPEN_QUESTIONS.md).

| Experience | Specification |
|---|---|
| Shared visual foundation | [Design system](DESIGN_SYSTEM_SPEC.md) |
| Marketing and onboarding | [Landing page](LANDING_PAGE_SPEC.md) |
| Guest browsing and interaction | [Guest experience](GUEST_EXPERIENCE_SPEC.md) |
| Simple, 3D, AR | [Menu modes](MENU_MODES_SPEC.md) |
| Cart, order images, sharing, waiter requests | [WhatsApp ordering](WHATSAPP_ORDERING_SPEC.md) |
| Venue/table identity and QR lifecycle | [QR and tables](QR_AND_TABLE_MANAGEMENT_SPEC.md) |
| Uploads, versions, concierge | [Model management](MODEL_MANAGEMENT_SPEC.md) |
| Owner administration | [Restaurant studio](RESTAURANT_STUDIO_SPEC.md) |
| Platform administration | [Super-admin controls](SUPER_ADMIN_CONTROL_SPEC.md) |
| Appearance and print design | [Brand and QR Studio](BRAND_AND_QR_STUDIO_SPEC.md) |
| Events, definitions and reporting | [Analytics](ANALYTICS_SPEC.md) |
| External review destination | [Google Reviews](GOOGLE_REVIEWS_SPEC.md) |
| Persistent project context and skill specifications | [Knowledge strategy](SKILLS_AND_PROJECT_KNOWLEDGE_STRATEGY.md) |

Source and reproducible tests outrank historical prose. [AGENTS.md](../AGENTS.md) remains unchanged; some audit-state wording there is historical. [project-documentation/](../project-documentation/MASTER_PROJECT_DOCUMENTATION.md) describes the existing implementation. This directory owns the new future product blueprint; earlier roadmaps and skill proposals remain historical evidence, not competing approvals. When a feature ships, update actual-state documentation with evidence and mark its corresponding requirement implemented. Do not mark a plan implemented because its specification exists.

## Review and acceptance

The 21 requested specifications plus this index and verification ledger form one navigable set. Each specification distinguishes current functionality, proposals, dependencies/decisions, acceptance tests and security boundaries. Review links and source anchors when approving a milestone. No application source, migrations, dependencies, credentials, AGENTS.md or infrastructure may change under Phase 0. No commit/push or local skill installation is included.
