## Context

現在のタブ一覧検索では、検索モードと検索クエリは `TabListViewModel` の `StateFlow` で管理されている。一方、検索前スクロール位置と前回検索クエリは `TabScreenContent` 内の `remember` に保持されている。

このため、検索中に他画面へ遷移して戻る、またはスレ画面/板画面から開いたタブ一覧 BottomSheet を閉じて再表示するなど、`TabScreenContent` の Composition が破棄・再作成される場面で復元用スクロール状態だけが失われる。検索状態だけが `TabListViewModel` に残ると、検索解除時に復元対象がなくなり、スクロール位置復元が動作しない。

新しい設計では `TabsViewModel` / `TabsUiState` を使わず、タブ一覧画面固有の一時 UI 状態は `TabListViewModel` に集約されている。この前提に合わせ、検索スクロール復元の状態と命令も `TabListViewModel` に移す。

## Goals / Non-Goals

**Goals:**
- 通常のタブ一覧、他画面から復帰したタブ一覧、板/スレ画面から開いたタブ一覧 BottomSheet のすべてで検索解除時のスクロール位置復元を安定させる。
- 検索状態、検索前スクロール位置、前回検索クエリ、スクロール命令の寿命を `TabListViewModel` に揃える。
- `LazyListState` の実体と `scrollToItem` の副作用実行は Composable 側に残し、ViewModel には index/offset と命令だけを保持する。
- 通常タブ一覧と BottomSheet の検索状態は、それぞれの `TabListViewModel` スコープで独立させる。
- BottomSheet dismiss 時に検索状態を閉じ、再表示時に検索クエリだけが残る不整合を防ぐ。

**Non-Goals:**
- タブセッション状態の正本である `TabSessionStore` に検索 UI 状態を移すこと。
- 通常タブ一覧と BottomSheet の検索状態を共有すること。
- `LazyListState` 自体を ViewModel に保持すること。
- タブ一覧のフィルタリング条件やカード表示仕様を変更すること。

## Decisions

### Decision 1: 検索復元状態は `TabListViewModel` に集約する

`TabScreenContent` の `remember` で保持していた検索前スクロールスナップショットと前回検索クエリ相当の状態を `TabListViewModel` に移す。検索モードと検索クエリも同じ ViewModel にあるため、復元状態だけが Composition 再生成で失われる不整合を防げる。

代替案として `rememberSaveable` で保持する方法もあるが、検索状態が ViewModel に残る設計との寿命差は解消できない。また BottomSheet dismiss/再表示時の状態整合性が呼び出し元の Composition に依存するため採用しない。

### Decision 2: ViewModel はスクロール位置の値と命令だけを保持する

ViewModel は `LazyListState` を直接保持せず、板一覧・スレッド一覧の `firstVisibleItemIndex` と `firstVisibleItemScrollOffset` を含むスナップショットを保持する。スクロール副作用は `TabScreenContent` が命令を受けて `scrollToItem` を実行する。

これにより、Compose UI オブジェクトを ViewModel に持ち込まず、UI 状態と副作用の責務を分離する。

### Decision 3: スクロール操作は consume 可能な命令として表現する

検索解除時の復元と検索クエリ変更時の先頭表示は、ViewModel から一回限りのスクロール命令として UI に渡す。UI は命令を実行した後、ViewModel の consume メソッドを呼び、同じ命令が再実行されないようにする。

命令は少なくとも以下を表現できるようにする。
- 完全リスト復帰後に板一覧・スレッド一覧の保存位置へ復元する命令
- 検索クエリ変更後に現在表示中ページの検索結果を先頭表示する命令

### Decision 3a: 復元命令は表示リスト更新後に実行する

検索解除時に `scrollCommand` を即時発行すると、UI がまだ検索結果リストを描画対象にしている途中状態で `scrollToItem` を実行し、その後の完全リスト再レイアウトでスクロール位置が上書きされる可能性がある。このため、安定性優先の追加修正では「検索状態を空へ更新する処理」と「保存位置へスクロールする副作用」を段階分離する。

ViewModel は検索解除時に保存済みスナップショットを復元待ち状態として保持し、UI は `searchQuery` が空で、板一覧・スレッド一覧が完全リストを描画対象に戻ったことを確認してから復元スクロールを実行する。実行後は復元待ち状態または命令を consume し、再Compositionで同じ復元を繰り返さない。

検索クエリ変更時の先頭表示も同じ考え方で扱う。クエリ更新と同じ瞬間に `scrollToItem(0)` を実行せず、UI が新しい検索結果リストを描画対象にした後、現在表示中ページだけを先頭表示する。

### Decision 4: BottomSheet dismiss 時に検索状態を閉じる

タブ一覧 BottomSheet は `showTabListSheet` によって Composition から外れる。閉じた後も `TabListViewModel` が呼び出し元 NavBackStackEntry に残る可能性があるため、dismiss 時に検索モードと検索クエリ、検索復元スナップショット、未消費スクロール命令を明示的にクリアする。

これにより、BottomSheet 再表示時は検索なしの初期状態から始まり、検索クエリだけが残って復元スナップショットがない状態を避ける。

### Decision 5: 通常タブ一覧と BottomSheet の検索状態は独立させる

通常タブ一覧と BottomSheet は異なる表示コンテキストで使われるため、検索状態は共有しない。`TabsScaffold` と `TabsBottomSheet` がそれぞれの `hiltViewModel<TabListViewModel>()` スコープで状態を持つ現状を維持し、そのスコープ内で復元状態まで一貫管理する。

## Risks / Trade-offs

- [Risk] スクロール命令が再Compositionで重複実行される → 実行後に必ず ViewModel へ consume を通知し、命令IDまたは nullable state で一回性を担保する。
- [Risk] 完全リスト復帰前に復元命令を実行するとフィルタ済みリスト上の index に対してスクロールしてしまう → 検索解除では復元待ち状態を保持し、検索クエリが空かつ完全リストが描画対象になった後の UI 側 `LaunchedEffect` で復元を処理する。
- [Risk] 検索クエリ変更直後に先頭表示を実行すると古い検索結果リスト上でスクロールしてしまう → 新しい検索クエリに対応した表示リストが反映された後、現在ページの `LazyListState` だけを先頭表示する。
- [Risk] 保存済み index がタブ削除などで範囲外になる → 復元時に現在の完全リストサイズへ `coerceIn` する。
- [Risk] BottomSheet dismiss 時に検索を保持したい利用者期待と異なる → BottomSheet は一時的な選択 UI として扱い、閉じたら検索状態を破棄する仕様を明文化する。
- [Risk] ViewModel の状態項目が増える → スクロール復元用の data class / sealed interface を分け、`TabListUiState` では UI が必要な命令だけを公開する。
