# Milestone 1B — S3 policy and media lifecycle proposal

Read-only investigation, 2026-10-31. This report is the only authorized file creation. No fix, policy deployment, AWS access, migration, test execution, fetch, commit or push was performed. All proposed implementation tasks require separate approval.

## 1. Executive summary

The current direct browser-to-S3 architecture is reusable. Preserve Spring Boot as the JSON metadata/control plane, tenant-scoped services, staged replacements and model-viewer. The urgent issue is a contradiction between example private-storage policies and the application’s presigned-reference workflow; this is a template defect, not proof of deployed exposure. Separately, source-confirmed deletion acknowledgement and transaction-boundary defects make cleanup unreliable even with correct permissions.

Recommended order: correct/review policy examples and private cache semantics; establish truthful deletion results; preserve cleanup evidence durably; move concierge network operations outside transactions with state revalidation; then strengthen issued-upload binding and prepare retention/version accounting. Never make private references public to resolve a 403. No new infrastructure is needed for the first proposal.

Evidence labels: **SC** source-confirmed; **TC** template-confirmed; **PF** previously fixed; **RU** runtime-unverified; **DU** deployment-unverified; **FR** future requirement. Severity reflects potential impact conditional on use/deployment; no current live critical compromise is established.

## 2. Git state and verification summary

- Root: `C:/Users/IQRA COMPUTER/Desktop/atelier-3d-media-engine-v4/atelier-3d-media-engine`.
- Branch: `atelier-ar-development`; HEAD `0c86b0d84140fdd1dce5ff4f48081700995beea7`, message `feat: preserve Atelier AR security fixes and product blueprint`.
- Cached `origin/atelier-ar-development` equals HEAD. This is the latest locally available GitHub checkpoint, not a fresh remote assertion. Remote origin remains `https://github.com/razaabbas-313/ar-based-menu.git`. No fetch/pull/push or GitHub request was performed.
- Entry status: no tracked/index differences; untracked `product-evolution.zip` and `project-documentation.zip`. Both are preserved. Exit adds only this untracked report; nothing is staged.
- **PF:** `web/AdminController.java:67` uses current AdminAccess; `web/StudioController.java:90` applies RestaurantAccess; category lookups remain tenant-scoped. `service/OtpService.java:68,85,122,157` retains REQUIRES_NEW, account locking and post-commit validation errors. Password reset remains disabled by the unchanged default configuration.
- Historical evidence: [Milestone 1A](MILESTONE_1A_VERIFICATION.md) records 396 tests passed, 18 real H2 OTP executions, nine OTP unit executions and frontend build success. These were **not rerun** for 1B. No S3/PostgreSQL test or policy simulation is claimed here.

Documentation cross-reference: [AGENTS](../AGENTS.md), [historical audit](../TECHNICAL_AUDIT.md), [current security ledger](SECURITY_DOCUMENTATION.md), [security plan](../product-evolution/SECURITY_AND_RELIABILITY_PLAN.md), [roadmap](../product-evolution/IMPLEMENTATION_ROADMAP.md), [implementation status](../IMPLEMENTATION_STATUS.md), [overview](../CODEBASE_OVERVIEW.md), [architecture proposal](../product-evolution/SYSTEM_ARCHITECTURE_PROPOSAL.md), [model spec](../product-evolution/MODEL_MANAGEMENT_SPEC.md), [database/API plan](../product-evolution/DATABASE_AND_API_CHANGE_PLAN.md). Historical root audit wording does not reopen A01–A03 or undo 1A.

## 3. Current S3 architecture

All backend source anchors below are relative to `backend/src/main/java/com/atelier/media/`; frontend anchors begin `frontend/`. Line references describe this checkpoint and will drift after implementation.

`config/AwsConfig.java` creates S3Presigner and S3Client using explicitly configured static credentials or DefaultCredentialsProvider. No credentials were retrieved. Local signing does not itself establish remote permission; a default provider may retrieve credentials when used, so even signing experiments need a controlled test provider. AwsConfig logs an access-key suffix and bucket/region; avoid propagating these identifiers into public telemetry.

`service/S3Storage.java:78–89` constructs public `restaurant-atelier/{slug}/` and private `restaurant-atelier-private/{slug}/` namespaces. Actual roots come from AtelierProperties. Public URL construction uses a configured public base URL or regional S3 URL; this does not prove CloudFront deployment. `presignPut:115` signs MIME, exact content length and cache metadata for configured TTL (default five minutes). `presignGet:102` delegates GET access, normally fifteen minutes for references. `head:134`, delete and list methods are network operations; presign/publicUrl are local computation after credential resolution.

