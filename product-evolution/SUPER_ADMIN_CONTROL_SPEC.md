# Super-admin control center

Status: proposed capability expansion. [Index](README.md).

## Current source

[AdminPage](../frontend/src/pages/AdminPage.jsx), [RestaurantDrawer](../frontend/src/components/admin/RestaurantDrawer.jsx), [RestaurantFormModal](../frontend/src/components/admin/RestaurantFormModal.jsx), [AdminController](../backend/src/main/java/com/atelier/media/web/AdminController.java), [AdminService](../backend/src/main/java/com/atelier/media/service/AdminService.java) and [AdminAccess](../backend/src/main/java/com/atelier/media/service/AdminAccess.java) implement provisioning, status/notes, direct-model boolean permission, credential reset, preview, support sessions and model desk operations. Current-account/version validation is now present; preserve it. No configurable plan registry or general entitlement UI exists.

## Proposed control model

During provisioning, choose restaurant identity and immutable plan revision, then permitted overrides. Present effective modes, ordering/waiter/venue/table QR, dish/table/model/storage/request quotas, customization, analytics and review capability before saving. Start safe and explain inherited/default/overridden values. No subscription charge or collection step.

Restaurant detail provides searchable configuration sections, actual usage, over-limit warnings, current plan revision, overrides with expiry/reason and an audit timeline. Plan editing is a separate workflow: draft → validate dependencies → compare affected configurations → publish revision → explicitly migrate selected restaurants. Disabling a template prevents new assignments; it does not erase existing configurations.

Only registered typed keys appear. Numeric controls enforce units/ranges, explicit zero versus unlimited semantics, hard ceilings and dependencies. Overrides show previous/new values, actor, reason, expiry, source and resulting behavior. Use optimistic configuration versions to reject lost updates. Preview impact before applying bulk plan changes; high-impact operations use concrete confirmation naming affected restaurants/features. Avoid blanket “override all” switches.

Admin direct model upload explicitly selects restaurant then its dish and calls the shared secured upload lifecycle. Concierge operations remain human controlled, with history, rejection reasons and safe private references. Operator dashboards separate platform provisioning/resource metrics from restaurant engagement and operational errors. Guest share attempts are never restaurant-accepted orders.

## Security boundaries

Every new admin controller must validate current admin existence/role/password usability/token version, not merely parse an audit actor. A central validated caller then checks the restaurant/dish relationship and records actor identity. Reuse [AdminAuditLog](../backend/src/main/java/com/atelier/media/model/AdminAuditLog.java); persist business mutation and audit evidence atomically. Do not log secrets, OTPs, signed URLs or guest notes. Support sessions and actor provenance must remain distinguishable.

No admin form may execute code/SQL, weaken tenant isolation, disable mandatory audits, edit secrets/infrastructure policies, remove database integrity or bypass upload safety. Business caps may change only inside hard supported ranges. Immutable history is append-only; corrections are new events, not invisible edits. Bulk operations are bounded, versioned and resumable; no unbounded synchronous tenant sweep.

## Dependencies and decisions

Depends on the entitlement resolver, quota definitions, configuration versions and audit schema review. Decide whether all existing super admins share business-management rights or a narrower permission is later needed; do not invent a new staff authorization model during the first phase. Define audit retention and policy-change propagation before rollout. [Entitlements](PLANS_AND_ENTITLEMENTS_SPEC.md) owns precedence and downgrade behavior.

## Acceptance and test scenarios

Test revoked/deleted/wrong-role admins for every new route, concurrent editors, invalid keys/ranges/dependencies, expired exceptions, plan revision migration, disabled plans, no-op updates and failed audit persistence. Verify mutation rolls back when mandatory audit cannot persist. Test effective-config preview against the value actually saved and enforced. Preserve provisioning/setup, support handoff and existing model desk behavior. Browser checks cover keyboard forms, errors, mobile layout and confirmation copy; backend negative tests establish enforcement.
