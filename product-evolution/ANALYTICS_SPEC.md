# Analytics architecture and metric dictionary

Status: current pipeline inspected; correctness repairs and new metrics proposed. [Index](README.md).

## Verified source pipeline and defects

[GuestMenuPage](../frontend/src/pages/GuestMenuPage.jsx) emits MENU_VIEW without dishId. [DishSheet](../frontend/src/components/guest/DishSheet.jsx) emits MODEL_3D_VIEW when the user chooses 3D and AR_LAUNCH when requesting AR. [AnalyticsEventDto](../backend/src/main/java/com/atelier/media/dto/AnalyticsEventDto.java) accepts slug, optional dishId and eventType; [AnalyticsService](../backend/src/main/java/com/atelier/media/service/AnalyticsService.java) validates type/restaurant/dish relationship, limits IP traffic to 60/minute and deduplicates IP+dish+event+restaurant for 30 seconds in process memory. Unknown IPs share the same dedup namespace. This is not unique visitor measurement.

Writes are dispatched to an unmanaged virtual-thread executor/ignored CompletableFuture. One TransactionTemplate callback increments counters, saves raw event and performs read-increment-save daily aggregation using processing-day UTC. The daily table has no optimistic version/upsert protection; commit-time failure may escape the inner catch without an observed result. Studio summaries use cumulative counters/top dishes; platform summaries also count raw rows, not a unified daily read model. A08/A15/A19 remain open. Dish views increment only on MENU_VIEW with dishId, which the current guest UI does not emit. No live database/event correctness was established by source inspection or mocked service tests.

## Authoritative metric dictionary

All new client events are bounded, untrusted observations. Count only validated/deduplicated accepted events under a documented schema version; do not retroactively relabel legacy data.

| Metric/event | Definition and producer | Dedup/denominator and limitation |
|---|---|---|
| Legacy menu views / MENU_VIEW | Current guest load event accepted by server | Existing 30-second IP key; label legacy views, not people |
| Menu visits / MENU_VISIT | Proposed successful public menu display with new session ID | One per tenant/session/entry visit; refresh definition explicit |
| Unique browser visitors | Proposed consent/policy-approved pseudonymous browser ID in selected range | Distinct IDs; not unique people, excludes unidentified traffic; disabled until privacy decision |
| Dish detail views / DISH_DETAIL_VIEW | Detail actually opens for tenant-owned public dish | One per session/dish/defined short window; independent of 3D |
| Category engagement / CATEGORY_SELECTED | Guest explicitly selects category | Session/category/window; not every scroll observation |
| Legacy MODEL_3D_VIEW | Current 3D-control click | Intent, not verified rendered model |
| Model views / MODEL_VIEW_READY | Proposed model ready and visible after user action | Session/dish/view attempt ID; exclude hidden preload |
| AR launch attempts / AR_LAUNCH | User requests AR | Session/dish/attempt ID; neither successful placement nor duration |
| Mode distribution | MENU_VISIT grouped by server-resolved effective mode | Known-mode visits denominator; legacy unknown separate |
| Device category | Coarse mobile/tablet/desktop if approved | Minimize UA data; no fingerprinting; unknown bucket |
| CART_ADD / CART_VIEW | Proposed guest cart actions | Session/cart revision/action ID; quantity vs action counts separate |
| Requests prepared / SNAPSHOT_CREATED | Server committed validated snapshot | Unique request ID; authoritative preparation, no send |
| IMAGE_GENERATED | Browser reports Blob generation | Request/render revision; client-reported |
| SHARE_FLOW_OPENED | Native share invocation/open-chat attempt | Attempt ID and native/manual channel; no guaranteed external open |
| SHARE_REPORTED_COMPLETED | Native promise resolved | Share attempt; handoff only, never delivery |
| Delivery confirmed | Verified authorized integration status | Provider message/status dedup; unavailable in guest-only workflow |
| Restaurant acknowledged | Authenticated accepted/rejected action | Unique request/ack revision; unavailable until workflow approved |
| Venue/table QR entries | Entry with source hint or validated table token | Session entry; context can be copied and is not a physical scan |
| General/unknown entry | No usable source context | Existing plain venue URLs cannot establish physical scan |
| WAITER_CHAT_OPENED | Guest initiates waiter chat link | Attempt/cooldown; no waiter response confirmation |
| REVIEW_LINK_OPENED | Guest invokes configured review destination | Session/action; not a posted Google review |

