# Skills and project knowledge strategy

Status: specifications only; no project-local skills created/installed. [Index](README.md).

## Current evidence and authority

The 17 global user skill manifests exist under `C:/Users/IQRA COMPUTER/.agents/skills/`; their agents/openai.yaml metadata specifies `allow_implicit_invocation: false`. The manifests/descriptions and explicit invocation metadata were inspected. Selected API/security/PostgreSQL/interface-design guidance was read for this blueprint, with frontend-design guidance for the landing direction. The [installation receipt](../project-documentation/ENGINEERING_SKILL_INSTALLATION_RECEIPT.md) records an earlier discovery verification; this pass verifies local files, not a fresh skills API discovery or every upstream workflow.

| Global skill | Applicable future work |
|---|---|
| emil-design-eng | Guest polish, focus/details, coherent interactions |
| animate | Purposeful new transitions after design approval |
| review-animations | Review motion against purpose/reduced motion |
| animation-performance | Composite-only properties and measured frame cost |
| apple-design | Physical sheet/gesture behavior where appropriate |
| frontend-design | Landing composition and distinctive food-led identity |
| interface-design | Owner/admin task hierarchy and consistent controls |
| web-design-guidelines | Accessibility and interface review |
| webapp-testing | Approved browser journeys; no unapproved helper/tool install |
| test-driven-development | Defect reproduction and domain regression tests |
| security-and-hardening | Trust boundaries, access, private media and abuse cases |
| code-review-and-quality | Correctness/security/architecture review of scoped diffs |
| vercel-react-best-practices | Applicable React 18 patterns/bundle work; exclude Next/RSC/React19 examples |
| supabase-postgres-best-practices | PostgreSQL constraints/indexes/locks; no Supabase auth migration |
| api-and-interface-design | Additive typed DTO/contracts and idempotency |
| performance-optimization | Benchmark before optimization and budget setting |
| observability-and-instrumentation | Failure/latency/cleanup evidence, redacted structured logs |

Global guidance does not authorize code changes, helpers, dependencies, service startup or cloud/DB actions. Installed engineering compatibility references explicitly preserve this stack and report-only boundaries. No need to invoke all skills simultaneously. A skill’s desired cookie/session pattern is not permission to replace the existing JWT auth architecture.

## Knowledge ownership

- [AGENTS.md](../AGENTS.md): concise project instructions, architecture/security/test rules. **Unchanged under Phase 0.** Propose a later narrow update to historical authorization/audit-state wording only with approval.
- [project-documentation/](../project-documentation/MASTER_PROJECT_DOCUMENTATION.md): actual implemented source/contracts and dated execution evidence. Update after approved code milestones, with explicit verification limits.
- [product-evolution/](README.md): this authoritative future blueprint, decisions and dependency roadmap. Mark requirements implemented only after evidence, link actual-state docs and retain decisions rather than duplicating a second future roadmap.
- Historical root audit/roadmap and [earlier custom-skill proposal](../project-documentation/ATELIER_CUSTOM_SKILLS_PROPOSAL.md): preserved evidence. The earlier four-skill text predates the expanded approved product direction; this new specification is the forward proposal, not permission to overwrite/install the old draft.

## Proposed instruction-only local skills

Future proposed location: `.agents/skills/{name}/SKILL.md` and `agents/openai.yaml` beneath the actual nested Git root. Do not create global duplicates. All default prompts should be report-only unless the user explicitly authorizes implementation; metadata remains explicit-only. Descriptions route use by task, not generic keywords.

| Name | Trigger and scope | Required references | Output and stop boundary |
|---|---|---|---|
| atelier-project-context | Starting/continuing an Atelier task or reconciling documentation | AGENTS, current Git diff, current master/source, this index/verification/decisions | Concise current/planned/evidence map and scope; no invented memory or blanket implementation permission |
| atelier-tenant-security | Admin/studio access, tenant media/table/request/plan ownership, support sessions | Security ledger, security plan, access services/controllers and signed-token/isolation tests | Route→principal→account→ownership matrix, abuse tests and minimal proposed fix; no production access or auth rewrite |
| atelier-analytics-verification | Event tracking, aggregates, dashboards, QR/share/AR claims | Analytics spec, event DTO/service/repositories, actual producers/widgets and tests | Event→row→aggregate→API→label evidence; distinguish intent/handoff/delivery, context/scan, browser/person |
| atelier-guest-experience | Guest browsing/modes/QR/cart/share/AR fallback and responsive acceptance | Guest/mode/WhatsApp specs, GuestMenuPage/DishSheet/ModelStage/router/client | Journey state/error/accessibility matrix; browser versus physical-device evidence, no simulated live claims |
| atelier-brand-studio | Appearance settings/drafts/assets/publication/QR exports | Brand/QR and design specs, BrandingService/settings DTO, BrandCustomizer/theme/QR helpers | Field→save→published→guest/export trace, contrast/print scan tests; no arbitrary code/style or route change |
| atelier-entitlements-and-quotas | Plan revision/assignment/override/resolver/quota changes | Entitlement/database/admin specs, actual policy repositories/services/tests once implemented | Deterministic precedence/provenance, downgrade/reservation/expiry/lock-order tests; no override of safety or unsupported keys |

Recommend the sixth specialized skill: quota and policy correctness crosses uploads, models, tables, menus, snapshots, admin changes and cache boundaries; mixing it into generic security guidance would obscure accounting/expiry details. It is an instruction discipline, not a replacement for backend enforcement. Source references to future services must be added only after those files exist.

Each future manifest should: locate the nested Git root; read authorization and relevant authoritative docs; inspect current source/diffs; state review versus approved implementation; preserve user changes; trace actual boundary paths; use only safe approved targets; distinguish test types and limitations; output findings/changes/evidence; never commit/push/install or create recurring background work automatically. Avoid duplicating entire specs inside skills. No scripts, hooks, executable helpers or cascading automatic skill invocations.

## Dependencies, review and acceptance

User must review these names/scopes/reference contracts before skill creation. Creation itself is a separate bounded documentation/metadata task; approval does not implement application findings. Exact final manifest/default-prompt wording, invocation discovery and parse checks are required before installation claims. No skill-builder tool is needed to install anything during Phase 0.

Future validation: verify frontmatter/name/description, explicit-only policy and all real source/doc links; discover from nested project root with zero parse errors; smoke-test report-only routing on synthetic source scenarios without editing files or sending traffic. Test wrong-tenant category/table, quota final-slot race, QR context versus scan, native share versus delivery and draft versus published state. Security acceptance requires no secret, signed URL or private data in outputs and no skill-based expansion beyond task authorization. Review stale pointers after every relevant implementation milestone.
