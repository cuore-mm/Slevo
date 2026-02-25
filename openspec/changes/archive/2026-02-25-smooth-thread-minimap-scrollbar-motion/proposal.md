## Why

スレッド画面のミニマップスクロールバー（MomentumBar）は、レス番号ベースの離散的な位置対応でスクロールを行うため、ドラッグ時の追従が段階的に見えやすい。投稿高さの差分があるケースでは体感上の移動量とのズレも生じ、スクロール体験の一貫性を損なっているため、自然な連続追従へ改善する必要がある。

## What Changes

- ミニマップドラッグ時のスクロール制御を、インデックスジャンプ中心からピクセル連続追従中心へ変更する。
- タップ移動とドラッグ移動を分離し、タップは目標位置移動、ドラッグは連続移動として扱う。
- ビューポートインジケーターの位置算出に `firstVisibleItemScrollOffset` を加味し、段階的な表示更新を抑える。
- 投稿数・投稿高さ分布・下端/上端近傍での挙動が破綻しないよう、境界条件とクランプ規則を明確化する。

## Capabilities

### New Capabilities
- `thread-minimap-scrollbar`: スレッド画面ミニマップの操作（タップ/ドラッグ）と表示同期を、連続的で自然なスクロール体験として定義する。

### Modified Capabilities
- なし

## Impact

- 影響コード: `ui/thread/components/MomentumBar.kt`、必要に応じて `ui/thread/screen/ThreadScreen.kt`。
- 影響UI: スレッド画面のミニマップ有効時スクロール操作とインジケーター表示。
- 外部API/依存関係の追加なし（既存 Compose / LazyListState の範囲で実現）。
