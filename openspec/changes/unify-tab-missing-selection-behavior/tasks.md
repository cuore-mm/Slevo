## 1. 依存関係と現行 publish 経路の固定

- [ ] 1.1 `openspec/changes/refactor-thread-tab-persistence-consistency/tasks.md` と `openspec/changes/fix-thread-deep-link-selection-consistency/tasks.md` の全 task が完了していることを確認し、未完なら本 change の application 実装を開始しない。完了条件: 両 change の最終コード/API と既存 regression test を読める状態である。
- [ ] 1.2 `BoardTabsCoordinator.kt`、`ThreadTabsCoordinator.kt`、`TabSessionStore.kt` で tabs、selected key、pending operation、canonical confirmation、close removed index を更新する全経路を列挙する。完了条件: 各経路を新しい atomic state reducer に接続する実装チェックリストが作業メモまたは変更 diff で確認でき、既存 FIFO/DB-canonical/cancellation 経路を特定できる。

## 2. 共有選択状態と pure decision の test-first 追加

- [ ] 2.1 `app/src/test/java/com/websarva/wings/android/slevo/ui/bbsroute/BbsRouteScaffoldSelectionTest.kt` に、`Loading`、valid key の各 index、`PendingMissing` の preserve decision、`Empty`、`Selected.key` invariant 違反の unit test を追加する。完了条件: 画面種別を test input にせず、各 state/cause の期待 decision を直接 assert する。
- [ ] 2.2 `app/src/main/java/com/websarva/wings/android/slevo/ui/bbsroute/` に `TabSelectionResolution<Key>`、`TabPresentationState<TabInfo, Key>`、pure display-decision helper を追加する。完了条件: `Selected`、`PendingMissing`、`Loading`、`Empty` が型で区別され、`Selected` は key index、pending は programmatic target なしを返し、全 type と非自明関数に規約準拠 KDoc がある。

## 3. 板 coordinator の atomic selection 解決

- [ ] 3.1 `BoardTabsCoordinatorTest.kt` に初回 loaded non-empty + null selection、valid restore、invalid restore、0 tabs の state transition test を追加する。完了条件: 非空 loaded state は常に `Selected` と同じ snapshot 内の有効 key を持ち、null/invalid は先頭へ補正され、空一覧は `Empty` と null key になることを assert する。
- [ ] 3.2 `BoardTabsCoordinatorTest.kt` の close coverage を同位置隣接、末尾 fallback、非選択 close の key 維持、last close の `Empty` まで拡張する。完了条件: 削除前 index に基づく既存規則と atomic presentation state を同じ test で assert する。
- [ ] 3.3 `BoardTabsCoordinator.kt` に tabs/key/pending cause を一回で解決・publish する reducer と `StateFlow<TabPresentationState<BoardTabInfo, String>>` を追加する。完了条件: 初回 Room emission、upsert/select、close、repository refresh の全 publish が reducer を通り、confirmed-invalid state が UI へ流れず、board tab 永続化方式は変更されない。
- [ ] 3.4 板の register/ensure lifecycle で target key の反映待ちが実在する経路だけ pending cause を設定し、success/failure/cancellation の全経路で解除する。完了条件: 通常の null/unknown key を pending と推測せず、解除後は `Selected` または `Empty` に収束する unit test が通る。

## 4. スレッド pending/canonical state との統合

- [ ] 4.1 `ThreadTabsCoordinatorTest.kt` に、selected key が stale canonical/projected list から一時的に欠落する間の `PendingMissing`、matching confirmation 後の `Selected`、失敗/cancellation 後の deterministic repair/`Empty` test を `MutableSharedFlow` と `CompletableDeferred` で追加する。完了条件: 各段階を scheduler 制御下で個別に assert し、pending marker が残留しない。
- [ ] 4.2 `ThreadTabsCoordinator.kt` の既存 `pendingOperations` と canonical revision lifecycle を selection reducer に接続し、`StateFlow<TabPresentationState<ThreadTabInfo, String>>` を公開する。完了条件: projection/FIFO worker/confirmation 条件を複製せず、success/failure/cancellation の全 path が atomic state を再評価する。
- [ ] 4.3 `ThreadTabsCoordinatorTest.kt` の rapid mutation、failed mutation recovery、readiness/repository/transaction cancellation、commit-before-cancellation tests を維持する。完了条件: test を削除・skip・期待緩和せず、新 presentation state 導入後も既存 assertion が通る。

## 5. Store と Deep Link orchestration

