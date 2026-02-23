## Why

`ThreadScreen` が 1 つの Composable に表示構築、スクロール副作用、ジェスチャー処理、メニュー/ダイアログ制御を集約しており、可読性と変更容易性が低下している。
今後の機能追加や不具合修正で回帰リスクを抑えるため、機能を変えずに責務境界を明確化する計画が必要である。

## What Changes

- `ThreadScreen` を画面オーケストレーション中心へ縮小し、副作用群（最終既読更新、自動スクロール、下端更新判定）を専用単位へ分離する。
- 投稿リスト描画を責務別コンポーネントへ分割し、投稿行描画・区切り線・新着バー・スクロールバー分岐の見通しを改善する。
- ポップアップ基準座標計算や画像ビューア遷移準備などの重複ロジックを補助関数へ集約する。
- 既存の UI 挙動、操作フロー、遷移条件、表示結果を維持し、ユーザー向け機能変更は行わない。

## Capabilities

### New Capabilities

- `thread-screen-composition`: スレッド画面実装が責務分離された構成を維持し、挙動不変でリファクタリングできることを定義する。

### Modified Capabilities

- なし

## Impact

- Affected specs:
  - `openspec/specs/thread-screen-composition/spec.md`
- Affected code:
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScreen.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/` 配下の関連Composable/補助関数
  - 必要に応じて `app/src/main/java/com/websarva/wings/android/slevo/ui/util/` 配下の共通ジェスチャー補助
- User impact:
  - 画面機能・見た目・操作に変更はなく、保守性改善のみを提供
