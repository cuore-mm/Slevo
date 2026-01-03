## Context
BoardViewModel and ThreadViewModel currently forward bookmark actions through BaseViewModel helpers, while shared UI code invokes those actions with concrete-type casts.

## Goals / Non-Goals
- Goals: unify the bookmark actions API, remove duplicated forwarding logic, and enable interface-based calls.
- Non-Goals: change bookmark behavior, data persistence, or UI state contents.

## Decisions
- Decision: define a BookmarkActions interface and a delegate implementation that wraps SingleBookmarkViewModel.
- Decision: BoardViewModel and ThreadViewModel will initialize the delegate during their setup flows and implement BookmarkActions via delegation.
- Decision: BbsRouteScaffold will call bookmark actions through the shared interface instead of concrete ViewModel casts.

## Risks / Trade-offs
- Risk: delegate initialization order might allow calls before setup; mitigation: keep the current initializeBoard/initializeThread ordering and guard against null delegates.
- Risk: API surface drift if method names change; mitigation: keep existing method names and signatures.

## Migration Plan
- Add the interface and delegate wrapper.
- Wire delegation in BoardViewModel and ThreadViewModel.
- Replace bookmark action calls in BbsRouteScaffold.
- Validate with build and unit tests.

## Open Questions
- None.
