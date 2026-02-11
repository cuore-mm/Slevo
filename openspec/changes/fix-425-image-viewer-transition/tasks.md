## 1. Shared Transition描画契約の統一

- [ ] 1.1 `ImageViewerPager` 側の shared element 設定を確認し、戻り遷移で overlay 前面化を起こさない構成に固定する
- [ ] 1.2 `ImageThumbnailGrid` 側にも同一の描画ポリシーを適用し、遷移元/遷移先の shared element 設定差分を解消する

## 2. 有効化フラグと実装の整合

- [ ] 2.1 サムネイル描画で `enableSharedElement` が実際に適用されるよう Modifier 構成を整理する
- [ ] 2.2 shared transition 無効時に shared element が付与されないことを UI 挙動で確認する

## 3. 回帰確認

- [ ] 3.1 issue #425 の再現手順（複数画像レス → ビューア表示 → 戻る）で、バー表示中に画像本体が前面化しないことを確認する
- [ ] 3.2 画像ビューアの通常遷移（進入/退出）、サムネイルタップ遷移、バー表示切替に回帰がないことを確認する
