## Context

現在のタブ一覧検索では、検索クエリに応じて通常リストと検索結果リストの item を切り替えながら、同じ `LazyListState` を使って表示している。この方式では、検索結果を表示した時点で通常リストのスクロール状態も検索結果リストの位置に更新される。

そのため、検索解除時に元の通常リスト位置へ戻すには、検索開始前の index/offset を保存し、検索解除後に `requestScrollToItem` で復元する必要がある。復元自体は可能だが、リスト内容の差し替え、再Composition、BottomSheet の破棄/再表示、検索クエリ変更による先頭表示が同時に起きると、スクロール副作用の実行タイミングを細かく制御する必要がある。

計画を見直し、通常リストと検索結果リストを別々の表示状態として扱う。通常リスト用の `LazyListState` は検索中に使わず、検索結果リスト用の `LazyListState` だけを検索中に使う。検索解除時は通常リストへ表示を戻すだけで、通常リストのスクロール位置が自然に維持される。

## Goals / Non-Goals

**Goals:**
- 通常のタブ一覧、他画面から復帰したタブ一覧、板/スレ画面から開いたタブ一覧 BottomSheet のすべてで、検索解除時に通常リストのスクロール位置を安定して維持する。
- 通常リストと検索結果リストの `LazyListState` を分離し、検索中のスクロールが通常リストの位置を上書きしないようにする。
- 検索クエリが空のときは通常リスト、非空のときは検索結果リストを表示する。
- 検索開始時または検索クエリ変更時は、現在表示中ページの検索結果リストだけを先頭表示する。
- BottomSheet dismiss 時に検索状態を閉じ、再表示時に検索クエリだけが残る不整合を防ぐ。
- 通常タブ一覧と BottomSheet の検索状態は、それぞれの `TabListViewModel` スコープで独立させる。

**Non-Goals:**
- タブセッション状態の正本である `TabSessionStore` に検索 UI 状態を移すこと。
- 通常タブ一覧と BottomSheet の検索状態を共有すること。
- `LazyListState` 自体を ViewModel に保持すること。
- タブ一覧のフィルタリング条件やカード表示仕様を変更すること。
- 検索クエリごとに過去の検索結果スクロール位置を永続保存すること。

## Decisions

### Decision 1: 通常リストと検索結果リストの `LazyListState` を分離する

板一覧・スレッド一覧それぞれに、通常表示用と検索結果表示用の `LazyListState` を持たせる。

```text
boardNormalListState  -> 通常の板タブ一覧
boardSearchListState  -> 検索結果の板タブ一覧
threadNormalListState -> 通常のスレッドタブ一覧
threadSearchListState -> 検索結果のスレッドタブ一覧
```

検索中は検索結果用 state だけを使うため、通常リスト用 state は検索結果スクロールの影響を受けない。検索解除時は通常リストと通常用 state を再表示するだけで、検索開始前の位置へ自然に戻る。

### Decision 2: 表示切り替え条件は検索クエリの非空判定にする

検索 UI が開いていても、検索クエリが空の間は通常リストを表示する。検索クエリが非空になったときだけ検索結果リストへ切り替える。

これにより、検索欄を開いただけで通常リストの表示 state が検索用 state へ切り替わり、見た目のスクロール位置が変わることを避ける。

### Decision 3: 通常リスト復元用 snapshot と復元待ち状態は不要にする

通常リストの `LazyListState` が検索中に変更されないため、検索解除時に index/offset snapshot を使って復元する必要はない。`TabSearchScrollSnapshot`、通常リスト復元用の pending state、復元用 `LaunchedEffect` は削除対象とする。

ViewModel は `LazyListState` を保持しない。検索状態と、検索結果リストを先頭表示する一回限りの要求だけを管理する。

### Decision 4: 検索結果リストの先頭表示だけ一回限りの要求として扱う

検索クエリが空から非空へ変わったとき、または非空から別の非空へ変わったときは、現在表示中ページの検索結果リストを先頭表示する。

この要求は `TabListViewModel` から UI へ nullable state として公開し、UI が実行後に consume する。要求には対象ページと検索クエリを含め、古い検索クエリ向けの要求を誤実行しないようにする。

### Decision 5: 検索結果の先頭表示は検索結果リストが表示対象になってから実行する

検索クエリ更新と同じ瞬間にスクロールすると、まだ通常リストまたは古い検索結果リストが描画対象になっている可能性がある。UI は要求に含まれる検索クエリと現在の検索クエリが一致し、検索結果リストが表示対象になった後に、対象ページの検索用 `LazyListState` へ先頭表示を適用する。

