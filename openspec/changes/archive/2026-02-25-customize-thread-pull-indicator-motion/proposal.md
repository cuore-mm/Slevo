## Why

Thread 画面の下端プル更新インジケーターは現在 `ContainedLoadingIndicator` の固定表示のみで、
「引っ張り量」と「更新中状態」の視覚的な違いが弱い。
更新トリガーの手応えを高めるため、プル中アニメーションを段階的に表現しつつ既存の更新中回転は維持する。

## What Changes

- Thread 画面の下端プル更新インジケーターをカスタマイズし、引っ張り中は左回転と拡大を適用する。
- プル中サイズは閾値まで比例して拡大し、閾値超過後に戻した場合は現在プル量に応じて再度縮小する。
- 回転は `overscroll` の現在量から算出し、引っ張り方向では左回転、戻し方向では右回転に見える連続モーションを適用する。
- 更新中 (`isLoading`) は既存どおり右回転の `ContainedLoadingIndicator` 表示を維持する。
- 既存の下端更新判定（閾値・触覚・発火条件）は変更せず、表示ロジックのみを拡張する。

## Capabilities

### New Capabilities
- `thread-pull-indicator-motion`: Thread 画面の下端プル更新インジケーターにおけるプル量連動モーションと更新中モードの表示契約を定義する

### Modified Capabilities
- なし

## Impact

- Affected specs:
  - `openspec/changes/customize-thread-pull-indicator-motion/specs/thread-pull-indicator-motion/spec.md`
- Affected code:
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScreen.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/effects/ThreadScreenEffects.kt`
  - 必要に応じて `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/` 配下のインジケーター専用 Composable
- User impact:
  - 更新操作の視認性向上（機能追加・仕様変更なし）
