## 1. 表示行モデルの追加

- [x] 1.1 `ThreadListItem.PostRow` を追加し、投稿行の最終表示行モデルを定義する
- [x] 1.2 投稿行の表示文脈を表す `PostDisplayRole` または同等の型を追加する
- [x] 1.3 投稿行がレス番号、表示ロール、更新グループ index、出現 index から `stableKey` を生成するようにする

## 2. 表示リスト生成の分離

- [x] 2.1 既存の `DisplayPost` 生成処理を維持しつつ、`DisplayPost` から `ThreadListItem.PostRow` へ変換する処理を追加する
- [x] 2.2 複数 `ThreadPostGroup` を処理する際に、各投稿行へ正しい groupIndex を付与する
- [x] 2.3 同一グループ・同一ロール・同一レス番号内で重複した表示行が出る場合に occurrenceIndex を増分する
- [x] 2.4 `ThreadUiState` または描画入力を、最終表示行リストを参照できる形へ更新する

## 3. LazyColumn 描画の移行

- [x] 3.1 THREAD画面の `LazyColumn` が投稿行の `ThreadListItem.PostRow.stableKey` を item key として使用するように変更する
- [x] 3.2 投稿行描画では `ThreadListItem.PostRow.displayPost` から既存のレス表示情報を取得する
- [x] 3.3 header divider は既存の `thread_header_divider` key のまま維持し、投稿行 key と衝突しないことを確認する
- [x] 3.4 投稿下 divider と新着バーは投稿行内の既存配置を維持し、独立した LazyColumn item にしない
- [x] 3.5 既存のツリーインデント、返信ポップアップ、画像共有遷移、スクロール保存の参照 index が破綻しないことを確認する

## 4. テスト追加・回帰確認

- [x] 4.1 TREE表示かつ複数新着グループで、同じレス番号の dimmed 親行が複数回出ても key が重複しないユニットテストを追加する
- [x] 4.2 NUMBER表示で通常投稿行の key が重複しないユニットテストを追加する
- [x] 4.3 `visibleItems.map { stableKey }` の一意性を検証するテストヘルパーを追加する
- [x] 4.4 既存のスレッド表示順、新着バー位置、ツリー表示のテストが引き続き通ることを確認する

## 5. 検証

- [ ] 5.1 CI でビルドとユニットテストが通ることを確認する
- [ ] 5.2 実機またはエミュレータで TREE表示のスレッドを複数回更新し、スクロール中に `Key "..." was already used` クラッシュが再発しないことを確認する
- [ ] 5.3 NUMBER表示、TREE表示、新着あり、検索あり、NGフィルタありの主要表示パターンでスクロールと表示崩れがないことを確認する
