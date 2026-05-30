## Context

`TabScreenContent.kt` はタブ一覧画面の最上位 Composable として、画面レイアウト、`TabsUiState` 収集、pager、下部操作群、長押し overlay、floating card アニメーション、タブアクションメニュー、詳細 BottomSheet、URL 入力ダイアログ、URL 解析、route 正規化、ナビゲーションまでを一つの関数内で扱っている。

この変更では、現在の floating card enter / exit アニメーションは維持し、まず責務分離によって見通しと修正安全性を改善する。アニメーション仕様や操作結果の変更は行わない。

## Goals / Non-Goals

**Goals:**

- `TabScreenContent` を画面全体の構成に集中させ、各領域の描画と操作接続を小さな Composable / helper へ分離する。
- `TabsUiState` の収集を画面上位に寄せ、子 Composable へ必要な state と callback を明示的に渡す。
- URL 入力処理を Composable の長い inline 分岐から分離し、検証状態の開始/終了を例外時にも安全に扱う。
- 既存の長押し選択、floating card アニメーション、詳細 BottomSheet、URL 入力後ナビゲーションのユーザー向け挙動を維持する。

**Non-Goals:**

- floating card の enter / exit アニメーション仕様を変更しない。
- タブ固定、詳細 BottomSheet、URL 判定対象パターンの機能追加はしない。
- Room schema、Repository、Coordinator のデータ仕様は変更しない。
- 大規模な画面構成刷新や navigation framework の変更は行わない。

## Decisions

### 1. `TabScreenContent` は画面の組み立てに寄せる

`TabScreenContent` は `TabsUiState` を収集し、主要レイヤーの配置を担当する。長押し overlay、floating card、タブアクションメニュー、BottomSheet、URL ダイアログは、それぞれ小さな private Composable または専用関数へ切り出す。

切り出し候補は次の通りとする。

- `TabLongPressOverlayLayer`: dim overlay、floating card、action menu、BackHandler をまとめる。
- `SelectedTabFloatingLayer`: board/thread floating card の共通配置、座標変換、scale 適用を扱う。
- `TabDetailBottomSheets`: detail state から `BoardInfoBottomSheet` / `ThreadInfoBottomSheet` を表示する。
- `TabUrlDialogHost`: URL ダイアログの表示、入力イベント、validation 表示を扱う。

### 2. アニメーション状態は現状維持しつつ局所化する

floating card の enter / exit アニメーションは現在の `Animatable` ベースを維持する。責務分離ではアニメーションを削除・再設計せず、状態と描画を `TabLongPressOverlayLayer` または専用 holder に閉じ込める。

`TabScreenContent` 直下に散らばる `floatingBoardTab`、`floatingThreadTab`、`floatingBounds`、`isFloatingExiting`、`floatingScale` は、同じ挙動を保ったまま一箇所に集める。ページ切替、選択解除、詳細表示時の既存挙動は変更しない。

### 3. `TabsUiState` は上位で一度収集し、子へ渡す

`TabScreenContent` で `collectAsStateWithLifecycle()` を使って `TabsUiState` を収集し、`TabsPagerContent`、`OpenBoardsList`、`OpenThreadsList` は `TabsUiState` または必要な部分 state を引数で受け取る。これにより、同じ `StateFlow` を画面ツリー内で複数回収集する構造を避ける。

Preview 用に `TabsViewModel? = null` を production Composable に持ち込むのではなく、Preview では state と callback を渡せる下位 Composable を使う。

### 4. URL 入力処理は typed result に分ける

URL ダイアログの `onOpen` から、URL 種別ごとの詳細処理を直接 inline で実行しない。候補として `TabsViewModel.openUrlInput(url: String)` または専用 handler を追加し、結果を sealed class で表す。

例:

```text
UrlOpenResult
├── NavigateBoard(route)
├── NavigateThread(route)
└── Invalid(message)
```

ViewModel が `NavHostController` を直接持たないようにし、Composable は typed result を受け取って既存の `navigateToBoard` / `navigateToThread` を呼ぶ。非同期処理は `try/finally` または ViewModel 側の state 管理で `finishUrlValidation()` 相当が必ず実行される構造にする。

### 5. 小さく段階的に分割する

責務分離は一度に全ロジックを移動せず、外部挙動を確認しやすい単位で進める。優先順は、URL 処理の安全化、BottomSheet host 切り出し、長押し overlay layer 切り出し、pager/list state 受け渡し整理の順とする。

## Risks / Trade-offs

- 責務分離中に floating card の表示タイミングが変わる可能性がある → アニメーション仕様を変更しないことをタスクに明記し、分割前後で同じ state transition を使う。
- `uiState` の受け渡し変更で子 Composable の Preview が壊れる可能性がある → state/callback を受け取る下位 Composable を Preview 対象にする。
- URL 処理を ViewModel 側へ寄せすぎると navigation 依存が ViewModel に漏れる可能性がある → ViewModel は typed result を出し、実際の navigation API 呼び出しは画面側に残す。
- 分割だけの変更でも diff が大きくなる可能性がある → 機能ごとに段階的に切り出し、各段階で CI を確認する。

## Migration Plan

1. URL 入力処理に `try/finally` 相当の完了保証を追加し、可能なら typed result 化する。
2. BottomSheet 表示を `TabDetailBottomSheets` へ切り出す。
3. 長押し overlay / floating card / action menu を `TabLongPressOverlayLayer` へ切り出す。
4. `TabsPagerContent` / `OpenBoardsList` / `OpenThreadsList` へ `uiState` と callback を明示的に渡し、多重 collect を減らす。
5. CI で build と unit test を確認する。

## Open Questions

- URL 入力処理の typed result を `TabsViewModel` の `StateFlow` で公開するか、suspend 関数の戻り値として画面側で扱うか。
- floating card アニメーション holder を private Composable 内の `remember` に閉じるか、専用 state holder class として切り出すか。