Funnel reports distinct requests/sessions at each defined step, with the same cohort/time window. Show native/manual branch separately; manual sends have no reliable completion signal. Show request→image→share attempt and optional handoff rate; never sales/revenue/conversion-to-paid. Engagement ratio uses visible model-view sessions divided by eligible menu sessions, not all-time model clicks divided by unrelated visits. Existing cumulative “engagement rate” may exceed 100%; relabel before presenting as a visitor conversion percentage.

## Proposed durable processing

Keep the modular monolith and existing tables initially. Add schemaVersion, eventId, server receivedAt, bounded client occurredAt, resolved tenant/dish, session and optional request/context/mode fields as approved. Validate public visibility and relevant feature; server resolves mode/context, never trusts claimed tenant identity or provider delivery events. Browser event authenticity cannot be guaranteed merely with origin headers or a signed context: label exposure and rate-limit abuse.

Recommend a short synchronous database transaction for bounded event ingestion initially: atomically claim event ID, insert event and PostgreSQL aggregate upsert/counters, then acknowledge durable acceptance. This removes hidden async loss without adding a queue provider. Measure latency/throughput first; use an approved durable outbox/worker only if needed. Duplicates must not increment counters. PostgreSQL [INSERT ON CONFLICT](https://www.postgresql.org/docs/current/sql-insert.html) supports atomic conflict handling; real DB concurrency tests must verify the chosen query. No database SQL is executed in Phase 0.

Use receivedAt as authoritative initial bucketing time; record client time only as untrusted metadata with bounds. Store UTC timestamps; date filters use an approved tenant IANA zone and half-open boundaries converted to UTC. Start reporting in UTC if tenant zones are not yet available. Daily UTC aggregates cannot answer arbitrary local-day ranges exactly without raw events/hourly buckets; choose UTC-only first or add hourly buckets after benchmark. Never silently combine processing day and event time. Version metric definitions and mark cutovers; historical dish views/QR/unique visitor data cannot be reconstructed reliably.

Recommend raw retention 90 days and non-identifying aggregates 13 months for review. Guest notes/phone/token values never enter events. Store no raw IP for reporting; use bounded ephemeral abuse counters and approved pseudonymous session IDs, without fingerprinting. Preview/demo/admin traffic is excluded or separately flagged. Bot filtering is heuristic and documented, not a guarantee. Retention jobs are batched/observable; tenant deletion includes aggregates (existing V10 lacks FK). Reconcile raw accepted rows and aggregates with fixture counts before choosing aggregate dashboards as authoritative.

## Dashboards, dependencies and decisions

Owner: date/range/zone, freshness/partial-data state, menu/dish/category trends, model/AR attempt engagement, source breakdown, request-sharing funnel, explicit comparisons and metric help. Empty/missing data is not zero; insufficient comparisons show no percentage. Platform: restaurant/resource counts, platform engagement totals and separate operational ingestion/failure/latency metrics. Tenant requests are scoped even when widget feature is hidden; advanced analytics grants no cross-tenant data.

Dependencies: A08/A19 fixes, public contract repair, explicit consent/session/retention/timezone choices and table/order lifecycle. Open decisions: synchronous latency budget, hourly aggregation need, identifier consent, period retention and retroactive data repair. Current deployed PostgreSQL version/query plans are unknown; benchmark against the approved target.

## Acceptance and test scenarios

Trace emitted request → validated row → dedup claim → atomic counters/buckets → API → widget label using deterministic fixtures. Test N concurrent increments exactly N, duplicate IDs once, failure rollback/observed errors, foreign/nonpublic dish rejection, unknown IP behavior, browser retries, bot/preview exclusions and tenant deletion. Test midnight UTC, DST/local ranges, delayed client times, zero denominator, sparse cohorts and legacy cutover. Verify no chart claims physical scans, unique people, successful AR or message delivery without evidence. Existing analytics unit tests do not substitute for this PostgreSQL/browser verification.
