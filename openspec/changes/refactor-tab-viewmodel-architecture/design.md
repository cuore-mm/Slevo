## Context

現状の板・スレッド画面は `BbsRouteScaffold` の Pager ページごとに `TabSessionStore.getOrCreateBoardViewModel()` / `getOrCreateThreadViewModel()` を呼び、`TabViewModelRegistry` がタブ key 単位で ViewModel をキャッシュしている。これはタブ切替の体感速度とタブごとの UI 状態分離には有利だが、ViewModel の寿命を Android 標準の `ViewModelStoreOwner` ではなく独自 Map で扱うため、生成・解放・監視 Flow の停止責務が曖昧になりやすい。

特に `ThreadViewModel` はスレ本文取得、レス表示変換、NG、検索、ツリー表示、ポップアップ、ブックマーク、投稿ダイアログ、画像保存、自動更新、スクロール保存、新着情報同期を抱えており、同時に開いたスレッドタブ数ぶん重いインスタンスが常駐する。加えて `ThreadTabInfo` と `ThreadUiState` の双方に新着・既読・スクロール周辺の状態が存在し、正本が分かりにくい。

この変更では、タブを「独立した Android ViewModel の所有単位」ではなく「画面内のセッション単位」として扱う。ViewModel は板画面 route / スレッド画面 route に 1 つずつ置き、選択中タブのセッション状態とデータ層を合成して表示状態を作る。

## Goals / Non-Goals

**Goals:**

- タブごとの `BoardViewModel` / `ThreadViewModel` キャッシュを廃止できる設計にする。
- タブ固有状態、スレッド・板データの正本、画面表示用 `UiState` の責務境界を明確にする。
- `ThreadViewModel` / `BoardViewModel` を軽量な route-level ViewModel に再構成し、データ取得・変換・更新処理を UseCase / Repository / coordinator へ分離する。
- 既存のタブ切替、スクロール位置復元、新着表示、検索、ポップアップ、投稿ダイアログ、更新操作の体験を維持する。
- 段階移行できるように、互換レイヤーとテスト観点を定義する。

**Non-Goals:**

- UI デザインやナビゲーション仕様そのものの変更。
- 板・スレッドデータの保存形式を必ず変更すること。
- すべての `ThreadViewModel` 内部ロジックを一度の変更で完全に最適化すること。
- Compose の Pager やタブ一覧 UI の大規模な見た目変更。

## Decisions

### Decision 1: ViewModel はタブ単位ではなく route 単位で所有する

`BoardRouteViewModel` 相当と `ThreadRouteViewModel` 相当を、板画面 route / スレッド画面 route の `ViewModelStoreOwner` に紐づけて 1 つずつ保持する。Pager の各ページは ViewModel インスタンスを直接所有せず、選択中または表示対象タブ key を ViewModel に渡して表示状態を得る。

代替案として per-tab ViewModel を維持する案もあるが、`TabViewModelRegistry` による独自ライフサイクル管理、タブ数に比例するメモリ増加、状態重複の問題が残るため採用しない。

### Decision 2: タブ固有状態は TabSessionStore 配下の Session State に集約する

タブの並び順、選択状態、ピン留め、スクロール位置、検索クエリ、表示モード、ポップアップスタック、投稿ダイアログ下書きなど「タブを閉じるまで保持したい UI セッション状態」は `TabSessionStore` 配下に集約する。ViewModel はこれらの正本を複製せず、Flow として購読して `UiState` に反映する。

代替案として `UiState` にタブ固有状態を残す案もあるが、タブ切替時に ViewModel 側と Store 側の同期が必要になり、現在の重複状態問題を解消できない。

### Decision 3: TabInfo と Session State は分離する

`ThreadTabInfo` / `BoardTabInfo` は、タブ一覧・選択・並び順・復元に必要な軽量メタ情報に限定する。thread key / board key、表示タイトル、pin、order、復元に必要なスクロール位置など、アプリ再起動後もタブとして復元したい最小限の状態を扱う。

