## 1. Interaction boundary implementation

- [ ] 1.1 Update `BbsRouteScaffold` so content-area horizontal drag does not propagate into pager tab switching.
- [ ] 1.2 Ensure the boundary logic preserves bottom bar swipe and explicit tab-switch command paths.
- [ ] 1.3 Confirm thread and board content still receive drag input required for vertical scroll and configured directional gestures.

## 2. Regression tests

- [ ] 2.1 Add/extend compose test to verify thread vertical scroll does not change current tab.
- [ ] 2.2 Add/extend compose test to verify board vertical scroll does not change current tab.
- [ ] 2.3 Add/extend compose test to verify bottom bar swipe or equivalent dedicated path still changes adjacent tab.

## 3. Validation

- [ ] 3.1 Run affected UI tests and confirm no regressions in tab switching behavior.
- [ ] 3.2 Run unit tests related to tab coordination/gesture dispatch if impacted by implementation.
- [ ] 3.3 Perform manual validation on thread and board screens for accidental tab-switch prevention and expected tab transitions.
