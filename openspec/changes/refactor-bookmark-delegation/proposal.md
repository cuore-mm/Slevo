# Change: Refactor bookmark actions via interface delegation

## Why
Bookmark actions in BoardViewModel and ThreadViewModel are manually forwarded and invoked with concrete-type casts, which makes the bookmark API harder to reuse and evolve.

## What Changes
- Introduce a shared bookmark actions interface with a delegated implementation.
- Update BoardViewModel and ThreadViewModel to implement the interface via delegation.
- Replace concrete ViewModel casts in BbsRouteScaffold with the shared interface.
- Keep SingleBookmarkState propagation behavior unchanged.

## Impact
- Affected specs: bookmark-actions
- Affected code: ui/board/viewmodel/BoardViewModel, ui/thread/viewmodel/ThreadViewModel, ui/bbsroute/BbsRouteScaffold, ui/common/bookmark/*
