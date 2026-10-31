# Phase 0 repository verification

Snapshot: 2026-10-31, Git HEAD `330a49c` plus existing uncommitted work. [Index](README.md). This ledger separates executed checks, inspected source and future proposals.

## Scope and preservation

Actual Git root is `atelier-3d-media-engine/` inside the supplied `atelier-3d-media-engine-v4` workspace. `product-evolution/` did not previously exist. This task creates the 21 requested specifications, README and this additional evidence ledger: **23 Markdown files**. Existing root documents, project-documentation, application/test source, schema, dependencies, deployment templates, credentials and AGENTS.md were not edited. No local skill was installed/created; no commit or push.

A SHA-256 preservation snapshot covers 314 pre-existing Git-visible tracked/untracked files, including the uncommitted security changes and existing documentation. Comparison after drafting found zero changes to those files. Ignored build/test artifacts were refreshed by verification, so “no application changes” does not mean build-output directories were untouched. The preservation snapshot and local logs live in ignored backend/target, not the authoritative document set.

## Stack checked from repository and tools

| Area | Declared/resolved evidence |
|---|---|
| Backend | [pom.xml](../backend/pom.xml): Java21, Spring Boot3.3.5, Security/JPA/Flyway, AWS SDK2.28.29 |
| Frontend | [package.json](../frontend/package.json) and [lock](../frontend/package-lock.json): React18.3.1; resolved Vite5.4.21, Tailwind3.4.19, model-viewer4.4.0 |
| Routing/client | [router.js](../frontend/src/utils/router.js), [client.js](../frontend/src/api/client.js), [AuthContext](../frontend/src/context/AuthContext.jsx); no router/auth replacement |
| Persistence | PostgreSQL declarations and separate H2/PostgreSQL V1–V13 trees; no live PostgreSQL version inspected |
| Runtime toolchain | Temurin Java21.0.12.1, Maven3.9.16, Node24.21.0/npm11.19.0; Java/Maven available after process-only environment refresh |
| Media | [S3Storage](../backend/src/main/java/com/atelier/media/service/S3Storage.java), browser direct PUT and [upload.js](../frontend/src/utils/upload.js); public/private prefixes remain separate |

## Fresh checks executed for this task

| Check | Actual result | Boundary |
|---|---|---|
| `mvn -o test` from backend | **374 tests, 29 report classes, 0 failures/errors/skips; BUILD SUCCESS; 1m45s** | Existing cached dependencies; H2/test profiles, mocked S3; not PostgreSQL/cloud acceptance |
| Test migration output | 13 H2 migrations applied to disposable in-memory schema through v13 | Does not prove PostgreSQL partial-index/locking behavior |
| `npm run build` from frontend | **Passed**, 1809 modules, 32.44s | Compilation/bundle output, not browser behavior |
| Main JS output | 579.61kB raw /161.62kB gzip | Not network/CPU timing or Core Web Vitals |
| model-viewer output | 1072.76kB raw /300.17kB gzip, separate chunk | Lazy import exists; sheet preload behavior still needs measurement |
| CSS output | 63.69kB raw /11.91kB gzip | No CSS/accessibility coverage inferred |
| `git diff --check` | Passed; existing CRLF normalization warnings only | Existing user modifications retained |
| New-document links/structure and preservation | Validated locally before completion | Local link resolution does not validate external destination behavior |

The first sandboxed Maven attempt stalled after Mockito/Byte Buddy could not open its Windows Java-agent named pipe; it was cancelled. The authorized outside-sandbox offline rerun succeeded without changing tests, JVM configuration, dependencies or application source. Current results come from that fresh successful log and Surefire XML totals, not the historical 208/374 claims. Nonfatal Java-agent warnings do not invalidate the successful rerun.

Ignored evidence: `backend/target/phase0-mvn-test-approved.log`, `backend/target/phase0-frontend-build.log`, `backend/target/surefire-reports/` and `backend/target/phase0-preservation-baseline.json`. These are local transient evidence, not guaranteed portable deliverables. No frontend test/lint scripts exist, so neither is reported as run.

## Inspection coverage and source conclusions

