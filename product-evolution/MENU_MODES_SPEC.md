# Menu modes specification

Status: proposed restaurant-level policy. [Index](README.md).

## Current behavior and extension points

[PublicMenuDto](../backend/src/main/java/com/atelier/media/dto/PublicMenuDto.java), [PublicMenuService](../backend/src/main/java/com/atelier/media/service/PublicMenuService.java), [DishMapper](../backend/src/main/java/com/atelier/media/service/DishMapper.java), [GuestMenuPage](../frontend/src/pages/GuestMenuPage.jsx) and [DishSheet](../frontend/src/components/guest/DishSheet.jsx) provide one shared menu with optional per-dish models/AR. There is no current plan-driven three-mode selector. `canUpload3d` controls restaurant uploading; it is not a complete viewing-mode entitlement.

## Proposed contract

Exactly three values: `SIMPLE`, `THREE_D`, `AR`. A validated restaurant preference selects one value from effective allowed modes. No separate applications or duplicated menu records.

| Effective mode | Photos/details/search/categories | Interactive model | AR launch |
|---|---|---|---|
| SIMPLE | Yes | No viewer initialization or model preload | Never |
| THREE_D | Yes | When valid model exists; rotation/zoom/progress/retry | Never, including hidden model-viewer activation UI |
| AR | Yes | Same fallback-capable viewer | Supported devices only; unsupported guidance and photo/3D fallback |

Allowed modes are an enum set, not a numeric plan rank. AR uses 3D infrastructure but a plan can choose its owner-selectable set explicitly. SIMPLE is the recommended mandatory safe fallback. Global hard disable of 3D/AR masks those capabilities while preserving browsing. Feature dependencies are validated centrally in [entitlements](PLANS_AND_ENTITLEMENTS_SPEC.md).

Resolve in the backend: load current account/publication state; compute effective capabilities; retain selected mode if allowed, otherwise use deterministic safe fallback; publish effective mode and safe action flags. Proposed public DTO additions are additive. SIMPLE responses should omit usable model links/metadata that are unnecessary for the selected experience; THREE_D should not publish an AR-launch capability. Already public model URLs cannot be made confidential by hiding controls.

Owner settings expose allowed modes with inherited/disabled explanations; the backend rejects unsupported writes regardless of UI. A downgrade preserves stored preference/history but uses SIMPLE when the selected mode is no longer allowed, with owner notification. Do not delete models. Explicitly approve the legacy mapping before migration; recommend preserving existing guest model/AR behavior through a legacy assignment instead of forcing all current restaurants to SIMPLE.

## Boundaries, dependencies and decisions

Depends on effective entitlements, cache invalidation and PD01 repair. Ordering, waiter requests and review links are independent flags; no mode implies them. Physical AR scale/compatibility remains device-tested, not guaranteed by enum selection. Migration/default policy and treatment of disabled plan features require approval.

## Acceptance and test scenarios

Test allowed/disallowed selector values, tampered requests, expired overrides and plan changes. Verify SIMPLE produces no viewer download/control, THREE_D cannot initiate AR through any UI path and AR falls back without a model/device. Preserve photos/cart in every mode. Confirm mode changes invalidate public caches after commit and stale clients cannot obtain disabled new server actions. Test legacy venue QR, preview/private states and public DTO field privacy. Measure the payload difference between modes; do not claim performance gains before execution.
