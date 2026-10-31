# Model management

Status: proposal extending existing operations. [Index](README.md).

## Current source and conflict

[DishService](../backend/src/main/java/com/atelier/media/service/DishService.java) supports entitled-by-boolean owner attachment/confirmation/removal. [ModelRequestService](../backend/src/main/java/com/atelier/media/service/ModelRequestService.java) handles private reference photos, human production, admin publication and request history. Its admin presign can target a dish without an active request; approval creates a completed history record in that case. [Admin3dDeskModal](../frontend/src/components/admin/Admin3dDeskModal.jsx) is oriented around requests, not comprehensive proactive restaurant/dish selection.

This is partial reuse for direct super-admin upload, not a wholly absent backend. Current replacement can delete the previous model after commit; request history does not guarantee retained downloadable versions, actor/origin lineage or complete asset history. The new retention requirement conflicts with that deletion policy and needs a deliberate lifecycle change.

## Proposed three origins

| Origin | Authority and flow | Required provenance |
|---|---|---|
| RESTAURANT_UPLOAD | Current restaurant access + model-upload entitlement + own completed dish; staged presign/PUT/confirm | Tenant, owner/support actor identity, dish, issued upload, size/type/checksum |
| CONCIERGE | Owner submits bounded references/details; admin human workflow creates and publishes output | Request ID, submitting actor, production status, publishing admin |
| ADMIN_DIRECT | Current admin explicitly selects restaurant and its dish; issued upload/PUT/confirm | Verified acting admin, target pair, reason, origin; never fabricate a guest/owner request |

Use the same validation/finalization service for all origins with explicit policy inputs. Do not reuse a path that falsely marks a concierge request completed when no request exists. Human queue supports submitted/in production/completed/rejected/cancelled states with recorded reasons and timestamps; only implement transitions actually backed by endpoints. Current code status coverage must be mapped before extending it.

## Version and asset architecture

Add immutable `model_versions`: tenant/dish, monotonic version, origin, uploader kind/ID, optional request, issued upload ID, object metadata, created/assigned/published timestamps and lifecycle. One dish has one current model version, but may retain superseded versions. Lock dish/tenant quota consistently when allocating/assigning; unique dish/version and tenant-scoped relationships prevent cross-assignment. Preserve existing Media model fields as a compatibility projection initially.

Pending upload issuance binds actor, tenant, dish, kind, expected size/type and expiry. Validate S3 metadata outside DB transactions, validate actual supported model content through an approved bounded mechanism, then recheck issuance, current access, quotas and optimistic version in a short transaction. Presign/publication is retry-safe and cannot assign an unrelated prefix-only key. Browser uploads remain direct to S3.

Retain old model objects and history until the separately approved retention policy expires; never delete merely because replaced. A metadata record cannot recover an already-deleted old object: legacy backfill labels availability unknown/missing and origin LEGACY_UNKNOWN rather than inventing uploader/history. Rollback/republication, if approved, creates an assignment event with actor and revalidates content/quotas. Owner and admin views expose safe history; private reference URLs remain authorized, short-lived and nonpublic.

Count active models separately from retained/pending storage. Reserve pending bytes before presign, settle actual usage once on confirm and release once on expiry/cancellation; cleanup failure remains charged/visible until resolved according to the approved storage definition. Current direct 50 MiB and concierge 25 MiB caps are different: preserve until a reviewed common hard ceiling and entitlement limit are approved. Start with GLB; assess self-contained glTF/external-URI rules without claiming every glTF is portable.

## Dependencies, decisions and security

Requires A05/A09/A10/A12/A13 remediation, entitlements/quota reservations and retention approval. Asset history must not leak reference photos or claim confidentiality for already public published models. No automated reconstruction provider, cloud changes or new renderer. Decide retained-version duration/count, rollback availability, owner-visible provenance and model validation worker/cost before implementation.

## Acceptance and test scenarios

Exercise all origins, no-request admin publication, explicit restaurant/dish mismatch, stale/foreign/unissued keys, byte/type mismatch, duplicate confirmation, concurrent replacement, revoked actor and over-limit assignment. Ensure old live model remains until successful publication; failed upload never deletes it. Test metadata/history visibility, retention holds, failed S3 deletion/retry, actual storage accounting and legacy unknown records. Use disposable PostgreSQL for locks/uniqueness and approved isolated S3 prefixes for permission/content/cache checks. Verify viewer fallback and native AR separately; metadata tests do not establish model scale or visual quality.
