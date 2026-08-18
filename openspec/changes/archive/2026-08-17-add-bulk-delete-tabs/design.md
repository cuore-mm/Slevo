## Context

現在のタブ一覧は `TabScreenContent.kt` が `TabSessionStore.openBoardTabs` と `openThreadTabs` を一度だけ収集し、`HorizontalPager` の `pagerState.currentPage` で `TabPage.BOARD` と `TabPage.THREAD` を切り替える。上部の `TabListTopSearchArea` は通常時に検索ボタンだけを表示し、長押しした単一タブに対しては `TabListUiState.selectedTabBounds` をアンカーとする `AnchoredTabActionMenu` を表示する。

単体クローズは `TabSessionStore` から `BoardTabsCoordinator.closeBoardTab` または retained scope 上の `ThreadTabsCoordinator.closeThreadTab` へ委譲される。各Coordinatorは対象行Repository API、canonical Room flow、pending projection、選択補正を管理し、Storeは対応する `TabSessionHolder` を破棄する。通常操作で `saveOpenBoardTabs` や `replaceOpenThreadTabsForBulkOperation` のような全件置換APIを使わないことは既存仕様上の制約である。

## Goals / Non-Goals

**Goals:**

- 検索ボタンの右に「その他」ボタンを置き、承認済みの1項目メニューを表示する。
- 実行時点で表示中の `TabPage` に属する未固定タブだけをすべて閉じる。
- 既存の対象行削除、選択補正、canonical確認、ThreadState遅延GC、holder破棄を再利用する。
- メニューと一括要求の状態を `TabListUiState` / `TabListViewModel` に置き、アクセシビリティとテスト可能性を確保する。

**Non-Goals:**

- 確認ダイアログ、Snackbar、Undo、対象件数表示、複数選択、追加メニュー項目は導入しない。
- 固定タブ、反対側のページ、タブ内容、履歴、ブックマークは変更しない。
- データベーススキーマ、DAO、Repository、Coordinatorの状態機械、全件置換APIは変更しない。
- 単一タブ長押しメニューの項目や挙動は変更しない。

## Decisions

### 1. 通常表示のアクションを右寄せRowにし、検索の右にその他ボタンを置く

`TabListSearchControls.kt` の `TabListTopSearchArea` で、`!isSearchMode` の `AnimatedVisibility` 内を右寄せの `Row` に変更し、既存検索 `TabActionButton` の後に `Icons.Default.MoreVert` の `TabActionButton` を置く。その他ボタンは `R.string.more`（「その他」）を `contentDescription` に使い、`onGloballyPositioned { boundsInWindow() }` で得た `IntRect` とともに `onMoreClick` を通知する。

検索モードでは既存どおり検索入力を優先し、検索・その他のアクションRowをともに非表示にする。これにより検索入力の幅と終了操作を変えない。メニュー表示中に検索モードへ入る経路は設けない。

代替案としてその他ボタンを検索モード中も残す方法は、入力領域のレイアウト変更という未承認UIを生むため採用しない。

### 2. `AnchoredTabActionMenu` に一括クローズ用オーバーロードを追加する

`AnchoredTabActionMenu.kt` に、既存の単一タブ用シグネチャを保ったまま、`expanded`、`anchorBoundsInWindow`、`hazeState`、`onDismissRequest`、`onCloseAllClick` を受け取る同名オーバーロードを追加する。このオーバーロードは `AnchoredOverlayMenu` に「全てのタブを閉じる」1項目だけを渡し、既存のクローズ項目と同じ `Close` アイコン、error色、装飾アイコンの `contentDescription = null` を使う。文言は `strings_common.xml` に新設する。

単一タブ用と一括用でアンカー配置、外側タップ、Back、色、Haze連携を共有しつつ、nullable callbackやmodeフラグで不正な項目組み合わせを作らない。既存呼び出し元 `TabLongPressOverlayLayer` のAPIと表示は維持する。

### 3. メニューの開閉とアンカーを `TabListUiState` が所有する

`TabListUiState.kt` に一括アクションメニューの表示フラグとアンカー `IntRect?` を追加する。`TabListViewModel.kt` に以下のイベントを追加する。

- その他ボタン押下: アンカーを保存してメニューを開く。
- 外側タップまたはBack: メニューを閉じ、アンカーを消去する。
- 「全てのタブを閉じる」押下: 先にメニュー状態を閉じてから、引数の `TabPage` を `TabSessionStore` へ渡す。
- ページ変更: 既存の長押し選択解除に加えて一括メニューも閉じ、旧ページのメニューから新ページを操作できないようにする。

`TabScreenContent.kt` は `pagerState.currentPage` を `TabPage.fromIndex` で変換してViewModelイベントへ渡す。ページはComposeのPagerが所有しており、ViewModelやStoreが推測しない。

### 4. Storeで対象スナップショットを確定し、既存の単体クローズを順番に再利用する

`TabSessionStore.kt` に `TabPage` を受ける一括クローズ入口を追加する。入口は指定ページの公開投影 `openBoardTabs.value` または `openThreadTabs.value` から `!isPinned` の対象を一度だけスナップショットし、次の経路へ渡す。

- Board: 対象順に既存 `closeBoardTab` を呼ぶ。
- Thread: 対象スナップショットを1つの retained Store scope coroutineで順に既存 `closeThreadTab` へ渡す。

対象を一度確定するため、処理途中のcanonical更新で反復対象がずれない。公開投影には受理済みpending操作が反映されるため、クリック受理時点で固定済みのタブは対象外になる。受理後に別操作が発生しても、CoordinatorのFIFO/pending規則とRepositoryのNoOp処理が既存どおり競合を解決する。

