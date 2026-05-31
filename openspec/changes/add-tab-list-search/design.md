## Context

タブ一覧画面は `TabScreenContent` が `TabsUiState` を収集し、`TabsPagerContent`、`OpenBoardsList`、`OpenThreadsList`、`TabListBottomControls` へ状態と操作を渡す構成になっている。下部操作群には既に Haze が適用されており、リスト本体は `hazeSource` の内側に描画される。

既存検索 UI は `SearchBottomBar` にまとまっているが、これは `FlexibleBottomAppBar` を含む下部表示専用の Composable である。タブ一覧の検索バーは画面上部に表示するため、検索入力欄・戻るボタン・クリアボタン・音声入力ボタンの中身を共通化し、配置コンテナだけを用途別に分ける必要がある。

リストのスクロール状態は現在 `RemovableTabList` の内部 `rememberLazyListState()` に閉じている。検索解除時に検索前の位置へ戻すには、板一覧とスレッド一覧それぞれの `LazyListState` を上位で保持できるようにする必要がある。

## Goals / Non-Goals

**Goals:**

- タブ一覧右上から検索モードへ入り、画面上部の検索バーで板名・スレ名を検索できるようにする。
- 検索中は作成・ページ切替・更新/キャンセルの下部操作群を隠し、更新進捗インジケーター表示は維持する。
- タブ一覧上部にも Haze を適用し、下部操作群と視覚表現を揃える。
- `SearchBottomBar` の検索入力部分を共通化し、既存の板・スレッド画面検索 UI の機能を維持する。
- 大文字小文字、ひらがな/カタカナを区別しないタブ検索を提供する。
- 検索終了時に検索開始前のスクロール位置へ復元する。
- 戻る操作で検索モードを終了できるようにする。

**Non-Goals:**

- 検索結果のハイライト表示は対象外とする。
- 検索クエリの永続化、履歴、サジェストは対象外とする。
- 検索中の板/スレッドページ切替導線は下部切替非表示により提供しない。検索は検索開始時に表示しているページのリストに対して行う。
- タブ一覧以外の板画面・スレッド画面の検索仕様は変更しない。

## Decisions

### Decision 1: 検索モードと検索クエリは `TabsUiState` と `TabsViewModel` で管理する

`isSearchMode` と `searchQuery` を `TabsUiState` に追加し、`TabsViewModel` に検索開始、検索終了、検索クエリ更新の操作を追加する。タブ一覧画面では既存方針どおり上位 Composable で `TabsUiState` を一度だけ収集し、子 Composable へ値とイベントを渡す。

代替案として `TabScreenContent` 内の `remember` だけで検索状態を管理する方法もあるが、検索中の表示制御、戻る操作、テスト対象を画面状態として扱いにくくなるため採用しない。

### Decision 2: `SearchBottomBar` の中身を `SearchInputField` として共通化する

検索入力、閉じるボタン、クリアボタン、音声入力、フォーカス要求、IME Search 処理を `SearchInputField` 相当の共通 Composable へ切り出す。`SearchBottomBar` は `FlexibleBottomAppBar` の外枠を持つ既存用途のラッパーとして残し、タブ一覧では上部用の `TabListSearchTopBar` から同じ入力部品を利用する。

代替案として `SearchBottomBar` をそのまま上部へ配置する方法もあるが、下部バー用の Material コンポーネントと余白設計に依存しており、タブ一覧上部の Haze 表示と責務が混ざるため採用しない。

### Decision 3: 検索フィルタは純粋関数として切り出し、正規化には既存 `toHiragana()` を利用する

検索対象文字列とクエリを `lowercase().toHiragana()` で正規化して部分一致を行う。板タブは板名を必須対象とし、表示に使われるサービス名も検索対象に含める。スレッドタブはスレ名と板名を検索対象にする。

フィルタリングは `TabScreenContent` 付近で `TabsUiState` の元リストと検索クエリから導出し、リポジトリや永続データには影響させない。純粋関数として切り出すことで、かな変換や大小文字無視の挙動をユニットテストしやすくする。

### Decision 4: `LazyListState` を `RemovableTabList` の外から渡せるようにする

`RemovableTabList` に `listState: LazyListState = rememberLazyListState()` を追加し、既存呼び出し元の互換性を保つ。タブ一覧では板一覧用とスレッド一覧用の `LazyListState` を `TabScreenContent` で保持し、検索開始時に両方の `firstVisibleItemIndex` と `firstVisibleItemScrollOffset` を保存する。検索終了時は保存値に基づいて `scrollToItem` で復元する。

現在ページだけを保存する代替案もあるが、検索中にリスト再構成やページ状態が変化しても安全に戻せるよう、板・スレッド両方を保存する。

### Decision 5: 戻る操作は検索モードを長押し選択より優先して処理する

検索モード中は `BackHandler` により検索を終了する。検索開始時には長押し選択状態を解除し、検索中は長押し選択やスワイプ削除などのリスト操作が検索 UI と競合しないようにする。検索モードでない場合は既存の長押し選択解除の戻る操作を維持する。

## Risks / Trade-offs

- [Risk] `SearchBottomBar` の共通化で既存の板・スレッド画面検索 UI に回帰が発生する。 → 共通部品の引数を既存 API に合わせ、`SearchBottomBarPreview` と既存検索画面の動作確認を行う。
- [Risk] 検索中に絞り込みリストへ切り替えることで削除アニメーションやスクロールバーの状態が不自然になる。 → `RemovableTabList` の key を既存と同じに保ち、検索中も削除要求の消費処理を維持する。
- [Risk] 検索解除時に保存した index が現在のリスト件数を超える。 → 復元時は対象リストの最終 index 以内へ丸め、空リストではスクロール復元をスキップする。
- [Risk] 上部 Haze 領域がタブカードのタップを透過する。 → 下部操作群と同様に上部検索領域・検索ボタン領域で必要なタップを消費し、背面カード操作を発生させない。
- [Risk] 検索クエリ更新ごとに全タブをフィルタして再composeが増える。 → フィルタ処理は軽量な純粋関数に留め、必要に応じて `remember` / `derivedStateOf` で入力リストとクエリの変更時だけ再計算する。