検索クエリ、表示モード、ポップアップスタック、投稿ダイアログ下書き、自動スクロール状態など、タブを開いている間の UI セッション状態は `ThreadSessionState` / `BoardSessionState` として別モデルに分ける。これにより `TabInfo` の肥大化を防ぎ、タブ一覧用モデル、セッション状態、描画用 `UiState` の責務を分離する。

スクロール位置は既存仕様との互換性と復元要件が強いため、`TabInfo` 側または永続タブ状態に残す。ただしスレッドの最新レス数、最初の新着レス番号、最終既読レス番号などの客観状態・既読状態は `TabInfo` / Session State の正本にせず、Repository / 履歴状態から合成する。

代替案として既存の `ThreadTabInfo` / `BoardTabInfo` にセッション状態を追加する案もあるが、タブ一覧・永続化・UI セッションの責務が混ざり、将来の状態同期や保存単位が不明瞭になるため採用しない。

### Decision 4: 板・スレ内容の正本は Repository / DB / UseCase に置く

板一覧、スレ本文、パース済み投稿、既読、ブックマーク、NG 設定、投稿履歴は Repository / DB / UseCase を正本とする。ViewModel は長期キャッシュではなく、選択中タブと各データソースを合成する collector / presenter として振る舞う。

代替案として `TabSessionStore` がスレ本文や板一覧を直接保持する案もあるが、永続化・更新・キャッシュ整合性の責務が肥大化するため採用しない。

### Decision 5: 重い処理は UseCase / coordinator へ抽出して ViewModel を薄くする

レスの表示行生成、検索・NG 適用、ツリー派生情報、更新処理、新着計算、自動更新判定などは、単体テスト可能な UseCase / coordinator へ移す。ViewModel はイベントを受け取り、UseCase を呼び、結果を `UiState` と `TabSessionStore` に反映する。

代替案として ViewModel のままメソッド分割する案もあるが、route-level にした後も巨大 ViewModel が残り、テスト容易性と責務分離の改善が限定的になる。

### Decision 6: 移行は互換層を挟んだ段階移行にする

最初にタブ固有状態の定義と正本を整理し、次に Thread / Board のデータ合成処理を UseCase 化する。その後 `BbsRouteScaffold` が per-tab ViewModel を要求しない形に変更し、最後に `TabViewModelRegistry` と手動 release を削除または互換用途のみに縮小する。

一括置換は差分が大きく、スクロール復元・新着同期・投稿ダイアログなどの退行リスクが高いため採用しない。

### Decision 7: Pager の composition 範囲に UiState 購読を委譲する

Pager の offscreen page をアプリ側で `previous/current/next` として明示管理せず、Compose Pager が composition するページだけが対象タブ key の `UiState` を購読する構造にする。route-level ViewModel は `observeUiState(tabKey)` または `uiStateFor(tabKey)` のような tab key 指定 API を提供し、全 open tabs 分の `UiState` を常時 combine しない。

Flow は tab key ごとに遅延生成し、必要に応じて ViewModel 内で再利用してよい。ただし `SharingStarted.WhileSubscribed` 相当の購読中のみ動く共有方式を使い、Pager が composition から外したページの重い合成処理が継続しないようにする。Repository cache や軽量 summary の保持は許容するが、完全な `UiState` の常時合成は表示中・composition 中ページに限定する。

代替案としてアプリ側で隣接ページを管理して先読みする案もあるが、Pager の offscreen policy と二重管理になりやすく、UI 層の composition 範囲と ViewModel の合成範囲がずれるため採用しない。性能問題が確認された場合のみ、Repository cache の先読みや小さな LRU cache を追加する。

### Decision 8: UI セッション状態は永続化せず、自動更新は表示中タブに限定する

検索クエリ、表示モード、ポップアップスタック、投稿ダイアログ下書き、自動スクロール状態などの UI セッション状態は、プロセス内のタブセッション状態として扱い、アプリ再起動後の永続復元対象にしない。永続化するのはタブ識別子、タイトル、pin、order、スクロール位置など、タブ一覧・選択・復元に必要な軽量状態に限定する。

自動スクロールに伴う定期 reload / refresh は、現在表示中のスレッドタブのみを対象にする。非表示タブは自動更新せず、開いている全タブの更新はタブ一覧や更新ボタンなどの明示操作として扱う。

