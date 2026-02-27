## Why

Issue #458 reports that vertical scrolling in thread content sometimes triggers an unintended tab switch. This behavior breaks reading continuity and contradicts the expected operation where adjacent tab switching is limited to bottom bar swipe interactions.

## What Changes

- Restrict tab swipe switching so drag gestures from content areas do not propagate to pager tab switching.
- Preserve existing vertical list scrolling and in-content gesture handling for thread and board screens.
- Keep tab switching from non-content entry points (including bottom bar swipe and existing explicit tab actions) working as before.
- Add regression test coverage for accidental tab switching during content scroll and for preserved intended tab switch paths.

## Capabilities

### New Capabilities
- `tab-swipe-interaction-scope`: Define and enforce where drag input is allowed to switch adjacent tabs in BBS route screens.

### Modified Capabilities
- (none)

## Impact

- Affected UI layer: `app/src/main/java/com/websarva/wings/android/slevo/ui/bbsroute/BbsRouteScaffold.kt`.
- Affected thread/board integration points that pass content modifiers and gesture handlers.
- Affected tests: compose/instrumented tests around tab switching and scrolling behavior.