Repository inventory covered application source/resources/tests, package/POM/lock/build configuration, both migration trees, infra/deployment templates, historical onboarding/audit documents and project-documentation. Detailed traces focused on current admin/restaurant guards, category ownership, OTP repository/transactions/sender, menu privacy and DTOs, guest event producers, analytics writes/read models, upload/model replacement/history, appearance/contact settings, QR routing/exports and the custom React architecture. Existing actual-state documents were consulted and cross-checked against these paths. This was not exhaustive line-by-line review of third-party dependencies/binary assets, a full-history secret audit or a certification of every source path.

- A01–A03 remediation exists in the working tree and fresh regression suite passes, including 159 AccountAccessSecurityTest executions and seven CategoryIsolationTest executions. Controller guards do not automatically protect future unrelated controllers; preserve equivalent gates.
- A04 remains a source-supported transaction defect; existing passing OTP tests use mocks and cannot prove committed failure state or single-use concurrency.
- A05 remains a conflict in supplied templates, not evidence of deployed bucket policy. No AWS account/policy request was made.
- A06, A07, A08, A09, A10, A12, A13 and A19 remain source-supported contract/lifecycle/reliability gaps; A11/A14/A15 are partly improved, not closed. A16/A17 remain documentation/performance work; A18 requires device evidence. [Security plan](SECURITY_AND_RELIABILITY_PLAN.md) covers every A01–A19 plus PD01.
- PD01: backend PublicMenuDto exposes menuLive, guest reads isMenuLive; the legacy envelope constructor also discards live/category context. Server private menu still withholds dishes. No private-item leak is claimed.
- No dedicated cart/order image/table registry/plan engine/review action exists in inspected sources. Existing WhatsApp contact field/link, venue QR, basic branding/analytics and human concierge are reusable.
- Admin model upload/publication already works without an active request at the service level. New proactive selection/provenance/issued-key/version-history requirements are extensions, not an entirely missing upload backend.
- Current photo processing downsamples/re-encodes at 1400px/.82; current model replacement may delete the old object. Those conflict with new master-quality/history requirements and are explicitly planned, not silently fixed.

## Primary-source feasibility research

Checked 2026-10-31; mutable external rules must be rechecked before integration/release.

| Source | Design implication |
|---|---|
| [W3C Web Share](https://www.w3.org/TR/web-share/) | User chooses target; file sharing support/activation must be checked; completed handoff does not prove message delivery |
| [WhatsApp click to chat](https://faq.whatsapp.com/5913398998672934) | Recipient + URL-encoded prefilled text, not an automatic image attachment interface |
| [WhatsApp Business policy](https://business.whatsapp.com/policy) and [developer hub](https://whatsappbusiness.com/developers/developer-hub/) | Separate approved sender/onboarding/recipient-permission/template/window/cost work for any official automation; no integration activated |
| [Google review-link guidance](https://support.google.com/business/answer/3474122?hl=en) | Link-only review CTA is feasible; avoid incentives and claiming click equals posted review |
| [PostgreSQL INSERT](https://www.postgresql.org/docs/current/sql-insert.html) | Atomic upsert is a candidate repair for daily aggregation; target DB tests still required |
| [AWS presigned URL guide](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html) | Reusable until expiry/same-key overwrite risk requires explicit content/finalization design |

Meta’s direct Cloud API send-message guide returned HTTP429; exact API version/current media limits were not verified. No unofficial source or library was used as a substitute for production API design. The detailed sharing state proposal is an engineering inference from official browser/messaging boundaries, not evidence that Atelier already implements it.

## Skills, exclusions and acceptance

All 17 requested manifests and explicit-only metadata were found locally; selective guidance informed proposals. [Knowledge strategy](SKILLS_AND_PROJECT_KNOWLEDGE_STRATEGY.md) records usage, future skill scopes and approval boundaries. No new skill discovery/runtime claim is inferred from the earlier installation receipt.

Not executed: live PostgreSQL migration/query/concurrency checks, S3 permission/upload/delete/replay checks, email delivery, WhatsApp messages/automation, Google API calls, authenticated browser journeys, responsive/screen-reader measurements, Core Web Vitals, physical QR scans, real AR/device scale checks, deployment or production database access. No dependencies/services were installed or paid integrations selected.

Phase 0 acceptance is a navigable source-linked future blueprint, explicit current/planned distinction, complete requirement coverage, accurate test evidence and preserved pre-existing files. Future feature acceptance belongs to its approved milestone/spec, not this ledger. Next implementation recommendation is milestone 1A in the [roadmap](IMPLEMENTATION_ROADMAP.md); it remains unapproved.
