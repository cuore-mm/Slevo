## Why

画像ビューアでトップバーとサムネイルバーが表示されている状態から戻る際、共有トランジション開始直前に画像本体がバーより前面に出る。常時再現し、視覚的な破綻として UX を損なうため、戻り遷移の表示順契約を明確化して修正する必要がある。

## What Changes

- 画像ビューアから戻るときの shared transition 中に、画像本体がトップバーとサムネイルバーの背面に維持されるよう表示順を統一する。
- 遷移元（ImageViewer）と遷移先（スレッドサムネイル）の shared element 設定差分を解消し、戻り時に一時的な前面化が起きない契約を追加する。
- 共有トランジション有効化フラグの適用漏れを整理し、遷移有効/無効の分岐が意図どおり反映される状態にする。

## Capabilities

### New Capabilities
- なし

### Modified Capabilities
- `image-viewer`: 画像ビューア終了時の shared transition における画像本体とバーUIの重なり順を要件として追加・明確化する。

## Impact

- 対象コード: `app/src/main/java/com/websarva/wings/android/slevo/ui/viewer/ImageViewerPager.kt`
- 対象コード: `app/src/main/java/com/websarva/wings/android/slevo/ui/common/ImageThumbnailGrid.kt`
- 対象コード: `app/src/main/java/com/websarva/wings/android/slevo/ui/viewer/ImageViewerScreen.kt`
- 既存 API や外部依存の追加はなし。UI 表示契約と shared transition 設定の整合が主な影響範囲。
