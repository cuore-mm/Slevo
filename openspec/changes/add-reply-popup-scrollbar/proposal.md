## Why

レスポップアップ内の投稿一覧は縦スクロール可能だが、現在位置が視覚的に把握しづらく、長いツリーや複数レスを閲覧する際に読み進め状況を見失いやすい。
Issue #442 の要望に合わせて、ポップアップ内にスクロールバーを表示し、閲覧位置の認知負荷を下げる。

## What Changes

- レスポップアップの投稿一覧にスクロールバー表示を追加する。
- スクロールバーのつまみはドラッグ不可（位置表示専用）にする。
- ポップアップ内容が表示領域内に収まる場合はスクロールバーを表示しない。
- ポップアップ内の既存操作（返信番号/返信元/ID/URL/画像タップ）や多段ポップアップ挙動は維持する。

## Capabilities

### New Capabilities

- なし

### Modified Capabilities

- `thread-tree-popup`: レスポップアップ内一覧のスクロール位置可視化要件として、非ドラッグ型スクロールバー表示を追加する。

## Impact

- Affected specs:
  - `openspec/specs/thread-tree-popup/spec.md`
- Affected code:
  - `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/res/ReplyPopup.kt`
- Non-functional impact:
  - 長文ポップアップ閲覧時の位置把握性向上
  - 操作ミス低減（つまみ誤ドラッグ抑止）
