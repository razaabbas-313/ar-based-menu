# Plans, entitlements and quotas

Status: architecture proposal; no billing. [Index](README.md).

## Current implementation

[Restaurant](../backend/src/main/java/com/atelier/media/model/Restaurant.java) stores lifecycle/status, usage counters and `canUpload3d`. [AtelierProperties](../backend/src/main/java/com/atelier/media/config/AtelierProperties.java) defines global file/display limits. [DishService](../backend/src/main/java/com/atelier/media/service/DishService.java) checks direct-model permission; [AdminService](../backend/src/main/java/com/atelier/media/service/AdminService.java) changes that permission. These are useful compatibility inputs, not a configurable plan/quota engine. A menu query limit is not a dish creation quota.

## Registry and plan structure

Maintain a code-supported typed registry with stable key, type, default, allowed range/set, units, dependency rules, global availability, owner preference eligibility and override eligibility. Persist supported values/configuration, not executable rules. Unknown keys/types fail validation; an admin cannot create software capability by entering an arbitrary key.

| Proposed key | Type/default recommendation | Dependency or quota definition |
|---|---|---|
| menu.allowedModes | Enum set; includes SIMPLE | THREE_D/AR use validated viewer infrastructure |
| ordering.enabled | Boolean false | Usable configured recipient and enabled menu |
| waiter.enabled | Boolean false | Usable recipient; optional valid-table requirement |
| qr.venue / qr.table | Boolean | Table capability requires venue route support |
| model.ownerUpload | Boolean from legacy canUpload3d migration | Upload format/size/hard security limits |
| model.concierge | Boolean | Request workflow available |
| branding.level / qr.templateLevel | Curated enum | Only supported schema/template IDs |
| analytics.level | NONE/BASIC/ADVANCED proposal | Available events; no fabricated widgets |
| reviews.enabled | Boolean false | Validated external destination |
| quota.dishes | Nonnegative integer | Nondeleted dishes, including pending new-photo rows |
| quota.tables | Nonnegative integer | Active tables plus creation reservations |
| quota.models | Nonnegative integer | Assigned active model slots plus new-slot reservations |
| quota.storageBytes | Nonnegative integer bytes | Retained managed originals/models/references/variants plus pending reservations |
| quota.modelUploadBytes | Integer bytes | Minimum of entitlement and global origin-specific hard cap |
| quota.conciergePerPeriod | Integer plus period definition | UTC calendar-month submissions; count cancelled/rejected submissions to prevent cycling, admin reversal audited |

Exact initial values are unapproved. Use an explicit typed unlimited flag/value within allowed definitions, never overloaded negative integers. Zero prohibits net growth/use. Concurrency reservations and active resource usage are distinct from historical metadata retention.

Recommend immutable plan revisions: plan identity/name/description/active/marketing visibility; revision holds validated feature/quota values. Each restaurant is pinned to a revision. Editing creates a new draft/revision; publishing does not silently change assigned restaurants. Admin explicitly previews/migrates assignments with audit evidence. Optional marketing description/price text is inert display metadata requiring separate approval, never a billing product. Starter/Growth/Premium/Enterprise are illustrative names only.

## Deterministic effective-value algorithm

1. Reject unavailable/deleted/suspended account operations through existing access/publication rules. No feature grant bypasses these checks.
2. Load known registry definitions, hard safety ceilings and global capability availability. A global disable wins over plans/overrides. Preserve SIMPLE browsing when public access otherwise permits it.
3. Load the restaurant’s pinned validated plan revision; absent keys use documented safe defaults. A legacy mapping preserves current permitted behavior until explicit migration.
4. Apply active restaurant overrides for override-eligible keys. Start inclusive, expiry exclusive; use server UTC time. Each override has typed value, reason, actor and timestamps. Numeric override may raise a plan cap but never exceed a hard ceiling. Explicit false/zero is a value, not absence.
5. Apply global availability masks and hard ceilings; validate dependencies over the resulting set. Reject an admin write with unsatisfied dependencies. Runtime global shutdown masks dependents to safe disabled states and supplies reason codes.
6. Resolve owner preferences only within allowed values. Effective mode falls back to SIMPLE if an old preference becomes disallowed. Ordering/waiter/reviews require both entitlement and owner enablement/configuration.
7. Return effective values with provenance, config revision, expiry/next reevaluation time, usage/reservations and human-readable denial reasons. Public projection contains only necessary action flags/mode, never plan internals, actor notes or override reasons.

Overrides replace a plan value rather than deep-merging ambiguous arbitrary JSON. Registry validators own structured types. Same-key overlapping active overrides should be prevented or version-replaced transactionally. Expired values cease at request time even if a scheduler is late. Cache expiry is bounded by earliest override expiry; mutation authorization reads fresh policy under the quota/configuration lock. Menu publication caches must not keep a disabled capability alive.

## Enforcement and race safety

All create/reactivate/upload/confirm/concierge/table/order paths use an effective-policy service in the domain layer. Owner UI mirrors it but cannot enforce it alone. For a quota mutation, lock the restaurant’s policy/usage row, reload configuration/version, calculate usage including reservations, reserve/mutate/audit as appropriate and commit. All competing resource operations use the same tenant-first lock order. Avoid network I/O under the lock.

Presign reserves bytes and resource slots with an expiry; upload confirmation settles actual size exactly once. Replacement reserves temporary extra bytes but only an additional model slot when necessary. Expiry/cancellation releases a reservation once, while orphan cleanup remains observable. A worker cannot simply erase accounting before S3 deletion is acknowledged. Reconcile counters from defined sources with an audited repair job; current counters alone are not sufficient proof of complete storage usage.

Downgrade example: 100 dishes with new cap 30 preserves all 100. Mark over limit, block create and reactivation that increases counted usage, allow edits/deactivation/deletion and replacement within the approved temporary-byte policy. Do not automatically hide/delete existing records. Disabling a capability may hide its actions while retaining assets/history; never revoke account/security controls. Re-enabling a table/model slot uses the same quota gate. Plan changes, overrides and reservation expiry race under the same policy lock/version discipline.

## Dependencies, decisions and acceptance

Depends on current security gates, additive schema/audit, agreed usage/retention definitions and safe cache behavior. Decide initial plan catalog, legacy grants, storage scope, reservation TTL, monthly period/grace and operator confirmations before implementation. Recommend the specialized sixth project skill because these rules span nearly every new write path.

Test resolution/provenance, false/zero/unlimited, unknown keys, dependency rejection, global disable, overlapping/expired overrides, owner invalid preferences, stale clients and multi-instance reads. Use real PostgreSQL transactions for two requests competing for the final dish/table/model/request/storage slot and concurrent policy downgrade. Test idempotent confirm/release/retries and denial before S3 signing. Verify failed audit/config writes roll back. No current plan quotas are claimed to be enforced by this proposal.
