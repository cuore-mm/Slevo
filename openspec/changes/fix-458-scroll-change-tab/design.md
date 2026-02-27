## Context

`BbsRouteScaffold` hosts tab pages with `HorizontalPager`, while each page contains scrollable content such as thread response lists and board lists. Issue #458 shows that horizontal drag recognition from content interaction can still cause accidental adjacent-tab transitions during vertical reading scroll. The expected behavior is that content-area drag should be consumed by content interactions, while tab switching by dedicated tab controls must remain available.

## Goals / Non-Goals

**Goals:**
- Prevent content-area drag from switching pager tabs on thread and board screens.
- Preserve vertical scrolling responsiveness in response lists and board lists.
- Preserve intentional tab transitions from non-content paths such as bottom bar swipe and explicit tab actions.
- Keep the implementation localized to route-scaffold interaction boundaries without rewriting thread or board business logic.

**Non-Goals:**
- Redesigning gesture assignment semantics in `GestureSettings`.
- Changing tab data model, tab persistence, or tab closing/opening rules.
- Introducing new navigation framework dependencies.

## Decisions

1. Introduce explicit interaction boundary handling in `BbsRouteScaffold` so content-area horizontal drags do not reach pager tab switching.
   - Rationale: The issue is an interaction propagation problem, so the route scaffold is the correct single chokepoint.
   - Alternative considered: force `HorizontalPager.userScrollEnabled = false` globally. Rejected because it would also remove intended swipe-based tab switching from non-content areas.

2. Keep existing content gesture and vertical scroll paths intact by only intercepting horizontal tab-switch propagation, not all drag input.
   - Rationale: Thread directional gesture features and list scroll interactions are existing user-visible behaviors and must not regress.
   - Alternative considered: blanket pointer consumption at container root. Rejected because it risks breaking per-item gesture features and nested scroll behavior.

3. Verify behavior with regression tests focused on interaction contracts.
   - Rationale: This bug is intermittent in manual operation; deterministic UI tests reduce recurrence risk.
   - Alternative considered: manual QA only. Rejected due to intermittent nature and weak long-term protection.

## Risks / Trade-offs

- [Risk] Interception boundary may be too broad and suppress intended gesture actions in content. -> Mitigation: limit interception to pager-switch propagation path and verify thread gesture actions still fire.
- [Risk] Existing bottom bar swipe transition path may be unintentionally blocked. -> Mitigation: add regression test for bottom bar initiated adjacent tab switch.
- [Risk] Nested scroll behavior can change subtly on some devices. -> Mitigation: verify thread and board vertical list scroll with compose tests and targeted manual checks.