`frontend/src/utils/upload.js:26–38` and `frontend/src/api/client.js:207–217` stream bytes directly by XHR using required headers, then return ETag for JSON confirmation. Browser-settable headers exclude host/content-length (`S3Storage.browserSettableHeaders`); the browser supplies those. CORS must expose ETag and allow the signed headers. No Java binary-upload endpoint is recommended.

Environment names: AWS_REGION, AWS_S3_BUCKET, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_S3_PUBLIC_BASE_URL and ATELIER_PRIVATE_KEY_PREFIX; public/private root and TTL properties also use Spring binding. Role/default-chain configuration, session tokens, region, endpoint, encryption and actual deployed values remain **DU**. No configuration secrets were opened.

## 4. Public/private media access matrix

Notation: P = configured public root plus tenant slug; Q = configured private root plus tenant slug. “App” means the authenticated AWS signer/application identity, not the restaurant’s JWT. Browser bearer links delegate only the signed operation. A single app principal spans tenants, so IAM root scoping does not substitute for application ownership checks.

| Asset | Current key/prefix and uploader | Readers / writers / S3 operations | Signing and current exposure | Cache / retention / cleanup and gaps |
|---|---|---|---|---|
| Dish photo | P/{photo media segment}/{UUID}.{ext}; owner | Guests public GET; owner issues via app; app PUT/GetObject-for-HEAD/DeleteObject | PUT then scoped row confirmation; public URL exists before confirmation | Public immutable intended only for finalized bytes; stale reaper/photo delete/replacement cleanup; metadata-only checks and replay remain |
| Published model | P/{MODEL_3D segment}/{UUID}.glb or P/models/twin-{UUID}.glb; owner/admin | Guests GET; entitled owner/admin writes via app; HEAD/delete by app | Direct PUT; owner pending binding versus admin prefix-only confirmation | Public immutable; old objects currently deleted on replacement; not retained version history |
| Pending new photo | Same P final namespace; owner | Anyone knowing key can GET if public template deployed; app/browser PUT | DB PRESIGNED is not an S3 access boundary | 15-minute stale-row threshold by default; row marked FAILED then best-effort delete; failures lose retry selection |
| Pending replacement photo/model | Same P namespace; owner | Same public-read risk; writes delegated by app | Pending fields bind key/size; old live asset retained until confirm | Public immutable even before confirm; reaper clears pending metadata before acknowledged delete |
| Branding logo/banner | P/branding/logo- or banner-{UUID}.{ext}; owner | Guests GET; owner-controlled app PUT/HEAD/delete | Prefix checks on confirm; no durable issued-key/expected-size binding | Public immutable; replaced/removed assets deleted best-effort; abandoned presigns lack tracked reaping |
| Private concierge references | Q/requests/{dishId}/ref-{slot}-{8-char UUID suffix}.webp; owner | Authorized owner history/admin views receive temporary GET; app PUT/HEAD/GET/delete | Issued pending keys; GET signing 15m; templates currently contradict app access | Must be private/no-store; currently public immutable metadata; current dish photos cleaned >14d after completion, historical JSON remains |
| Pending concierge references | Same Q; owner | App + issued bearer PUT; no anonymous read intended | ModelRequestPendingKey binds tenant/dish/key/slot but not complete expected metadata/expiry | Need expiry/grace/retry retention; pending repository expiry method has no caller; unconfirmed objects orphan |
| Historical model assets | Existing public prefixes, possibly prior slug | Authorized history metadata; known public model URL still guest-readable | History records do not guarantee object availability | New retained versions are FR; old model deletion may leave broken historical URLs; choose explicit duration/holds |
| Deleted/orphaned assets | Any prior P/Q or prior slug | No legitimate new writes after deletion; existing bearer PUT may remain usable until expiry | DB deletion does not revoke issued S3 URLs; prefix sweep alone can race a late PUT | Need durable manifest, expiry-aware second reconciliation and retention checks; do not broaden public access |

The `.webp` private key suffix is fixed even though accepted MIME may be JPEG/PNG; metadata and bytes, not suffix, must drive validation. Private keys should not be exposed in guest DTOs, public logs or third-party URLs. Downloaded/cached copies cannot be remotely revoked merely by expiring a signature.

## 5. IAM and bucket-policy findings