各単体クローズを再利用することで、Board/Threadの選択中タブを閉じた場合は既存の「同じ位置、範囲外なら末尾、残り0件ならEmpty」補正が順次適用される。選択中タブが固定済みなら削除されず選択も維持される。各 `TabSessionHolder` の破棄、対象行永続化、ThreadStateの遅延GCも既存経路に残る。

専用bulk intentと集合DELETEは、操作の原子性が要件に含まれない一方で、Board/Thread両状態機械、projection、canonical confirmation、supersession、DAO transactionを広く変更するため採用しない。全件置換APIは残存行の再書込みやThreadState timestamp更新を伴い、通常の対象行mutation契約に反するため使用しない。

### 5. 一括操作専用の削除アニメーションは追加しない

メニュー選択後はメニューを即時に閉じ、既存StateFlowの更新に従って対象行を一覧から除く。`RemovableTabList.externalRemoveKey` は単一タブのスワイプ/長押しクローズ用であるため、多重キー対応へ拡張しない。確認、進捗、完了通知も追加しない。

## Implementation Contract

1. `TabListTopSearchArea` の呼び出し元 `TabScreenContent` まで `onMoreClick(IntRect)` を接続し、検索ボタンの視覚・クリック処理を維持する。
2. 一括メニューは必ず `AnchoredTabActionMenu` の新オーバーロードで描画し、項目は完全一致の「全てのタブを閉じる」1件だけにする。
3. 一括メニュー状態をComposableのローカルBooleanだけで持たず、`TabListUiState` と `TabListViewModel` のイベントで更新する。
4. 実行ページはクリック時の `TabPage.fromIndex(pagerState.currentPage)` を渡す。`currentScreenRoute` や初期ページから推測しない。
5. 一括対象はStoreがスナップショットした指定ページの `isPinned == false` のタブだけにする。空集合は正常なNoOpとする。
6. Storeは既存の `closeBoardTab` / `closeThreadTab` を再利用し、DAO、Repository、全件置換APIを追加・呼び出ししない。
7. Thread対象は1つの retained coroutine内で一覧順に処理し、BottomSheetやViewModelの破棄後も受理済みクローズを完了させる。
8. 新規/変更する型と非自明関数にはリポジトリ規約どおりKDocを付け、長い関数は責務別セクションコメントを付ける。Compose Preview関数にはKDocを付けない。

## Error Cases and Compatibility

- 未固定タブが0件: メニューだけを閉じ、永続化呼び出しを行わない。
- 一括対象が処理前に単体クローズ済み: 既存のNoOp/存在確認で安全に完了する。
- 一括処理中に画面またはBottomSheetが閉じる: Store/Coordinator scopeで受理済み処理を継続する。
- canonical load前: 公開投影が空ならNoOpとし、存在しない対象を推測しない。
- ページ移動またはBack: メニューを閉じ、別ページへ古い操作を持ち越さない。
- 既存の単体長押しメニュー、スワイプ削除、固定切替、検索、最終選択ページ永続化とのAPI互換性を維持する。

## Testing Strategy

- `TabListViewModelTest.kt`: メニュー開閉・アンカー消去、ページ変更時dismiss、Board/ThreadページのStore委譲、実行時dismissを検証する。
- `TabSessionStoreTest.kt`: Board/Threadそれぞれで未固定だけが既存Coordinatorへ一覧順に委譲されること、固定タブと反対ページが不変であること、空集合がNoOpであること、Threadがretained処理されることを検証する。
- `app/src/androidTest/.../ui/tabs/` にCompose UIテストを追加し、通常時の検索→その他の順序とcontent description、「全てのタブを閉じる」1項目、項目クリック、外側タップ/Back dismissを検証する。
- 既存 `BoardTabsCoordinatorTest.kt` / `ThreadTabsCoordinatorTest.kt` の単体close・選択補正テストを回帰証拠として維持する。必要なら複数回closeで固定タブだけが残り、選択が残存タブまたはEmptyへ収束するケースを追加する。
- CI相当として `./gradlew testCiUnitTest assembleCi --stacktrace` を実行する。Compose instrumented testはCI workflow対象外のため、実装時に対象テストを `connectedDebugAndroidTest` で実行し、実行環境がなければ未実行理由を明示する。

## Risks / Trade-offs

- [複数の対象行削除は単一DB transactionではない] → 原子性を要件化せず、既存のFIFO/pending/canonical確認と対象行NoOpを再利用する。途中失敗時も既に確定したクローズを巻き戻さず、既存状態機械がcanonical状態へ収束する。
- [大量タブで単体コマンド数が増える] → タブ数は既存一覧の範囲であり、まず安全な既存経路を優先する。性能問題を確認した場合のみ別changeで集合mutationを設計する。
- [クリック直後の固定切替との競合] → 受理時の公開投影を操作の境界とし、同一画面のイベントをViewModelからStoreへ順に渡す。Coordinatorが受理済みpendingを投影するため、先行固定操作は対象判定へ反映される。
- [UIテストが通常CIで実行されない] → 操作境界をViewModel/StoreのJVMテストで網羅し、Compose testはローカルまたはAndroid実行環境で別途確認する。

## Migration Plan

データ移行は不要。UI、UiState、Store APIを同一リリースで追加する。問題時は追加したメニュー入口とStore一括入口を削除すれば、既存の単体クローズ経路へ戻せる。

## Open Questions

なし。ボタン、メニューコンポーネント、項目文言、表示中ページ境界、固定タブ除外はIssue #497で承認済みであり、範囲外のUIは追加しない。
