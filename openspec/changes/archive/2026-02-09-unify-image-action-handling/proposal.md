## Why

スレッド画面と画像ビューア画面で画像アクションの実行処理が重複しており、同一アクションの挙動差分や修正漏れが発生しやすい状態です。機能追加と不具合修正のコストを下げるため、実行処理を共通化して保守性を改善します。

## What Changes

- `ImageMenuAction` の実行処理（コピー、共有、他アプリ起動、Web検索、保存トリガー、NG登録トリガー）を `ui/common` の共通ランナーへ集約する。
- スレッド画面の画像メニュー実行を共通ランナー経由に置き換える。
- 画像ビューア画面のトップバー/ドロップダウン実行を共通ランナー経由に置き換える。
- 画面固有責務（メニュー表示方式、保存権限要求フロー、保存結果トースト、UiState更新）は各画面側に維持する。

## Capabilities

### New Capabilities
- なし

### Modified Capabilities
- `thread-image-menu`: スレッド画面の画像アクション実行を共通ランナー経由に統一し、既存アクション挙動を維持する。
- `image-viewer`: 画像ビューアの画像アクション実行を共通ランナー経由に統一し、既存アクション挙動を維持する。

## Impact

- 影響コード:
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/viewer/ImageViewerScreen.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/common/...`（新規共通ランナー）
- API/外部依存の追加はなし。
- 既存UI（BottomSheet/DropdownMenu）およびユーザー向け文言は変更しない。