| ID / severity | Classification and evidence | Root cause / effect |
|---|---|---|
| S3-01 High (A05) | **TC**, `infra/s3-public-read-policy.json`, DenyPublicReadPrivateAssets | Principal `*`, unconditional private GetObject Deny also matches the app signer. A presigned GET cannot override it. Live policy **DU** |
| S3-02 High (A05) | **TC**, `infra/iam-policy.json` | Object PUT/GET/DELETE and ListBucket conditions only cover public root; private PUT/HEAD/GET/delete/list lack example grants. HEAD requires GetObject permission, not an invented s3:HeadObject action |
| S3-03 Medium | **TC/DU**, `infra/s3-cors.json`, DEPLOYMENT.md:72–88 | Deployment guide shows public Allow-only policy, unlike checked-in blanket Deny. Wildcard hosted origins broaden browser permission but CORS is not authorization. BPA, ACLs, role policy and encryption configuration unknown |

Public reads intentionally require only GetObject on P. Private reads are authenticated app/delegated presigned GET; no public Allow for Q. IAM object permissions apply to object ARNs; ListBucket applies to bucket ARN with prefix conditions. Identity/resource permissions, boundaries, organization policies and explicit denies all affect the outcome; KMS encryption, if present, introduces separate key-policy permissions. Do not assume an Allow grants access against another applicable Deny. See [AWS evaluation logic](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_evaluation-logic.html).

## 6. Presigned URL security

**SC S3-04 Medium (A12):** URLs are bearer capabilities, reusable until expiry/credential expiry; matching-key PUT can overwrite confirmed bytes. Random keys reduce guessing, not replay by a URL holder. Same-size alternate bytes can pass length verification, while immutable caching/history still describes earlier content. [AWS presigned URL documentation](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html) documents reuse and replacement.

| Path and anchor | Operation binding / confirmation checks | Missing guarantee |
|---|---|---|
| MediaService.generatePresignedUploadUrl:73–130; confirmUpload:137–179 | Tenant row/key scoped; signed MIME/size; HEAD exact stored size; completed retry short-circuit; transactional state recheck | Client ETag not compared; MIME/content/dimensions not decoded; replay still possible |
| MediaReplacementService.presign:78–107; confirm:112–180 | Own completed dish; pending key/size/type; exact HEAD length; conditional ETag comparison; recheck pending key in transaction | ETag comparison only when both present; no content validation; key remains overwritable |
| DishService.attachModel:172–206; confirmModelUpload:210–259 | Current studio access and canUpload3d; pending exact key/size; recheck before swap | Confirmation does not compare client ETag or decode GLB; presign MIME is not binary validation |
| BrandingService.presignAsset:122; confirmAsset:138–185 | Current tenant; branding/kind prefix; HEAD positive size/allowed MIME/cap | No issued upload record, exact expected size or ETag binding; concurrent form/upload semantics need tests |
| ModelRequestService.presignPhotos:103–157; confirmRequest:162–231 | Own dish, slots/count/type/cap; tracked issued key plus current tenant/dish prefix; HEAD cap/type | No expected-size/ETag binding in pending key; confirmation must validate duplicate/mismatched slots against issuance, not merely normalize supplied slot; deletes all dish pending rows, potentially orphaning unsubmitted keys |
| ModelRequestService.presignModelUpload:265–280; approveModel:285–380 | Current admin controller guard; target dish resolves owning restaurant; model prefix, HEAD cap/MIME | No operation-issued final key binding, expected size/ETag or explicit requested tenant/dish pair; current admin privilege is not full asset provenance |

S3 ETag is not a universal content digest, especially with multipart/encryption. Client-submitted dimensions and metadata are not proof of decoded format/safety. The adapter GET signer accepts arbitrary keys from trusted callers; readers/history mapping must revalidate retained metadata ownership before signing, especially any legacy/imported rows. This is a hardening requirement, not a demonstrated cross-tenant request exploit.

## 7. Upload and replacement lifecycle

Photo: validate current account/category/metadata → create PRESIGNED row and sequential dish code in short transaction → sign PUT → browser uploads → tenant-scoped confirm HEAD outside transaction → reload state, publish and adjust usage atomically → evict menu cache. Presign failure can leave an unuploaded row; reaper handles known photo rows later.

Photo replacement: preserve old live photo; commit new pending key before signing; superseded pending key delete outside transaction; HEAD exact size and optional ETag comparison; transactional pending-key recheck/swap/accounting; delete previous object afterward. Owner model flow parallels this, with canUpload3d checks on issuance/confirmation. Media has `@Version` (`model/Media.java:47`); retain optimistic conflicts. This review does not establish PostgreSQL exactly-once accounting under concurrent confirmation.

**SC S3-05 Medium:** abandoned branding/admin-model presigns have no equivalent pending record/reaper. **SC S3-06 Medium:** old public photo/model/branding objects are deleted best-effort after DB references/accounting move on; a failed delete becomes an untracked physical object. Do not assume accounting equals actual retained bytes.

