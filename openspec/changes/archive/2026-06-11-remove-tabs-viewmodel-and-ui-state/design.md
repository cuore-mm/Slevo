## Context

現状では `TabSessionStore` がタブセッション状態の正本になり、`TabListViewModel` がタブ一覧画面固有状態を管理している。一方で、既存画面との互換性のため `TabsViewModel` と `TabsUiState` が残っており、`BoardScaffold`、`ThreadScaffold`、`BbsRouteScaffold`、`TabScreenContent` などが `tabsViewModel.uiState` または `tabsViewModel.sessionUiState` を参照している。

現在の `TabsUiState` は UI 状態という名前でありながら、実態は開いているタブ一覧、ロード状態、更新状態、新着レス数、URL検証中フラグをまとめたセッション状態 DTO になっている。この名称と責務のズレが、今後の変更で画面固有状態を再び Activity スコープへ戻すリスクを生む。

## Goals / Non-Goals

**Goals:**

- `TabsViewModel` を削除し、Activity スコープの ViewModel をタブセッション API として使う構造を廃止する。
- `TabsUiState` を削除し、タブセッション状態は `TabSessionStore` の `StateFlow` を直接収集する。
- タブ一覧画面固有状態は `TabListViewModel` / `TabListUiState` に限定する。
- `BoardScaffold`、`ThreadScaffold`、`BbsRouteScaffold`、navigation などの既存利用箇所を、まずは既存の `TabsViewModel` 受け渡し経路を保ったまま `TabSessionStore` ベースへ移行する。
- ユーザー向け挙動を変更しない。

**Non-Goals:**

- `TabSessionStore` の内部実装や coordinator の保存ロジックを大きく作り替えない。
- タブ一覧 UI の表示仕様や検索アルゴリズムを変更しない。
- `TabListViewModel` を削除しない。
- URL解決や Deep Link の仕様を変更しない。

## Decisions

### 1. `TabsViewModel` は段階移行ではなく最終的に削除する

`TabsViewModel` は現在、`TabSessionStore` への委譲ラッパーとして機能している。残し続けると「タブセッションは ViewModel が所有する」という誤解が残るため、この変更では最終成果として削除する。

代替案として `TabsViewModel` を `TabSessionViewModel` にリネームして残す案もあるが、Activity retained な非 ViewModel コンポーネントへ移した意図が曖昧になるため採用しない。

### 2. 各画面は必要な `TabSessionStore` の Flow を画面上位で収集する

`TabsUiState` 相当の巨大な集約 DTO は作らず、画面ごとに必要な Flow を収集する。

- `BoardScaffold`: `boardLoaded`, `openBoardTabs`, `boardCurrentPage`
- `ThreadScaffold`: `threadLoaded`, `openThreadTabs`, `threadCurrentPage`
- `TabScreenContent`: `openBoardTabs`, `openThreadTabs`, `newResCounts`, `isRefreshing`, `refreshProgress`, `boardLoaded`, `threadLoaded`
- `BbsRouteScaffold`: URL検証状態はローカルまたは専用状態へ移し、タブセッション状態から外す

必要に応じて画面内だけの private な集約データクラスを作ることは許容するが、公開 API として `TabsUiState` の後継を作らない。

### 3. `TabSessionStore` は既存の `TabsViewModel` 受け渡し経路へ素直に置換する

この変更では、子 Composable への受け渡し制限や callback 分解を同時に進めない。既存の `TabsViewModel` 引数を `TabSessionStore` 引数へ置き換え、`tabsViewModel.*` 呼び出しを `tabSessionStore.*` 呼び出しへ移すことを優先する。

`MainActivity` では `TabsViewModel by viewModels()` を削除し、`@Inject lateinit var tabSessionStore: TabSessionStore` を追加する。`AppScaffold`、`AppNavGraph`、`BoardScaffold`、`ThreadScaffold`、`TabsScaffold`、`TabsBottomSheet`、navigation 関連 Composable へは、現在 `TabsViewModel` を渡している経路と同じ経路で `TabSessionStore` を渡す。

子 Composable に `TabSessionStore` が深く渡ることは、この変更では許容する。後続の整理で必要に応じて状態値と操作ラムダへ分解する。

### 4. `BbsRouteScaffold` の URL検証状態はローカル `rememberSaveable` へ移す

`TabsUiState.isUrlValidating` はタブセッションではなく URL入力ダイアログの画面状態である。`BbsRouteScaffold` ではすでに `showUrlDialog` と `urlError` をローカル状態で保持しているため、`isUrlValidating` も同じ箇所の `rememberSaveable` 状態へ移す。

この分離により `TabsUiState` を削除しても、URL入力のローディング表示は維持できる。

### 5. navigation helper は `TabsViewModel` ではなくセッション操作インターフェースを受け取る

`navigateToBoard` / `navigateToThread` などが `TabsViewModel?` を受け取っている場合、まずは `TabSessionStore?` を受け取る形へ変更する。正規化関数ラムダへの分解は後続整理として扱う。

操作の責務を明確にするため、navigation helper が直接 ViewModel 型に依存する構造は解消する。

## Risks / Trade-offs

- [Risk] `TabSessionStore` を多くの画面へ渡すことで引数が増える。  
  → この変更では `TabsViewModel` 削除を優先し、既存の受け渡し経路を保って機械的に置換する。子 Composable の callback 分解は後続整理で行う。

- [Risk] `TabsUiState` 削除により複数 Flow の収集箇所が増え、recomposition が増える可能性がある。  
  → 画面ごとに必要な Flow のみ収集し、重い派生値は `remember` / `derivedStateOf` または ViewModel 側で集約する。

- [Risk] `BbsRouteScaffold` の URL検証状態を `rememberSaveable` へ移す際にローディング表示が崩れる。  
  → URL入力ダイアログの開閉、検証中、エラー表示のシナリオを手動確認項目に含める。

- [Risk] navigation helper の引数変更で Deep Link や履歴/ブックマーク経由の遷移に影響する。  
  → 既存の `handle-deep-link`、`resolve-url-routing`、`navigation-route-normalization` 関連テストを更新し、代表経路を手動確認する。

## Migration Plan

1. `TabsViewModel` の利用箇所を分類する。
   - セッション状態参照
   - セッション操作
   - navigation 正規化
   - URL検証状態
2. `MainActivity` で `TabsViewModel by viewModels()` を削除し、`TabSessionStore` を Hilt injection する。
3. `AppScaffold` / `AppNavGraph` / 各 Scaffold / 子 Composable の引数を、既存の `TabsViewModel` 経路を保ったまま `TabSessionStore` へ置き換える。
4. `BoardScaffold` / `ThreadScaffold` / `TabScreenContent` を `TabSessionStore` の Flow 収集へ移行する。
5. `BbsRouteScaffold` の URL検証状態をローカル `rememberSaveable` 状態へ移す。
6. navigation helper の `TabsViewModel` 依存を `TabSessionStore` 引数へ置換する。
7. `TabsViewModel.kt` と `TabsUiState.kt` を削除する。
8. テストと手動確認を実施する。

## Open Questions

- `TabSessionStore` を深い子 Composable へ渡し続ける箇所を、後続整理でどこまで状態値・操作ラムダへ分解するか。
- navigation helper の `TabSessionStore` 引数を、後続整理で正規化・タブ確保操作の関数型引数へ分解するか。
