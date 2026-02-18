## Why

現在のサムネイルグリッドは、画像表示が失敗していてもタップで画像ビューアへ遷移できるため、ユーザー体験として「表示できない画像へ遷移する」不整合が発生する。
サムネイル表示成功と遷移可否を一致させ、誤タップ時の無効遷移を防ぐ必要がある。

## What Changes

- サムネイル単位で表示成功状態を管理し、成功したサムネイルのみ画像ビューア遷移を許可する。
- 読み込み中またはエラーのサムネイルはタップしても画像ビューア遷移しないようにする。
- 画像ビューアへ渡す画像URL一覧は従来仕様を維持し、表示成功画像への絞り込みは行わない。

## Capabilities

### New Capabilities

- なし

### Modified Capabilities

- `image-viewer`: サムネイル起点の遷移前提を「表示成功済みサムネイルのみ遷移可能」に変更する。

## Impact

- Affected specs:
  - `openspec/specs/image-viewer/spec.md`
- Affected code:
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/common/ImageThumbnailGrid.kt`
  - サムネイルタップ遷移を呼び出すスレッド画面/ポップアップ/投稿ダイアログ経路
- User impact:
  - 表示失敗サムネイルの無効遷移を抑止し、遷移挙動の一貫性を向上