## 8. Private concierge references

Owner requests one-to-five supported slots on own dish. Controller current-account gate precedes scalar-ID ModelRequestService. Issued keys persist in ModelRequestPendingKey. Confirmation locks the dish, checks active request, validates Q/dish prefix and issued membership, HEADs each object, stores photo JSON on current dish and ModelRequest history, then deletes pending-key records for that dish.

`ModelRequestService.parsePhotos:475` and `presignReferencePhotosJson:493` turn stored keys into 15-minute GET links for staff/admin/history. Signing is local, not a network GET in those read transactions. Stored JSON can include a constructed public-style URL at confirmation; private prefix policy must keep that URL inaccessible to guests. Parsing/signing errors currently produce empty photo lists or original JSON, obscuring unavailable-reference states. Fail closed with a visible authorized “reference unavailable” state rather than expose legacy URLs as a fallback.

Cleanup: `cleanupReferencePhotos:448–472` selects current dishes marked COMPLETED with completion older than 14 days. It does not enumerate all historical/rejected/superseded/reference/pending records. Historical JSON can retain keys/URLs after current photo cleanup. Retention is currently a narrow scheduled rule, not an approved comprehensive policy or history-preservation guarantee.

## 9. Deletion and cleanup reliability

**SC S3-07 High for deletion integrity/privacy (A10):** `S3Storage.deleteKeys:165–181` calls quiet DeleteObjects, ignores response errors, catches SDK exceptions and returns submitted-key count. Quiet success does not mean empty response equals every key deleted if errors are ignored. `deleteQuietly:149` similarly swallows failures. [DeleteObjects API](https://docs.aws.amazon.com/AmazonS3/latest/API/API_DeleteObjects.html) distinguishes per-key errors, whole-request failure and versioned deletion.

`cleanupReferencePhotos` can clear current photo JSON and increment cleanedCount despite failed deletion, preventing subsequent selection. `StorageJanitor.wipeRestaurantAssets:24` can log “finished” with submission counts. `deletePrefix:186` accumulates every listed key in memory, returns zero on list failure and has no durable cursor/result state. These are source-confirmed misleading acknowledgement paths; no deletion was attempted.

**SC S3-08 High/Medium depending retention obligations:** `AdminService.hardDelete:372–392` gathers current public dish/model/pending/branding keys, deletes the restaurant, then asynchronously wipes known keys and only its current public prefix. Private request/history/pending assets and prior-slug unreferenced assets are omitted. Cascade deletion removes discovery metadata before cleanup can recover it. `analytics_daily` in PostgreSQL V10 has no tenant FK and can survive tenant deletion; raw events and other relationships need fixture-level cascade validation, not assumptions.

**SC S3-09 Medium:** MediaService.expireStalePresignedUploads:195–240 commits FAILED/clears pending fields before deleting; subsequent attempts do not rediscover lost keys. A catch/log is not a retry record. ModelRequestPendingKeyRepository.deleteByCreatedAtBefore exists but has no source caller and would only delete rows, not objects. Scheduler StaleUploadReaper runs after one minute and then every five minutes by default; SchedulingConfig can disable it. AsyncConfig enables in-process asynchronous wipes without durable work persistence. Process crash between DB commit and invocation loses cleanup intent.

Proposed deletion result: requested keys, acknowledged removed/absent keys, failed keys with bounded reason/retryability, and unknown-outcome keys for transport failure; never count submitted as deleted. Store a small cleanup manifest/task with tenant immutable ID, bucket/key or approved version ID, purpose/retention eligibility, attempts/next-run/error class/status and actor/request correlation. Commit cleanup intent with mutation; execute outside transaction; persist outcomes/retries. Do not clear the only reference or subtract retained storage until the defined accounting transition is acknowledged. DB logical deletion and physical purge are distinct states. Idempotent retries tolerate absent keys; permission failures require operator action rather than endless blind loops.

Versioning/object lock/MFA/delete markers are **DU**. Deleting a key in a versioned bucket need not purge earlier versions. Do not add DeleteObjectVersion permission or purge historical versions without separate retention review. Expired upload grace and re-sweep are needed to prevent a still-valid PUT recreating an object after cleanup.

## 10. Database transaction-boundary issues

**SC S3-10 Medium (A09):** ModelRequestService.confirmRequest:161/167/191 holds transaction and pessimistic dish lock across up to five S3 HEADs. approveModel:284/297 holds a transaction across HEAD. cleanupReferencePhotos:448/459 holds a transaction across DELETE; current code even signs reference GETs while preparing cleanup, though it only needs keys. Slow AWS consumes pooled connections and extends lock occupancy. Approval uses ordinary entity loading rather than the dish lock used in request submission; @Version detects some conflicts but does not prove idempotent history/accounting.

Approval's afterCommit callback deletes the old model (`ModelRequestService:362–371`). Data is committed, but synchronous afterCommit callbacks may still retain transaction-associated resources until Spring cleanup; afterCommit is not equivalent to a durable detached worker. History GET mapping only signs locally; do not falsely classify it as a download while a DB transaction is open.

Reuse MediaService/MediaReplacementService/DishService pattern: initial scoped read → HEAD outside transaction → short transaction reacquiring dish/account policy state and validating issued-key/pending/status/version again → atomic publication/history/usage/cleanup intent → detached cleanup. Recheck current account restrictions after external latency. Define simultaneous-confirm outcomes as one transition or replay of same result, with no duplicate history/usage. Test actual transaction state at fake network boundary and real PostgreSQL locking; do not merely delete @Transactional annotations.

## 11. Caching and privacy

**SC S3-11 High privacy-design risk / RU actual cache exposure (A13):** `S3Storage:49,115–121` applies `public, max-age=31536000, immutable` to every PUT, including Q. This does not make a private S3 object anonymously readable, but authorizes long-lived caching of an authorized response. Signature expiry alone does not erase a browser/proxy copy. No shared-cache/CDN behavior was tested.

Propose explicit public/private cache class at signing: published immutable public keys retain long-cache performance; private reference PUT metadata uses `private, no-store` and signed GET response overrides supply equivalent no-store behavior as defense in depth. Existing objects keep old metadata unless separately migrated/replaced; changing future signing alone does not fix them. Inspect approved nonproduction response headers/caches before planning a bounded existing-object metadata correction. No purge/copy/invalidation here. Consider draft/pending objects separately from finalized publication, without moving all upload bytes through Java.

## 12. Multi-tenant isolation review

**PF:** Tenant comes from verified JWT claims, then current RestaurantAccess checks; scoped dish/key/category finders remain. AdminController validates current admin account/version before reads/presign/approval. Public DishMapper.toGuestDto:74 omits internal keys, ETag, request notes/status and staff metadata. These are controls to preserve, not proof of all new paths being safe.

Remaining **SC** limitations: ModelRequestService scalar-ID methods rely on guarded callers; signing history trusts stored key metadata; admin final-key/branding confirmation is prefix-based rather than operation-bound; app IAM covers every tenant. Approval logs AdminPrincipal email but does not persist complete uploader/origin/version lineage or a dedicated immutable model-publication audit record. A passed principal/log line is not durable actor provenance. `hardDelete` does persist a deletion audit, but its queued-object count is not confirmed purge.

Suspension/deletion checks block new owner issuance, but a previously issued PUT/GET bearer URL is not bound to subsequent JWT revocation. Revoking a restaurant does not automatically revoke the signer’s entire IAM identity or public known media URLs. Define bounded residual URL lifetime and confirmation rejection on revoked account. No cross-tenant exploit or live private exposure was exercised.

## 13. Future model-management implications

**FR:** Keep compatibility with existing slug-based keys; do not rename/move live objects in a planning task. For new assets, recommend immutable tenant ID, dish ID, opaque asset/version ID and purpose in server-built keys; store original key/bucket on each asset record. Slug edits must not redefine historical ownership. A staging/private purpose may be justified for validated publication, but requires explicit migration/cost review; retain browser-to-S3 transfer.

Introduce only the necessary issued-upload/version records when approved: tenant/dish, origin RESTAURANT_UPLOAD/CONCIERGE/ADMIN_DIRECT/LEGACY_UNKNOWN, actor kind/ID, optional request ID, expected MIME/bytes, expiry, confirmation state/checksum and immutable object metadata. Backfill only known facts. Current admin service already supports no-active-request publication; extend its explicit targeting/audit rather than duplicate a new pipeline.

Do not delete prior model files merely on replacement once retained history ships. Define current, superseded, retention-held, purge-pending and purged/unavailable states. Track active model slots separately from physical retained/pending bytes; reserve temporary upload bytes and release exactly once. Entitlement overrides may adjust business caps but never weaken private access, ownership or mandatory audit. Existing usage counters omit some private/unconfirmed/historical objects; quotas cannot rely on them without reconciliation. Model history, application versions and S3 bucket versioning are different mechanisms.

## 14. Safe policy-change proposals

Illustrative JSON only, not deployed or written to infra files. Same-account app role is assumed; cross-account trust, boundaries, organization controls and KMS need separate review. Replace placeholders only in an approved deployment task.

App identity policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ManagedObjectOperations",
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": [
        "arn:aws:s3:::<BUCKET>/<PUBLIC_ROOT>/*",
        "arn:aws:s3:::<BUCKET>/<PRIVATE_ROOT>/*"
      ]
    },
    {
      "Sid": "ManagedPrefixListing",
      "Effect": "Allow",
      "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::<BUCKET>",
      "Condition": {"StringLike": {"s3:prefix": ["<PUBLIC_ROOT>/*", "<PRIVATE_ROOT>/*"]}}
    }
  ]
}
```

No s3:* or bucket-admin/ACL/version-deletion rights. Prefix wildcard permits generated tenant/object suffixes, not arbitrary bucket objects. List private only if the approved cleanup worker actually needs it; current missing-private cleanup must not be presented as implemented merely because IAM grants listing. Prefer a managed role/default chain over adding credentials. Split deletion/listing into a worker role later only if operational need warrants it.

Minimal public-read bucket statement:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "IntentionallyPublicMenuObjects",
    "Effect": "Allow",
    "Principal": "*",
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::<BUCKET>/<PUBLIC_ROOT>/*"
  }]
}
```

