# Change: URL処理フローの共通化

## Why
- Deep Link、URL入力、スレ内リンクでURL判定ロジックが分散しており、挙動の不一致や修正漏れが起きやすい。
- `docs/external/5ch.md` に定義された入力URLパターンと現行実装の判定が一致していない箇所がある。
- 入口ごとにエラー通知UIは維持しつつ、解析ロジックを共通化して保守性を高めたい。

## What Changes
- URL構造の解析を担う共通リゾルバ（URL解決）を追加し、各入口から利用する。
- 入口ごとの許可ポリシーで「遷移可/不可」を判定し、エラー通知は現行のまま維持する。
- URL入力は `docs/external/5ch.md` の入力URLパターン（A〜D）に準拠する。
- dat URL は全入口で非対応とする。

## Impact
- Affected specs:
  - `handle-deep-link`（既存要件の修正）
  - `resolve-url-routing`（新規）
  - `handle-url-input`（新規）
  - `handle-thread-link`（新規）
- Affected code (予定):
  - `ui/util/UrlUtils.kt` / `ui/util/DeepLinkUtils.kt`
  - `ui/navigation/DeepLinkHandler.kt`
  - `ui/tabs/TabScreenContent.kt`
  - `ui/thread/res/PostItemBody.kt`
- リスク:
  - 入力パターンの許可/拒否の変更による既存挙動の差異
  - itest URLのホスト解決失敗時の扱い
