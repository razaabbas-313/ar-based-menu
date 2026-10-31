# WhatsApp ordering and waiter requests

Status: proposed; no ordering or messaging provider activated. [Index](README.md).

## Current source

[SocialBar](../frontend/src/components/guest/SocialBar.jsx) and [format helpers](../frontend/src/utils/format.js) construct a WhatsApp contact/reservation link. [Restaurant](../backend/src/main/java/com/atelier/media/model/Restaurant.java) already stores a WhatsApp number. There is no cart, image order snapshot, delivery acknowledgement or waiter request workflow. Reuse [API client](../frontend/src/api/client.js), existing guest sheets and server DTO/service patterns.

## Proposed cart and validation

No guest login. Keep a bounded cart per restaurant and resolved table identity in sessionStorage, with a proposed two-hour inactivity expiry. Never merge carts across restaurants/tables. Explain reset on context changes. Suggested limits for review: 50 distinct dishes, quantities 1–99, 500 guest-note characters and 200 per-item-note characters. Use integer quantities and backend decimal currency arithmetic; no floating-point authoritative totals. Exclude unpriced items from cart until a price or explicit quote-only policy is approved.

On review completion, submit dish IDs, quantities, bounded notes, optional table token and one idempotency key retained for that cart revision. The server resolves the restaurant from the public route, validates visibility/order entitlement and allowed venue/table channel, resolves table ownership/activity, loads all dishes scoped to that restaurant, rejects unavailable/incomplete dishes and calculates prices itself. Never accept client restaurant IDs, line totals or table display labels as authority. Return changed-item errors requiring guest review; do not silently change totals.

Recommend a small persistent `order_requests` snapshot with immutable lines, currency, server time, restaurant/table display snapshots, safe branding revision, recipient configuration version and an opaque reference. Its purpose is reproducible images, duplicate prevention and honest attempt records, not checkout. A proposed 30-day request/notes retention and two-hour read capability need approval; public bearer read tokens are stored hashed, returned once and never placed in analytics/logs. No public order listing/search. Idempotency is unique per restaurant+key, bound to a canonical payload hash; same body replays the result, different body returns conflict, in-flight duplicate returns bounded retry/conflict. Retain key tombstones at least as long as the documented retry window; reject reuse after snapshot expiry rather than creating an unintended duplicate. Revalidation after entitlement/contact changes can require regeneration.

## Image preparation

Render locally from the server snapshot; do not persist image binaries by default. Use Canvas/Blob and existing approved assets, await font readiness, then expose a separate user-triggered share button so asynchronous preparation does not lose transient activation. Proposed 1080px width, dark text on light panel, logo/name, reference, restaurant-local date/time with zone, validated table label, wrapped item names/notes, quantity × unit price, line totals, estimated total and a prominent “Order request — awaiting restaurant confirmation.” There is no paid/accepted stamp.

Measure text before drawing; bound canvas height and paginate oversized carts with reference and page numbers repeated. Never truncate items silently. Omit dish thumbnails initially. Fall back to text/placeholder logo on CORS or decode errors; a remote image must not taint the canvas. Provide a semantic HTML summary for accessibility and text copying. Preview every page and allow regeneration. Multi-file share support is independently checked; otherwise offer downloads per page or existing ZIP capability with clear manual attachment instructions. File sizes and render time must be measured on low-memory phones before final limits are set.

## Guest sharing and event evidence

