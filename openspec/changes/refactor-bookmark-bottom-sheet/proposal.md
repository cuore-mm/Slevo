# Change: Refactor bookmark logic into a bottom sheet ViewModel

## Why
Bookmark-related operations are duplicated across `ThreadViewModel`, `BoardViewModel`, and `BookmarkListViewModel`. Consolidating them reduces drift and makes the bookmark sheet behavior consistent across screens.

## What Changes
- Introduce a dedicated bookmark bottom sheet ViewModel that owns bookmark edit flows for single and bulk contexts.
- Replace bookmark-handling methods in `ThreadViewModel`, `BoardViewModel`, and `BookmarkListViewModel` with delegation to the shared ViewModel.
- Adjust UI scaffolds to use the shared ViewModel state instead of per-screen bookmark state.

## Impact
- Affected specs: bookmark
- Affected code: `ui/common/bookmark/*`, `ui/board/viewmodel/BoardViewModel.kt`, `ui/thread/viewmodel/ThreadViewModel.kt`, `ui/bookmarklist/BookmarkListViewModel.kt`, `ui/bbsroute/BbsRouteScaffold.kt`, `ui/bookmarklist/BookmarkListScaffold.kt`