先頭表示は UX と安定性の両方を見て選択する。検索結果リストの初期表示では `requestScrollToItem(0)` を基本とし、視覚的な滑らかさを優先する場合は `animateScrollToItem(0)` を検討する。

### Decision 5a: 検索入力の text と selection を同じ UI 状態スコープで保持する

検索結果から板/スレ画面へ遷移し、戻る操作でタブ一覧へ復帰した場合、検索クエリ文字列自体は `TabListViewModel` に残る一方、`BasicTextField(value: String)` の内部 selection state は Composition 再生成で初期化される。このため、検索バーの text だけでなく selection も `TabListViewModel` 側の UI 状態として保持する。

UI は `TextFieldValue` 相当の state を受け取り、入力文字列とカーソル位置をまとめて更新する。これにより、検索中に別画面へ移動して戻った後も、検索バーのカーソル位置を復元できる。

### Decision 5a-2: 板画面・スレッド画面の検索入力も `TextFieldValue` で保持する

板画面・スレッド画面の `SearchBottomBar` は `String` ベースの `SearchInputField` 互換 API を経由している。この互換 API は `TextFieldValue(text = searchQuery, selection = TextRange(searchQuery.length))` を毎回生成し、`onValueChange` では `.text` だけを ViewModel へ渡すため、IME が未確定変換範囲として使う `composition` が破棄される。

日本語入力などでは `composition` が消えると変換前文字列が即確定され、変換候補を選べなくなる。このため、板画面・スレッド画面も検索入力状態を `TextFieldValue` として ViewModel/UiState に保持し、`BasicTextField` から渡される `TextFieldValue` をそのまま保存する。既存の絞り込み処理やハイライト処理は、派生プロパティ `searchQuery = searchInputValue.text` を通して文字列を参照する。

`SearchBottomBar` は `TextFieldValue` ベースの API を主経路に変更する。`String` ベースの `SearchInputField` 互換 API は、移行完了後に削除するか、限定的な簡易用途として残す場合でも IME composition を扱えないことを明確にする。

### Decision 5b: 検索バーのフォーカス要求は一回限りの要求として扱う

`LaunchedEffect(Unit)` によるフォーカス要求は、検索バー Composable が Composition に再登場するたびに再実行される。検索結果から別画面へ遷移して戻ると、検索モード継続中でも再フォーカスが走り、selection が意図せず変化する要因になる。

このため、検索バーのフォーカス要求は `enterSearchMode()` でだけ発行される一回限りの UI 要求として扱う。UI は要求を受けて `FocusRequester.requestFocus()` とキーボード表示を実行した後、要求を consume する。これにより、検索モードへ入った直後だけフォーカスし、戻る操作による再Compositionでは再フォーカスしない。

### Decision 5c: 検索結果が 0 件のときは空状態メッセージへ切り替える

検索クエリが非空の間は検索結果リストを表示するが、フィルタ結果が 0 件のケースでは空の `LazyColumn` を描画すると、ユーザーからは何も起きていないように見える。このため、検索結果表示中かつ現在ページのフィルタ結果が空のときは、リストの代わりに中央寄せの空状態メッセージを表示する。

この空状態は通常リストが 0 件であることを表すものではなく、検索クエリに一致する結果が存在しないことだけを示す。表示は `TabsPagerContent` 側で現在ページごとに判定し、通常リストの表示条件や検索結果先頭表示要求の処理は変更しない。

### Decision 5d: 通常/検索結果あり/検索結果なしを単一の表示状態としてフェード切り替えする

通常リストと検索結果リストの切り替えに加えて、検索結果表示中には「結果あり」と「結果なし」の差し替えも発生する。これを外側と内側の二段 `AnimatedContent` で表現すると、検索解除時に検索クエリのクリアと表示モード変更が同一フレームで起きた場合、消えゆく検索側コンテンツが一瞬だけ「検索結果あり」へ再評価される余地がある。

このため、表示状態を `Normal` / `SearchResults` / `SearchEmpty` の 3 値へ統合し、同じ `AnimatedContent` でフェード切り替えする。これにより、検索結果なし状態から戻るときの遷移は `SearchEmpty -> Normal` となり、`SearchEmpty -> SearchResults -> Normal` のような途中状態を挟まない。

### Decision 6: BottomSheet dismiss 時に検索状態を閉じる

タブ一覧 BottomSheet は `showTabListSheet` によって Composition から外れる。閉じた後も `TabListViewModel` が呼び出し元 NavBackStackEntry に残る可能性があるため、dismiss 時に検索モード、検索クエリ、検索結果先頭表示要求を明示的にクリアする。