Use HTTPS, `navigator.canShare({files})` and `navigator.share` when supported. Share-target and recipient selection belong to the guest/device; the browser cannot force WhatsApp or the configured restaurant. Show/copy the restaurant number next to the native-share action and ask the guest to choose that contact. A resolved promise means handoff reported by the browser, not WhatsApp delivery. Cancellation remains cancellation; errors preserve the image and expose fallback. [W3C Web Share](https://www.w3.org/TR/web-share/) defines this handoff boundary.

Fallback: download image → open restaurant chat → attach image manually → send in WhatsApp. A wa.me URL can contain an international-format recipient and URL-encoded text, including reference and a bounded summary; it cannot attach the PNG. Cap composed text and fall back to reference plus manual image attachment for large carts. [WhatsApp click-to-chat documentation](https://faq.whatsapp.com/5913398998672934) documents the recipient/text contract.

| Evidence | Stored/visible meaning | Never infer |
|---|---|---|
| CART_PREPARED | Browser cart exists; not a durable restaurant order | Image or delivery |
| SNAPSHOT_CREATED | Server validated and recorded a request | Browser rendered it |
| IMAGE_GENERATED | Browser reports successful Blob creation | Share or send |
| SHARE_FLOW_OPENED | Browser attempted native share/open-chat action; record channel | Recipient, delivery or acceptance |
| SHARE_REPORTED_COMPLETED | Browser share promise resolved; evidence remains client-reported | Message sent/received |
| SHARE_CANCELLED / SHARE_FAILED | Browser reported cancellation/error | Restaurant rejection |
| DELIVERY_CONFIRMED | Reserved for independently verified authorized provider evidence | Available in guest-only MVP |
| ACCEPTED / REJECTED | Reserved for authenticated restaurant acknowledgement workflow | Available without a real workflow |

Store share attempts as append-only bounded observations separate from immutable snapshot state. Repeated attempts never promote evidence to delivery. An order-request dashboard, if approved, labels these as prepared requests/share reports. Analytics events are untrusted engagement evidence; server snapshot creation is durable evidence of validation only.

## Restaurant controls and number verification

Separate effective ordering entitlement, owner enable switch, venue/table channel rules, waiter switch and business copy. Normalize international numbers and validate syntax on the backend. A “test chat” plus owner acknowledgement is **owner-confirmed**, not proof that Atelier verified ownership or WhatsApp registration. Automated verification requires an approved provider/challenge flow; never add a false “verified” badge. Disable sharing if no usable destination; confirm contact changes, audit them and warn owners about outdated exported images.

Waiter action previews “Hello {restaurant}, please assist {validated table label}.” Unknown venue context uses a neutral message or is disabled when configured table-only. Guest explicitly opens chat and sends; show “WhatsApp opened; send your message there.” Proposed 60-second local cooldown and bounded server event recording add friction, not guaranteed prevention of repeated messages outside Atelier. No unsolicited messages, browser automation or waiter-acceptance claim.

## Optional official automated delivery

A separately approved integration could send media through an authorized WhatsApp Business Platform sender; it cannot impersonate the guest. Business account/number onboarding, recipient permission/opt-in, sender identity, templates/customer-service-window rules, current pricing, media hosting/privacy and webhook authentication/idempotency require validation first. Business-initiated messages generally require approved templates, with different rules inside the customer service window; costs and restrictions must be reviewed at implementation time. [WhatsApp Business policy](https://business.whatsapp.com/policy) governs messaging; [official developer hub](https://whatsappbusiness.com/developers/developer-hub/) links technical setup. Meta’s send-message guide returned HTTP 429 during this research, so no current API-version/media-limit guarantee is made. No paid provider or credentials are selected.

## Dependencies, security and acceptance

Requires entitlements, validated table identities, menu/privacy repair, request retention approval and analytics event contracts. Notes/images may contain personal information: no guest phone collection required, no public S3 receipt bucket, no note text in logs/events. Rate-limit anonymous snapshot creation/read attempts; references are non-enumerable but never sufficient alone to read private notes. Server validation cannot prevent users editing an exported image; label it a request and do not treat it as trusted acceptance evidence.

Test foreign/missing dish and table IDs, disabled channel, malformed number, price/availability changes, decimal rounding, duplicate retries, payload changes, concurrent creation and expired reads. Test long/unicode names, RTL text, missing fonts/logo, huge carts/pagination, CORS failures and memory constraints. Test native share success/cancel/error, no file sharing, wrong/unavailable share targets, popup blocking, no WhatsApp and desktop manual attachment. Verify no action reports “sent” merely because a browser flow opened. No message is sent during Phase 0.
