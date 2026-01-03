## 1. Implementation
- [ ] 1.1 Create a BookmarkActions interface and a delegate wrapper around SingleBookmarkViewModel.
- [ ] 1.2 Update BoardViewModel to initialize the delegate and implement BookmarkActions via delegation.
- [ ] 1.3 Update ThreadViewModel to initialize the delegate and implement BookmarkActions via delegation.
- [ ] 1.4 Replace bookmark action calls in BbsRouteScaffold with the shared interface.
- [ ] 1.5 Remove or retire BaseViewModel bookmark forwarding helpers once unused.

## 2. Validation
- [ ] 2.1 Run `./gradlew build`.
- [ ] 2.2 Run `./gradlew test` (or `./gradlew testDebugUnitTest`).
