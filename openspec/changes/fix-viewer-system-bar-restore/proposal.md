## Why

画像ビューアでシステムバーを非表示にしたまま戻ると、遷移先画面でも immersive 状態が残る回帰が発生している。通常の戻る遷移で UI 状態がリークするため、画面ライフサイクルに合わせた復元要件を明確化し、再発を防ぐ必要がある。

## What Changes

- 画像ビューアの終了時に、表示開始前のシステムバー可視状態を復元する要件を追加する。
- システムバーの見た目（ライト/ダークアイコン・ナビゲーションバーコントラスト）復元要件と、可視状態復元要件を分離して定義する。
- ビューア内でのバー表示切替挙動は維持しつつ、遷移先画面へ状態が漏れないことを受け入れ条件に加える。

## Capabilities

### New Capabilities
- なし

### Modified Capabilities
- `image-viewer`: 画面破棄時のシステムバー可視状態復元要件を追加する

## Impact

- 影響コード: `app/src/main/java/com/websarva/wings/android/slevo/ui/viewer/ImageViewerScreen.kt`
- 影響範囲: 画像ビューアから他画面への戻る遷移時のシステムバー表示状態
- API/依存関係: 公開 API 変更なし、既存 `WindowInsetsControllerCompat` 利用範囲の見直しのみ
