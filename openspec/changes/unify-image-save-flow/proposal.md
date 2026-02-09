## Why

スレッド画面と画像ビューア画面で、画像保存時の権限要求・保存実行・結果トーストの実装が重複し、画面ごとに状態保持先や処理経路が分かれています。保存体験の不一致や改修漏れを防ぐため、保存フローを単一の責務へ集約して両画面で同一挙動に統一する必要があります。

## What Changes

- 画像保存フロー（URL正規化、権限判定、保留URL管理、保存実行、結果メッセージ生成）を共通コーディネータへ集約する。
- スレッド画面と画像ビューア画面の `ViewModel` から、共通の保存イベント契約（単体保存・複数保存・権限結果受理・UIイベント通知）を利用する構成へ変更する。
- UI（Composable）側は権限ランチャーの起動とUIイベント消費に限定し、保存処理ロジックを持たないように整理する。
- 既存の画像アクション（単体保存／全保存）は両画面とも同じ保存フローを通るようにし、結果トースト文言を完全一致させる。

## Capabilities

### New Capabilities
- なし

### Modified Capabilities
- `image-viewer`: 画像保存時の権限要求・保存結果通知の仕様を、スレッド画面と同一の共通フロー前提に変更する。
- `thread-image-menu`: 画像保存アクション実行時の保存フローを、画像ビューアと共通の契約・挙動で実行するよう変更する。

## Impact

- 影響コード:
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/viewer/ImageViewerScreen.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/common/*`（新規共通保存フロー含む）
  - 関連 `ViewModel` / `UiState`
- API/インターフェース:
  - 画面内コールバック中心の保存呼び出しを、`ViewModel` 主導の保存イベント契約へ統一。
- 依存:
  - Android 権限要求 API（`ActivityResultContracts.RequestPermission`）の利用箇所は UI 層に限定。