- [ ] 5.1 `TabSessionStore.kt` から board/thread presentation state を公開し、既存 `open*Tabs` と `selected*TabKey` が必要な consumer には同じ atomic source から派生した互換 Flow を提供する。完了条件: UI が別々の mutable tabs/key source を組み合わせず、全既存 call site が compile する。
- [ ] 5.2 `DeepLinkHandlerTest.kt` または抽出済み board route orchestration helper の unit test に、板 Deep Link の register/ensure → selection confirmation → navigate の順序、登録失敗、選択失敗、cancellation を追加する。完了条件: 成功時だけ target の `Selected` 確認後に navigation し、失敗時は既存 selection を変更しないことを order assertion で検証する。
- [ ] 5.3 `DeepLinkHandler.kt` と `TabSessionStore.kt` の板 Deep Link 経路を、test が要求する場合に限り selection confirmation 対応へ更新する。完了条件: URL 解決、既存 error 文言、destination deduplication を変更せず、navigation 時点で target key が atomic state の `Selected` である。

## 6. 共通 Pager UI の移行

- [ ] 6.1 `BbsRouteScaffold.kt` の API を `TabPresentationState<TabInfo, Key>` 入力へ変更し、`MissingSelectionPolicy`、`isTabsLoaded`、個別 `openTabs`/`selectedTabKey` parameter を削除する。完了条件: `BoardScaffold.kt` と `ThreadScaffold.kt` が画面固有 policy なしで同じ API を呼び、type-safe な `getKey` を使う。
- [ ] 6.2 `BbsRouteScaffold.kt` で `Selected` のみ key index へ scroll し、`PendingMissing` では `pagerState.currentPage` の tab から content/bottom bar/sheet を構成して programmatic scroll と `onTabSelected` を抑止する。完了条件: pending 中も `currentTabInfo` が null にならず、`HorizontalPager` の stable item key と既存 scroll-position state が維持される。
- [ ] 6.3 `BbsRouteScaffold.kt` の `Loading` と `Empty` を分離し、`Empty` のみ既存 `onEmptyTabs` を発火させる。完了条件: 板初回/restore の非空 snapshot は最初から有効 tab content を表示し、0 tabs では tab content を構成しない。
- [ ] 6.4 `BoardScaffold.kt` と `ThreadScaffold.kt` の個別 Flow collect と route initialization guard を atomic state に合わせて更新する。完了条件: valid restore を route placeholder で上書きせず、invalid/null restore は coordinator 補正に任せ、既存 resolve failure/cancellation handling を維持する。

## 7. Compose と orchestration の受入 test

- [ ] 7.1 `app/src/androidTest/java/com/websarva/wings/android/slevo/ui/bbsroute/BbsRouteScaffoldTest.kt` を追加し、valid `Selected` が対応 content を表示することを test tag と Compose rule で検証する。完了条件: 非 0 index の key を入力し、その page content が表示され selection callback の不要な発火がない。
- [ ] 7.2 同 Compose test に、表示中 page が 0 以外の状態で `PendingMissing` へ遷移しても同じ content を表示し、page 0 へ移動せず selection callback を発火しない scenario を追加する。完了条件: callback count、表示 tag、非表示 tag を決定論的に assert する。
- [ ] 7.3 同 Compose test に、pending target confirmation 後は target page/content へ同期し、`Empty` では content を表示しない scenario を追加する。完了条件: state update 前後を `waitForIdle` で分離し、target と empty の双方を assert する。
- [ ] 7.4 board initial load/restore、board Deep Link、close、transient pending、confirmed invalid、zero tabs、existing thread pending の各 spec scenario が少なくとも一つの unit/Compose/orchestration test に対応することを test 名一覧で確認する。完了条件: 未対応 scenario が 0 件である。

## 8. 回帰確認と完了条件

- [ ] 8.1 `./gradlew testDebugUnitTest` を実行する。完了条件: 新規 selection/coordinator/Deep Link tests と既存 thread FIFO/cancellation/close tests を含め全 unit test が成功する。
- [ ] 8.2 接続済み端末または CI 環境で `./gradlew connectedDebugAndroidTest` の `BbsRouteScaffoldTest` を実行する。完了条件: valid/pending/confirmed/empty の Compose test が成功する。実行環境がない場合は実行不能理由と CI 必須 check を明記し、未検証のまま成功扱いにしない。
- [ ] 8.3 `./gradlew assembleDebug` を実行する。完了条件: debug build が成功し、`MissingSelectionPolicy` と旧 `deriveSelectedPageIndex` の production/test 参照が 0 件である。
- [ ] 8.4 最終 diff で Room schema/entity、backup format、navigation route、user-facing resource、theme、icon、semantics、既存 active OpenSpec artifact に意図しない変更がないことを確認する。完了条件: 変更が本 change の scoped paths/tests のみに限定され、規約必須 KDoc・section comment が揃っている。
