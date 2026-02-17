## Why

`ImageViewerScreen` が単一関数に状態管理・副作用・UI構築を集約しており、可読性と変更容易性が低下している。
Issue #441 の目的に沿って責務を分割し、将来の機能追加や不具合修正時の影響範囲を限定できる構造へ整理する必要がある。

## What Changes

- `ImageViewerScreen` を画面オーケストレーション中心へ縮小し、副作用処理と表示構築を専用Composable/関数へ分離する。
- 画面の表示状態は `UiState` として `ViewModel` 管理へ寄せ、Compose 実装都合の状態のみ UI ローカルに限定する。
- サムネイル同期、システムバー制御、画像保存イベント処理などのロジック境界を明確化し、ファイル単位で責務を分離する。
- 既存のUI挙動・操作フロー・遷移条件を維持し、機能変更を行わない。

## Capabilities

### New Capabilities

- なし

### Modified Capabilities

- `image-viewer`: 画像ビューア画面の内部実装要件として、責務分離された構成を満たしつつ既存挙動を維持する要件を追加する。

## Impact

- Affected specs:
  - `openspec/specs/image-viewer/spec.md`
- Affected code:
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/viewer/ImageViewerScreen.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/viewer/ImageViewerViewModel.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/viewer/` 配下の関連Composable/補助関数
- User impact:
  - 画面の機能・見た目・操作に変更はなく、保守性改善のみを提供