Remove the blanket private GetObject Deny from the example only after reviewing other applicable grants. No private Allow is added. Under a verified same-account model with no other grants/ACLs, default deny keeps anonymous Q reads denied while the app's IAM allow supports signed operations. If an explicit private guardrail is required, design an exception for a narrowly identified `<ACCOUNT_ID>`/`<APP_ROLE>` principal ARN and approved operational roles; test role sessions, service principals and cross-account behavior. Do not paste an untested Deny-exception policy or a wildcard NotPrincipal pattern into production.

Block Public Access can prevent intended anonymous P access even with an Allow; account/organization/bucket/access-point settings combine restrictively. Keep ACLs disabled/blocked where compatible; do not disable all public-access controls just to silence 403s. If policy-public access is prohibited, an approved CDN/OAC or other serving model is a separate architecture task, not a silent change here. [AWS BPA guide](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html) describes these interactions. TLS-only and encryption restrictions may be desirable but must be reviewed against current clients/service principals before addition.

## 15. Proposed source-code improvements

Do not implement in 1B. Small coherent slices: result-bearing S3 deletion API and migrated callers; minimal durable cleanup intent with retention eligibility; explicit private cache class; issued-upload binding for branding/admin outputs; common confirmation checks without replacing domain services; bounded private pending expiry; short concierge transactions; after-commit cache invalidation and durable detached cleanup. Preserve existing API/error shapes, browser signed headers and old-live-until-confirm semantics.

