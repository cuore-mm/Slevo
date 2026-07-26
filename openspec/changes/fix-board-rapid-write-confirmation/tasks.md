## 1. Supersession state rule

- [ ] 1.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/BoardTabsCoordinator.kt` に `Scroll`、`Pin`、`Info` だけを `(boardUrl, operation kind)` へ分類する private supersession key/helper を追加し、`Ensure` と `Delete` が対象外であることを exhaustive `when` で確認する。新しい non-trivial type/function にはリポジトリ規約どおり KDoc を annotation より前に付ける。
- [ ] 1.2 `BoardTabsCoordinator.register()` を変更し、新 command の登録と同一 supersession key の先行 command 除去を単一 `_state.update` / `rebuildPresentation()` で行う。Delete の `selectedKey` 更新を維持し、同一 key の pending が登録直後に最大 1 件であることをコード上で確認する。
- [ ] 1.3 `register()` の state 更新後に supersede 対象の `CompletableDeferred` を `TabCommandResult.NoOp` で terminal completion する。supersede 済み command の遅延 `finish()` が result を上書きせず、id-based removal で最新 command を除去しないことを確認する。
- [ ] 1.4 `BoardTabsCoordinator.execute()` の Loaded 待機後・repository dispatch 前に command ID の membership check を追加し、supersede 済み command は targeted write を呼ばず return する。in-flight write の cancellation、global mutex、actor/channel、full-list persistence を追加していないことを確認する。

## 2. Deterministic coordinator tests

- [ ] 2.1 `app/src/test/java/com/websarva/wings/android/slevo/ui/tabs/BoardTabsCoordinatorTest.kt` に `repeatedScrollWrites_finalOnlyCanonicalEmissionBoundsPendingAndConverges` を追加する。各 targeted write 成功後も中間 `databaseFlow` を emit せず、同一 Board scroll pending が 1、projection が最新位置、最終 snapshot 後に pending 0 かつ canonical/effective が最終位置であることを検証する。
- [ ] 2.2 同 test file に `queuedScrollWrites_supersededWritesAreSkipped` を追加する。test dispatcher を進める前に複数 scroll command を登録し、`runCurrent()` 後に supersede 済み repository call がなく最新位置の `updateBoardTabScrollPosition` だけが呼ばれることを検証する。
- [ ] 2.3 同 test file に `rapidPinWrites_finalOnlyCanonicalEmissionConverges` と `rapidResolvedInfoWrites_finalOnlyCanonicalEmissionConverges` を追加する。既存 effective projection を基準にした最終 pin intent、最終 boardId/boardName、pending 最大 1、最終 snapshot 後の pending 解放と canonical convergence をそれぞれ検証する。
- [ ] 2.4 同 test file に `supersededWriteFailure_doesNotRemoveLatestPending` と `latestWriteFailure_rollsBackWithoutRestoringSupersededProjection` を追加する。先行の遅延 failure が最新 pending/projection を変更しないこと、および最新 failure が pending を除去して古い projection を復活させず DB canonical state へ戻ることを `CompletableDeferred` barrier で決定的に検証する。
- [ ] 2.5 同 test file に `sameOperationOnDifferentBoards_confirmsIndependently` を追加する。Board A の最終 snapshot では A の command だけが確認され B の最新 projection が残り、Board B の最終 snapshot 後に両方の pending が 0 になることを検証する。
- [ ] 2.6 追加 test と helper に日本語 KDoc、guard/fallback comment、30 行超の section header を規約どおり付け、reflection または test 専用 production API を追加していないことを確認する。

## 3. Regression and verification

- [ ] 3.1 `BoardTabsCoordinatorTest` の既存 close、selection、page animation、resolved-info field preservation、large snapshot/no bulk persistence test を変更せず成功させ、`Ensure`、`Delete`、Deep Link result の契約に production 差分がないことを diff で確認する。
- [ ] 3.2 targeted unit test を `./gradlew :app:testDebugUnitTest --tests 'com.websarva.wings.android.slevo.ui.tabs.BoardTabsCoordinatorTest'` で実行し、rapid scroll/pin/info、failure、different-board の全 test が成功することを確認する。
- [ ] 3.3 repository 必須 verification として `./gradlew :app:testDebugUnitTest` と `./gradlew :app:assembleDebug` を実行し、unit test と build が成功することを確認する。
- [ ] 3.4 `git diff -- app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/BoardTabsCoordinator.kt app/src/test/java/com/websarva/wings/android/slevo/ui/tabs/BoardTabsCoordinatorTest.kt` を確認し、DAO、Repository、UI、Thread、deferred P2、full-list persistence が変更されていないことを完了条件とする。
