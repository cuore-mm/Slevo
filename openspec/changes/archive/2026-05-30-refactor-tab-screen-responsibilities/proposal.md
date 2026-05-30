## Why

`TabScreenContent.kt` が長押しメニュー、floating card、BottomSheet、URL ダイアログ、URL 解析、ナビゲーションを一箇所で扱っており、画面描画と操作制御の責務が混ざっている。戻るアニメーションの挙動は維持したまま、責務を分割して読みやすさ、テストしやすさ、今後の修正安全性を上げる。

## What Changes

- `TabScreenContent` から長押し overlay / floating card / action menu / BottomSheet / URL ダイアログ周辺の描画と制御を小さな Composable または状態ホルダーへ分離する。
- floating card の enter / exit アニメーション仕様は現状維持し、責務分離だけで見た目・操作タイミングを変えない。
- URL 入力からの URL 判定、非同期解決、route 正規化、ナビゲーション後処理を整理し、`finishUrlValidation()` が例外時にも呼ばれる構造にする。
- `TabsPagerContent` / `OpenBoardsList` / `OpenThreadsList` の `uiState` 受け渡しを整理し、同じ `StateFlow` の多重 collect を減らす。
- BottomSheet 表示用 state と長押し選択 state の分離方針は維持し、詳細表示の挙動を変えない。
- **BREAKING** なし。ユーザー向け機能・画面表示・操作結果は現状維持する。

## Capabilities

### New Capabilities
- なし

### Modified Capabilities
- `tablist-ui`: タブ一覧画面の長押し overlay、floating card、action menu、BottomSheet 表示の外部挙動を維持しながら責務を分離する。
- `handle-url-input`: URL 入力処理の外部挙動を維持しながら、検証状態の完了保証とナビゲーション処理の責務を整理する。

## Impact

- UI: `TabScreenContent`, `TabsPagerContent`, `OpenBoardsList`, `OpenThreadsList`, `UrlOpenDialog` 周辺
- 状態管理: `TabsUiState`, `TabsViewModel` の URL 開始処理・詳細表示 state・長押し選択 state の受け渡し
- ナビゲーション: URL 入力後の `navigateToBoard` / `navigateToThread` 呼び出し経路
- テスト: URL 入力処理、長押しメニュー表示、詳細 BottomSheet 表示、floating card アニメーション挙動の回帰確認