Avoid a broad upload rewrite. Actual binary inspection/finalization deserves its own scope: bounded decoding/GLB validation outside long DB transactions, trusted checksums as appropriate, safe external glTF resource handling and immutable publication. ETag comparison alone does not solve reusable PUT overwrite. Do not route large multipart HTTP bodies through Spring Boot or introduce a paid scanner/worker provider without approval.

## 16. Testing and disposable-environment requirements

No new test was written or executed in this review. Existing service tests mock AWS and much persistence. `ModelRequestServiceTest.bucketPolicyExcludesPrivatePrefix:331` cannot establish correct IAM evaluation or successful authenticated private GET simply by inspecting policy text. Preserve existing regression tests and add tests that fail for the actual defect.

| Layer | Proposed cases | Objective evidence |
|---|---|---|
| Local unit | Public/private key construction; foreign/unissued/expired/wrong-operation keys; signed headers/TTL; absent metadata; invalid MIME/size; ETag variants; JSON errors | Rejection before mutation/signing; no secrets/URLs in output; public/private cache choice explicit |
| Delete adapter | Mixed DeleteObjects errors, all success, transport timeout, 1001 keys, duplicates/absent keys, list failure/pagination | Per-key outcomes truthful; unknown not success; bounded batches; only failures retried |
| Domain integration | Failed cleanup after replacement/remove/hard delete, crash/restart window, duplicate workers and late PUT | Durable retry evidence survives; current/retained assets never targeted; settlement once |
| Transaction integration | Fake network callback asserts no active transaction/lock; race approve/confirm/reaper/suspend | Old live asset survives failure; publication/history/usage consistent; no blind annotation removal |
| Disposable PostgreSQL | V13 upgrade fixtures/new migrations if separately approved, optimistic/pessimistic conflicts and active-request partial unique index | One allowed transition; no double usage/history; transaction rollback verified on real vendor |
| Disposable AWS | Authorized private PUT/HEAD/GET; anonymous Q GET denied; intended public P GET; unrelated-prefix/list denied; expired URL; exact headers/CORS; private cache | Observe real status/headers using synthetic data, no URLs/credentials in reports |

