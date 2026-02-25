## Why

スレッド画面の投稿メニューとダイアログが主投稿用とポップアップ投稿用で別管理になっており、挙動差分が混入しやすい。
同一の操作結果と保守性を確保するため、表示責務を統合する必要がある。

## What Changes

- `ThreadScreen` と `ReplyPopup` からの投稿メニュー/ダイアログ要求を共通ホストへ集約する。
- `PostMenuSheet` と `PostItemDialogs` の表示責務を `ThreadScaffold` 側に統一する。
- 投稿メニューとダイアログのイベント経路を一本化し、表示対象の違いによる分岐を排除する。

## Capabilities

### New Capabilities
- `thread-post-overlay-unification`: 主投稿とポップアップ投稿のメニュー/ダイアログを共通ホストで管理する。

### Modified Capabilities
- なし

## Impact

- Affected specs:
  - `openspec/changes/unify-thread-post-overlays/specs/thread-post-overlay-unification/spec.md`
- Affected code:
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScreen.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/res/PostItemDialogs.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/sheet/PostMenuSheet.kt`
- User impact:
  - 操作結果は変更せず、主投稿とポップアップ投稿で同一挙動を維持する