代替案として非表示タブの検索・ポップアップ状態を永続化する案もあるが、保存対象が増えて復元時の整合性が複雑になるため採用しない。開いている全タブを自動更新する案も、通信・変換・合成コストがタブ数に比例して増え、per-tab ViewModel 廃止の目的と逆行するため採用しない。

### Decision 9: Task 1 の棚卸し結果を状態配置の正本とする

Task 1 の時点で、`ThreadUiState`、`BoardUiState`、`ThreadTabInfo`、`BoardTabInfo`、`TabSessionStore`、`ThreadViewModel`、`BoardViewModel` の保持項目を以下の 4 区分へ確定分類する。この分類を以後の実装判断の基準とし、新しい状態を追加する場合も同じ区分に従う。

#### 軽量 TabInfo に残す項目

- `ThreadTabInfo` / `BoardTabInfo` の識別子: `ThreadId`、`boardUrl`、`boardName`、`serviceName`
- タブ一覧表示に必要な軽量メタ情報: `title`、`bookmarkColorName`、`isPinned`
- タブ復元に必要な永続状態: `firstVisibleItemIndex`、`firstVisibleItemScrollOffset`
- 選択中タブ key とタブ並び順は `BoardTabsCoordinator` / `ThreadTabsCoordinator` の正本として扱い、`TabSessionStore` から再公開する

`resCount`、`newResCount`、`prevResCount`、`lastReadResNo`、`firstNewResNo` のような客観状態・既読状態は、表示モデル互換のため当面 `ThreadTabInfo` に残りうるが、正本としては扱わず Repository 由来の合成値として段階的に縮小する。

#### UI SessionState に移す項目

- 検索状態: `searchInputValue`、`searchQuery`、`isSearchMode`、`isSearchActive`
- シート / ダイアログ状態: `showThreadInfoSheet`、`showMoreSheet`、`showDisplaySettingsSheet`、`showImageMenuSheet`、`showImageNgDialog`、`showBoardInfoSheet`、`showSortSheet`、`postDialogState`
- 一時操作状態: `popupStack`、`pendingToastResId`、`resetScroll`、`isTabSwipeEnabled`
- 揮発 UI 状態: `imageMenuTargetUrl`、`imageMenuTargetUrls`、`imageNgTargetUrl`
- 自動スクロール / 更新まわりの揮発状態: `isAutoScroll` の実行状態、`loadingSource`、`isLoading`、`loadProgress`

これらは `ThreadSessionState` / `BoardSessionState` または `TabSessionStore` 配下の補助 holder に寄せ、アプリ再起動後は復元しない。

#### Repository / DB / UseCase を正本にする項目

- 板 / スレの客観データ: `boardInfo`、`threadInfo`、`threads`、`posts`
- 既読・新着・履歴: `prevResCount`、`lastReadResNo`、`firstNewResNo`、`myPostNumbers`
- ブックマーク状態: `bookmarkStatusState` の基データ
- NG / 設定: `ngPostNumbers` の基データ、`textScale`、`headerTextScale`、`bodyTextScale`、`lineHeight`、`gestureSettings`、ソート設定

ViewModel はこれらを保持せず、Repository / UseCase / coordinator の Flow を購読して表示用に合成する。

#### 合成 UiState に限定する項目

- スレッド表示派生: `visiblePostRows`、`replyCounts`、`firstAfterIndex`、`postGroups`、`latestArrivalGroupIndex`
- ツリー / 返信 / ID 派生: `idCountMap`、`idIndexList`、`replySourceMap`、`treeOrder`、`treeDepthMap`、`treeRootMap`
- 揮発キャッシュ: `imageLoadFailureByUrl`、`imageLoadingUrls`
- 画面描画専用の一時値: `serviceName`、`showMinimapScrollbar`

これらは保持の正本を持たず、SessionState と Repository の値から毎回再合成する。

#### per-tab ViewModel 前提を解消する移管方針