Prefer a dedicated nonproduction bucket: same-bucket isolated prefixes do not isolate account BPA/bucket-policy changes, so prefix-only tests must not change shared production bucket policy. Owner must explicitly approve account, region, bucket, temporary role/principal, public-read test need, synthetic prefix, cleanup authorization/limits, budget and retention. Test identity needs only object PUT/GET/DELETE under test P/Q plus scoped ListBucket if required; no IAM/bucket-policy administrator permission. An authorized operator applies reviewed test policies separately. Use two tenants and synthetic small photos/GLBs. Establish a deny/out-of-scope negative test without touching other objects. Never enumerate production assets for convenience.

Use failure injection/local stubs for per-key partial failures first; validate in AWS using deliberately denied synthetic keys only after separate approval. Treat timeout outcome as unknown; reconcile after retry. Record sanitized response status/error class and test IDs, never credential providers, full signed URLs or private images. Browser CORS and private cache checks need an approved HTTPS test origin/device; a CLI GET cannot prove browser behavior.

## 17. Storage, cost and performance

No AWS prices, verified production costs or capacity claim. Signing itself has no S3 API call, but default-provider credential retrieval and subsequent PUT/GET/HEAD/LIST/DELETE have operational effects. Confirmation HEADs, repeated history GETs, retry storms, orphan storage, retained versions and large GLBs can dominate requests/storage/egress. Long transactions increase pool occupancy and lock waits independently of S3 bills.

Measure object counts/bytes by purpose/state/age, retained versions, pending expiry, deletion failure age, request/egress rates, HEAD latency/retries, model transfer sizes and cache-hit rates. Inspect versioning/delete-marker/object-lock behavior before estimating physical purge or cost. Bound list/delete memory, page size and worker concurrency; do not load an entire large tenant prefix before batching indefinitely. Keep public immutable caching for truly immutable finalized keys; private no-store trades repeat transfer for confidentiality. Retention policy must acknowledge this tradeoff.

Operational questions: which cleanup remains failed and how old; did permission change break authorized private access; are S3 latency/lock waits exhausting the pool; are storage counters drifting? Proposed signals: cleanup requested/acknowledged/failed/unknown totals, pending age/backlog, attempts/next retry, S3 latency/error histograms, bounded operation/status labels and pool/lock metrics. Correlate durable task/actor IDs in access-controlled structured logs; no signed URLs, raw reference JSON or credentials. Do not label operational cleanup metrics as restaurant engagement analytics. No telemetry backend/dependency is installed here.

## 18. Prioritized implementation roadmap

Each task is separately approvable. A policy example correction is not permission to apply it to AWS.

| Task / finding / priority | Scope, root cause, risk and affected files | Dependencies / tests / acceptance | Deployment and rollback |
|---|---|---|---|
| B1 — S3-01/02/03, High, first | Correct only policy templates and safe deployment instructions: missing Q permissions + blanket Deny; preserve anonymous Q denial | Review actual intended principal/topology privately; JSON/static semantic tests, negative anonymous/private tests in separately approved bucket | Commit example changes only after approval; no live apply; source rollback restores examples, never restore a known-bad live Deny blindly |
| B2 — S3-11, High privacy risk | Explicit private cache class in S3Storage and reference callers; prevent new private immutable metadata | Confirm required signed browser headers; test PUT/GET headers and public cache unchanged; existing-object remediation separately planned | Roll out for new objects; forward-fix privacy regressions; do not roll back to cacheable private responses or claim old objects fixed |
| B3 — S3-07, High | Result-bearing delete API + all callers; stop reporting submitted as deleted in S3Storage/StorageJanitor/ModelRequestService | Unit mixed-error/timeout/batch tests; callers preserve failed keys and distinguish unknown | No schema required for adapter contract itself; deploy compatible callers together; rollback code only with retained failure evidence |
| B4 — S3-08/09, High/Medium | Durable bounded cleanup manifests/retries; current references disappear before purge; AdminService/MediaService/BrandingService/reaper/janitor and approved additive schema | B3; retention/tenant-deletion policy; SQL crash/restart/idempotency tests, fixture cascades/aggregate orphans | New Flyway migration only if approved; dry-run candidates first; pause worker on error, retain manifests; no destructive down-migration |
| B5 — S3-10, Medium | Concierge HEAD/delete outside DB transactions; preserve locks/versions/revalidation in ModelRequestService | B3/B4 for deletion; real PostgreSQL confirmation/request races; network callback asserts detached boundary | Small endpoints separately; monitor pool/latency/conflicts; rollback app-compatible flow without discarding pending work |
| B6 — S3-04/05/06, Medium | Bind issued branding/admin keys, unify metadata checks, explicit pending expiry; lifecycle ownership/replay gaps | Define schema/binary guarantees, TTL/grace and immutable publish strategy; foreign/replay/concurrent/reaper tests | Additive issued records and compatibility for in-flight URLs; drain or honor old issuance; do not mass-delete untracked prefixes |
| B7 — FR model history/quotas | Immutable tenant/asset/version provenance and retention-accounting policy, no automatic replacement purge | Product decisions, B4/B6, entitlements design; old-version availability and quota races on PostgreSQL | Legacy-aware backfill and staged read cutover; preserve objects/metadata on rollback; no retroactive invented history |

