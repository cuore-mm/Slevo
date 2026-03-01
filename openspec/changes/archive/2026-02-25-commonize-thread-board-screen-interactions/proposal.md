## Why

`ThreadScreen` 系と `BoardScreen` 系で同じ UI 振る舞いのロジックが複数箇所に重複しており、片側修正時に差分が発生しやすい。
ジェスチャー挙動と画像ビューア遷移の保守コストを下げるため、共通化対象を明示した計画が必要である。

## What Changes

- `ToTop` / `ToBottom` の実行ロジックを `ThreadScreen` と `BoardScreen` で共通利用できる単位へ抽出する。
- 末尾スクロール補助（viewport 更新待ち・末尾インデックス算出）を画面共通のユーティリティへ統一する。
- `GestureHint.Invalid` の一定時間後リセット処理を画面共通の effect として再利用する。
- `ThreadScaffold` と `BoardScaffold` の `onGestureAction` 分岐を共通ディスパッチ関数へ整理する。
- 画像ビューア遷移（URL encode + route 生成 + navigate）を共通ヘルパーに統一し、`ThreadScreen` / `ThreadScaffold` / `BoardScaffold` で再利用する。

## Capabilities

### New Capabilities
- `shared-screen-interactions`: 画面横断で共通化するジェスチャー実行・ヒント表示・画像遷移ロジックを定義する。

### Modified Capabilities
- なし

## Impact

- Affected specs:
  - `openspec/changes/commonize-thread-board-screen-interactions/specs/shared-screen-interactions/spec.md`
- Affected code:
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScreen.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/board/screen/BoardScreen.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/board/screen/BoardScaffold.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/util/` 配下の共通ヘルパー
- User impact:
  - 画面挙動は変更せず、保守性と一貫性を向上させる