- `bookmarkSheetHolder` と `postDialogController` は per-tab ViewModel の所有をやめ、`TabSessionStore` 配下の Session holder へ寄せる
- `popupStack` と `isTabSwipeEnabled` はアクティブタブ基準で扱う SessionState とし、Pager 全体制御は `TabSessionStore` が担う
- `pendingPost`、`observedThreadHistoryId`、`postHistoryCollectJob`、`lastAutoRefreshTime` のような ViewModel 内部の継続状態は Repository 監視または SessionState へ移す
- `ThreadViewModel` / `BoardViewModel` に残すのは、対象 tab key の `UiState` Flow を遅延生成する presenter 責務だけに絞る

## Risks / Trade-offs

- [Risk] 非表示タブの UI セッション状態が失われる → `TabSessionStore` にタブ固有状態を移し、タブ切替・画面離脱・タブ削除の各タイミングで保存を検証する。
- [Risk] `TabInfo` と `SessionState` の境界が曖昧になり状態が再び重複する → アプリ再起動後も復元する軽量タブ状態は `TabInfo`、タブを開いている間の UI セッション状態は `SessionState`、客観データは Repository / DB という分類基準を実装タスクで検証する。
- [Risk] Pager が compose したページごとに `UiState` Flow を作ることで Flow 生成が頻発する → tab key ごとの Flow 定義を再利用し、購読がなくなったら重い合成が止まる共有方式を使う。
- [Risk] 完全な `UiState` を表示直前まで合成しないことで初回表示が遅れる → Repository cache や軽量 summary を活用し、必要になった場合だけ限定的な LRU cache を追加する。
- [Risk] `ThreadViewModel` 分割中に既存挙動が壊れる → UseCase 抽出ごとに既存ユニットテストを追加し、移行中は互換 API を残す。
- [Risk] 自動更新やバックグラウンド更新の責務が曖昧になる → 更新ポリシーを route-level ViewModel ではなく UseCase / coordinator に置き、表示中タブと一括更新のトリガーを明示する。
- [Risk] `UiState` から正本状態を削ることで Compose 側の参照が大きく変わる → まず `UiState` のフィールドを読み取り専用の合成結果として維持し、内部の供給元だけを段階的に置き換える。
- [Risk] UI セッション状態を永続化しないことでアプリ再起動後に検索やポップアップ状態が失われる → 再起動後に復元する状態はタブ識別子とスクロール位置などの軽量タブ状態に限定する仕様として扱い、表示中の操作状態はプロセス内セッションに閉じる。
- [Risk] 自動更新を表示中タブに限定すると非表示タブの新着が即時反映されない → 非表示タブの新着確認は明示的な一括更新またはタブ表示時の更新で扱う。

## Migration Plan

1. 現状の `ThreadUiState` / `BoardUiState` と `ThreadTabInfo` / `BoardTabInfo` の重複項目を棚卸しし、正本を `TabInfo`、`SessionState`、Repository / DB、ViewModel 合成結果に分類する。
2. `ThreadTabInfo` / `BoardTabInfo` は軽量なタブメタ情報に限定し、検索・表示モード・ポップアップ・ダイアログ下書きなどを保持する `ThreadSessionState` / `BoardSessionState` 相当の別モデルを導入する。
3. スレッド表示行生成、NG・検索適用、新着計算、板スレ一覧変換を UseCase / coordinator として切り出し、既存 ViewModel から利用する。
4. route-level ViewModel を導入し、tab key 指定で `UiState` Flow を遅延提供できる API を追加する。
5. `BbsRouteScaffold` のページ生成を per-tab ViewModel 取得から、Pager が compose したページごとに tab key 指定 `UiState` Flow を購読する構造へ切り替える。
6. UI セッション状態はプロセス内 Session State に限定し、永続タブ状態へ保存しないように保存経路を整理する。
7. 自動スクロールに伴う定期更新を表示中スレッドタブのみに限定し、全タブ更新は明示操作として分離する。
8. `TabViewModelRegistry`、`BaseViewModel.release()`、per-tab ViewModel factory 依存を削除または互換層として縮小する。
9. スクロール復元、タブ切替、新着表示、更新、投稿ダイアログ、検索、ポップアップの回帰テストを追加・更新する。

## Open Questions

- なし。