これにより、BottomSheet 再表示時は検索なしの初期状態から始まり、検索クエリだけが残る状態を避ける。

### Decision 7: 通常タブ一覧と BottomSheet の検索状態は独立させる

通常タブ一覧と BottomSheet は異なる表示コンテキストで使われるため、検索状態は共有しない。`TabsScaffold` と `TabsBottomSheet` がそれぞれの `hiltViewModel<TabListViewModel>()` スコープで状態を持つ現状を維持し、そのスコープ内で検索結果先頭表示要求まで一貫管理する。

### Decision 8: `TabListViewModel` の画面 UI 状態は `TabListUiState` を正本として保持する

`combine(vararg)` で多数の `MutableStateFlow` を束ねると、変換ラムダは `Array<Any?>` ベースになり、添字アクセスとキャストへ依存する。状態項目の追加や並び替えで実行時型エラーを起こしやすく、`TabListUiState` 全体としてどの値が同時更新されるかも追いにくい。

このため、`TabListViewModel` の画面固有 UI 状態は `MutableStateFlow(TabListUiState())` を正本として保持し、各操作は `copy` ベースで更新する。これにより、検索状態・選択状態・BottomSheet 状態・URL ダイアログ状態を型安全にまとめて扱い、関連項目の同時更新も1回の state 更新として明示できる。

### Decision 9: タブ一覧画面全体で共有する余白・アニメーションは defaults へ集約する

タブ一覧まわりでは、上部検索領域の高さ、下部操作群ぶんのリスト余白、削除アニメーション時間、通常/検索切り替えフェード時間などが複数ファイルに分散しやすい。これらが各所でハードコードされると、見た目の調整時に値の同期漏れが起こりやすく、コメントで計算式を追う必要も生じる。

このため、タブ一覧画面全体で共有したい寸法とアニメーション時間は `TabListDefaults` のような共通定義へ集約する。個々のカード内部に閉じた細かい装飾値までは無理に共通化せず、画面横断で整合させたい値に限定して集約する。

## Risks / Trade-offs

- [Risk] `LazyListState` が板/スレッド × 通常/検索で 4 つになる → タブ一覧の状態量としては小さく、復元用 snapshot / pending state を減らせるため許容する。
- [Risk] 検索欄を開いただけで検索用 state へ切り替わると見た目の位置が変わる → 表示切り替え条件を `searchQuery.isNotBlank()` にする。
- [Risk] 検索クエリ変更直後に古い検索結果リストへ先頭表示してしまう → 先頭表示要求に query を含め、UI 側で現在の query と一致する場合だけ実行する。
- [Risk] 検索文字列は残るがカーソル位置だけ失われる → text と selection を同じ UI 状態で保持し、`TextFieldValue` 相当の値として UI へ渡す。
- [Risk] `String` ベースの検索入力アダプターが IME composition を捨て、日本語変換前の文字が即確定される → 板・スレ画面も `TextFieldValue` を正本として保持し、`composition` をそのまま伝播する。
- [Risk] 戻る操作のたびに検索バーへ再フォーカスして selection が変わる → フォーカスは `LaunchedEffect(Unit)` ではなく一回限り要求として発行し、実行後に consume する。
- [Risk] 検索結果 0 件時に無表示だと検索失敗か描画不具合か判別しづらい → 検索結果専用の空状態メッセージを中央表示する。
- [Risk] 外側/内側の二段アニメーションが検索解除時に競合し、消え際の検索側 UI が別状態へ再評価される → 通常/検索結果あり/検索結果なしを単一の表示状態へ統合する。
- [Risk] `combine(vararg)` の配列キャストが状態追加や順序変更に弱い → `TabListUiState` を単一 `MutableStateFlow` の正本として保持し、`copy` 更新へ寄せる。
- [Risk] タブ一覧の余白やアニメーション時間が各ファイルで微妙にズレる → 画面横断で共有したい値を defaults へ集約する。
- [Risk] 検索結果リストのスクロール位置を次回検索時に保持するかが曖昧になる → この変更では検索クエリ変更時に検索結果リストを先頭表示する仕様を維持し、クエリ別の検索結果位置保持は扱わない。
- [Risk] 通常リストと検索結果リストを別 Composable として切り替えると UI ツリーが増える → 既存のリスト Composable を再利用し、渡す items と `LazyListState` だけを切り替える。
- [Risk] BottomSheet dismiss 時に検索を保持したい利用者期待と異なる → BottomSheet は一時的な選択 UI として扱い、閉じたら検索状態を破棄する仕様を維持する。
