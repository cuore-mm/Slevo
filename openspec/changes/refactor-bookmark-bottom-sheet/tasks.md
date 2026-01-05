## 1. Implementation
- [ ] 1.1 Define `BookmarkBottomSheetViewModel` and its `UiState`/mode types for single and bulk contexts
- [ ] 1.2 Wire repositories and context binding (board/thread/bulk selection) into the new ViewModel
- [ ] 1.3 Replace bookmark operations in `BoardViewModel` and `ThreadViewModel` with delegation to the shared ViewModel
- [ ] 1.4 Replace bookmark operations in `BookmarkListViewModel` with delegation to the shared ViewModel (bulk mode)
- [ ] 1.5 Update `BbsRouteScaffold` and `BookmarkListScaffold` to consume the shared ViewModel state
- [ ] 1.6 Remove or deprecate `SingleBookmarkViewModel` and related state if no longer referenced

## 2. Validation
- [ ] 2.1 Run unit tests
- [ ] 2.2 Run build
