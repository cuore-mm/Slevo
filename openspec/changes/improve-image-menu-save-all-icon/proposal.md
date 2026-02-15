## Why

`ImageMenu` の BottomSheet で「単体保存」と「すべて保存（件数付き）」が同じテキスト中心の見た目になっており、操作意図の判別に時間がかかる。
Issue #437 の要望どおり「すべて保存」に複数対象であることを示す合成アイコンを導入し、視認性と誤操作防止を改善する。

## What Changes

- `ImageMenu` の「すべて保存」アクションに `download` をベースにしたアイコンを表示する。
- アイコン右上に `filter_none` を重ねた小バッジを追加し、複数保存アクションであることを明示する。
- 単体保存を含む既存メニュー項目の動作・表示条件は維持し、今回変更は「すべて保存」行の視覚表現に限定する。

## Capabilities

### New Capabilities

- なし

### Modified Capabilities

- `thread-image-menu`: 「レス内の画像をすべて保存」メニューのアイコン表現に、download + 重なりバッジの視認性要件を追加する。

## Impact

- Affected specs:
  - `openspec/specs/thread-image-menu/spec.md`
- Affected code:
  - スレッド画面の画像メニュー UI（BottomSheet のメニュー項目レンダリング）
  - 合成アイコン表示に関わる Compose UI 定義
- Non-functional impact:
  - 画像保存アクションの判別速度向上
  - メニュー操作時の視覚的な一貫性向上
