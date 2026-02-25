## Context

`ThreadScreen` リファクタリングにより画面内責務は分離されたが、`BoardScreen` 系との間では同等処理が依然として重複している。
特に以下 5 点は同じロジックを別実装で保持しているため、仕様差分が混入しやすい。

1. `ToTop` / `ToBottom` ジェスチャー実行
2. 末尾スクロール補助（viewport 更新待ち、末尾ターゲット算出）
3. `GestureHint.Invalid` の遅延リセット
4. `onGestureAction` の画面操作ディスパッチ
5. 画像ビューア遷移時の URL エンコードと route 組み立て

本変更は上記 5 点のみを共通化対象とし、一覧表示ロジックや pull-to-refresh 制御など画面固有処理は維持する。

## Goals / Non-Goals

**Goals:**
- 指定された 5 つの重複ロジックを共通 API に統合し、`ThreadScreen` 系と `BoardScreen` 系で同一実装を使う。
- 共通化後も既存挙動（遷移先、スクロール位置、ヒント表示タイミング、アクション結果）を不変に保つ。
- 画面ごとの責務（Thread 固有 popup 処理、Board 固有 pull-to-refresh）を侵食しない構造にする。

**Non-Goals:**
- `ThreadScreen` と `BoardScreen` の UI コンポーネントそのもの（リスト描画、カード描画）の統合。
- `Thread` 固有の下端更新（overscroll + haptic）ロジックの `Board` への展開。
- ジェスチャー割り当て仕様や画像ビューア画面仕様の変更。

## Decisions

- **共通化単位は「ユーティリティ + 小さな Composable effect」に分離する**
  - 純計算（末尾ターゲット算出、画像 route 生成）と副作用（invalid リセット）を分離し、依存を最小化する。
  - 代替案として巨大な `ScreenInteractionController` へ集約する案は、画面固有依存が混ざって再利用性が落ちるため採用しない。

- **ToTop/ToBottom 実行は画面共通 executor 関数へ統一する**
  - `listState`、`fallbackCount`、`showBottomBar`、`CoroutineScope` を受ける小さな実行 API を用意し、両画面の `when (action)` から呼ぶ。
  - 代替案として現状ロジックを複製維持する案は、将来の閾値変更・待機フレーム変更で片側漏れが発生しやすいため採用しない。

- **GestureAction ディスパッチは「共通部分 + 画面固有ハンドラ」で二段構成にする**
  - 共通部分（Refresh/Search/OpenTabList/OpenBookmarkList/OpenBoardList/OpenHistory/OpenNewTab/SwitchTab/CloseTabの枠）を共通化し、画面固有処理は callback で注入する。
  - 代替案として共通 `when` に全画面分岐を詰め込む案は、if 分岐の増加で可読性が落ちるため採用しない。

- **画像ビューア遷移は共通 route ビルダーを介して行う**
  - URL encode、`initialIndex` の clamp、`AppRoute.ImageViewer` 生成までを共通化し、各呼び出し側は `navController.navigate(route)` のみ実行する。
  - 代替案として `NavController` まで受け取る共通関数はテストしづらく責務が重くなるため採用しない。

## Risks / Trade-offs

- [Risk] 共通 API の引数が増え、呼び出し時の誤配線が起きる可能性。
  - Mitigation: 画面別の引数セットを data class で束ね、命名で用途を固定する。

- [Risk] 共通化で action 実行順（`showBottomBar` → スクロール）が変化する可能性。
  - Mitigation: 既存順序を仕様化し、共通 executor 内で順序固定する。

- [Trade-off] ファイル分割により参照箇所は増えるが、重複削減と挙動統一を優先する。

## Migration Plan

1. 末尾スクロール補助と invalid リセット effect を `ui/util` 側へ移す。
2. `ToTop` / `ToBottom` 実行 API を作成し、`ThreadScreen` と `BoardScreen` の gesture 分岐を置き換える。
3. 画像ビューア route ビルダーを導入し、`ThreadScreen` / `ThreadScaffold` / `BoardScaffold` の重複遷移を置き換える。
4. `onGestureAction` 共通ディスパッチを導入し、各 Scaffold の `when` を整理する。
5. 回帰確認（スクロール/遷移/ジェスチャー）と CI（build + unit test）を実施する。

## Open Questions

- 共通化後の配置を `ui/util` 直下に置くか、`ui/common/interaction` などの専用パッケージを作るか。
