## 1. 先行変更と failing tests の固定

- [ ] 1.1 `refactor-thread-tab-persistence-consistency` の全 task、unit/build/Room test が完了し、`ThreadTabsCoordinator` / `TabSessionStore` に readiness、targeted mutation completion、canonical confirmation API が存在することを確認する。不足している場合は本変更を開始せず先行変更へ戻る。
- [x] 1.2 `app/src/test/java/com/websarva/wings/android/slevo/ui/tabs/ThreadTabsCoordinatorTest.kt` に、存在しない target の selection attempt が failure を返し、既存 selected key を null または別 key に変更しない failing test を追加する。canonical target のみ成功する対照 test も追加する。
- [x] 1.3 `app/src/test/java/com/websarva/wings/android/slevo/ui/bbsroute/BbsRouteScaffoldSelectionTest.kt` に、thread preserve policy では missing selected key が page 0 ではなく no-scroll を返す failing testを追加する。matched key、loaded-empty、confirmed deletion 後の adjacent key、board の既存 missing→first behavior の assertion を残す。
- [x] 1.4 `app/src/test/java/com/websarva/wings/android/slevo/ui/navigation/` に Deep Link thread orchestration test を追加し、`CompletableDeferred` で readiness、registration write、canonical confirmation、selection を個別停止でき、呼出順を記録できる fixture を作る。
- [x] 1.5 Task 1.4 の fixture で、readiness blocked 中は register/select/navigate 未呼出し、write completion 後も canonical confirmation 前は select/navigate 未呼出し、confirmation 後は select→navigate の順で各一回となる failing test を追加する。
- [x] 1.6 同 fixture に registration failure、canonical target 不在、selection failure、cancellation の failing tests を追加し、既存 selection 不変、navigation 未呼出し、通常 failure だけ既存 error callback と consume が各一回、cancellation は古い target の error/select/navigate なしとなることを確認する。

## 2. selection contract の hardening

- [x] 2.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/ThreadTabsCoordinator.kt` の `selectThreadTab` を success/failure completion API に変更する。target 不在時は `_selectedThreadTabKey` を変更せず failure を返し、Task 1.2 を通す。
- [x] 2.2 `ThreadTabsCoordinator.kt` の last-tab close と `updateSelectedThreadKeyAfterRemoval` を、absent-target selection failure と分離した明示的 removal correction として維持する。選択中削除では adjacent/last/null が従来どおり選ばれる既存/追加 test を通す。
- [x] 2.3 `ThreadTabsCoordinator.animateThreadPage` と thread selection/page helper を検索し、unresolved selected key に `?: 0` を適用する経路を no-op/result failure へ変更する。target 不在で page 0 animation target が生成されない unit test を通す。
- [x] 2.4 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/store/TabSessionStore.kt` の thread selection/register API を新しい result contract へ移行し、caller が failure を捨てないようにする。`TabSessionStoreTest.kt` で absent/confirmed target の delegation と selection preservation を確認する。

## 3. thread Pager の no-jump policy

- [x] 3.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/bbsroute/BbsRouteScaffold.kt` の `deriveSelectedPageIndex` と pager synchronization に、thread call site だけが指定する missing-selection preserve policy を追加する。unresolved 時は programmatic scroll 指示を返さず、resolved index の時だけ `scrollToPage` を実行する。
- [x] 3.2 `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt` から preserve policy を指定し、`app/src/main/java/com/websarva/wings/android/slevo/ui/board/screen/BoardScaffold.kt` は既存 policy を維持する。Task 1.3 の thread/board assertion をすべて通す。
- [ ] 3.3 必要な場合は `app/src/androidTest/` の `BbsRouteScaffold` Compose test を追加し、current page が 0 以外の状態で selected key を一時不一致にしても page 0 へ移動せず、解決済み key 受信後だけ対応 page へ移動することを semantics 変更なしで確認する。

## 4. Deep Link の順序付き orchestration

- [x] 4.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/navigation/DeepLinkHandler.kt` の thread branch を、URL resolve/normalize、`awaitThreadTabsReady`、registration completion、canonical existence verification、selection result、navigation の順に実行する testable suspend function へ分離する。Task 1.5 の call-order test を通す。
- [x] 4.2 `DeepLinkHandler.kt` で pending target を selected key として設定せず、registration/confirmation 待機中は処理開始前の selection を維持する。blocked readiness と stale/absent target test で selected key と navigate callback が不変であることを確認する。
- [x] 4.3 `DeepLinkHandler.kt` で `navigateToThreadScreen` を selection success 後だけ呼ぶ。failure result は既存 error Toast callback へ集約し、既存 `invalidUrlMessage` resource と `finally { onConsumed() }` を再利用して、新規 resource/UI を追加していない。
- [x] 4.4 `DeepLinkHandler.kt` で `CancellationException` を通常 error として通知せず再伝播し、古い target の select/navigation/error side effect を停止する。Task 1.6 の cancellation test を通す。

## 5. destination orchestration の重複排除

- [x] 5.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt` の `LaunchedEffect(threadRoute, threadLoaded)` を先行変更の readiness/completion API へ移行する。既に canonical target が selected なら ensure/select を再発行しない guard を追加し、unit/mock test でゼロ回呼出しを確認する。
- [x] 5.2 Deep Link 以外の未登録 route entry では `ThreadScaffold` が registration completion と canonical confirmation を待ってから selection し、failure 時は現在 selection を壊さないようにする。既存 board resolution failure recovery を維持する test を通す。
- [x] 5.3 `TabSessionStoreTest.kt` と navigation test に、Deep Link success 後の destination 表示で同じ target の mutation が一回だけ、navigation が一回だけである assertion を追加する。

## 6. UI 境界、回帰、完了確認

- [x] 6.1 production diff を確認し、新しい Composable、Toast/Snackbar/Dialog、string resource、icon、theme、content description、semantics が追加変更されていないこと、board Deep Link/selection/Pager behavior が変わっていないことを確認する。
- [x] 6.2 Deep Link orchestration test、`ThreadTabsCoordinatorTest.kt`、`TabSessionStoreTest.kt`、`BbsRouteScaffoldSelectionTest.kt`、`NavigationExtensionsTest.kt` を実行し、blocked readiness、canonical confirmation、failure/cancellation、no first-page jump、success-only navigation が決定的に通ることを確認する。
- [x] 6.3 CI の `testDebugUnitTest` で unit test 全件成功を確認した。Compose/UI test は追加していないため emulator/device 実行は不要とした。
- [x] 6.4 CI の APK build で build 成功を確認し、DB/schema/migration、URL pattern、route serialization、back-stack policy に変更がないことを最終差分で確認する。