B1/B2 can be designed together; B3 must precede acknowledgement-dependent cleanup fixes. Do not release broad private permissions without the cache/privacy and ownership review. If a real leak or production outage is later evidenced, reassess priority; none was established here.

## 19. Acceptance criteria

1. B1: templates parse; only intended P anonymous GET is allowed, Q anonymous reads denied, correct app private PUT/HEAD/GET/delete works and unrelated paths/listing are denied in the approved test environment. Existing explicit-Deny/BPA/key-policy interactions are documented.
2. B2: new private uploads/downloads emit no-store behavior; public finalized immutable responses retain intended caching; existing-object metadata migration is explicitly bounded or left pending, never falsely closed.
3. B3/B4: injected per-key/transport failures never produce full-success counts or erase retry evidence; restart and duplicate execution preserve convergence; object deletion and storage settlement are distinct, idempotent transitions.
4. B5: S3 network boundaries observe no open operation transaction; account/dish/issued-key/status is revalidated after HEAD; two confirmations produce one publication/usage/history transition or documented replay/conflict.
5. B6: foreign/unissued/stale/wrong-kind keys reject; legitimate retries preserve old-live media until successful confirm; late PUT/reaper race has bounded grace/reconciliation; metadata guarantees are distinguished from binary guarantees.
6. B7: superseded model objects survive while retained; purge requires retention eligibility and no active references; tenant/actor/origin lineage is real, and quota counters account for pending/retained bytes without duplicates.
7. Every implementation preserves A01–A03/1A tests, API compatibility and direct uploads, runs appropriate backend/frontend checks, and reports PostgreSQL/AWS/browser blocks explicitly. A passed mocked suite is insufficient for policy semantics or S3 delivery/privacy claims.

## 20. Unresolved questions, limitations and approval requirements

- Which nonproduction app role/account, bucket/region, BPA/ACL/object ownership, encryption/KMS, versioning/object lock, CDN/origin and deployed policy combination is intended? All remain **DU**; no AWS account, endpoint, credentials or object was accessed.
- Is public prefix visibility of pending bytes acceptable, or should validated staged publication be separately scoped? Do not silently change printed/public media contracts.
- What private-reference/historical-model retention and purge guarantees are approved, including rejected/superseded requests, prior slugs, backups and deletion holds? Current 14-day current-dish cleanup is not a complete answer.
- Is hard deletion allowed before physical cleanup completes, or should a tombstone/manifest remain? Who operates failed-permission tasks, and what is the acknowledgement contract?
- What binary validation/checksum/finalization guarantee, storage accounting scope and pending reservation/grace policy is required? Preserve SDK/browser header compatibility and pinned SDK until reviewed.
- PostgreSQL and AWS disposable environments require explicit safe targets; none was used. No performance benchmark, live IAM simulator run, optical/model/device check or new runtime test was executed.

Security and code-review guidance were applied report-only, with observability guidance for truthful cleanup/failure evidence and local compatibility references. No mutation experiment or third-party helper was invoked. Findings are source/template observations plus clearly labeled engineering proposals, not proof of production vulnerability or current policy deployment. Official AWS documentation was consulted through public documentation pages only; this does not contact the user's AWS services.

**First proposed approval: B1 — correct the example private-access policy contradiction and deployment instructions, with local JSON/semantic regression tests only.** Scope: reviewed `infra/iam-policy.json`, `infra/s3-public-read-policy.json`, directly relevant deployment documentation/tests; no AWS application, credentials or cloud mutation. Before that implementation, approve the precise role/prefix assumptions and policy semantics. Authorized disposable-bucket verification, cache changes and all later source/schema work remain separately scoped approvals.
