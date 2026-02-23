## Why

スレッド画面のプルアップ更新は、最下部へスクロールした慣性の延長でも発火してしまい、意図せず更新が走ることがある。Issue #258 で求められている操作感に合わせ、更新発動条件とフィードバックを見直す必要がある。

## What Changes

- 最下部でスクロールが停止している状態からの上方向ドラッグのみ、プルアップ更新の判定対象にする。
- 更新閾値を超えた瞬間に触覚フィードバックを発生させ、発動可能状態を明確に伝える。
- 既存の矢印アイコン表示を廃止し、`ContainedLoadingIndicator` を基準とした統一インジケーターへ置き換える。
- インジケーターバーを画面下部表示として維持しつつ、下部UIとの重なりを考慮した表示ルールを定義する。

## Capabilities

### New Capabilities
- `thread-pull-up-refresh`: スレッド画面下端でのプルアップ更新の発動条件、触覚フィードバック、インジケーター表示を定義する。

### Modified Capabilities
- なし

## Impact

- 影響箇所: `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScreen.kt`、`app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt`
- UI挙動: 下端オーバースクロール時の更新判定と下部インジケーター表示
- 依存: Jetpack Compose の Nested Scroll / HapticFeedback API
