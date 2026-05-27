## 1. 共通計算ユーティリティ

- [ ] 1.1 `data/util` にスレッドキー由来の派生情報を表すモデルと計算ユーティリティを追加する
- [ ] 1.2 有効な epoch thread key 判定、作成日時計算、勢い計算、まとめて計算する API を実装する
- [ ] 1.3 無効 key、`THREAD_KEY_THRESHOLD` 以上、レス数 0 以下、未来時刻に近い key のデフォルト処理を既存挙動と揃える
- [ ] 1.4 勢い計算で呼び出し側が `nowSeconds` を指定できるようにする

## 2. 既存計算箇所の置き換え

- [ ] 2.1 `ThreadListParser` の作成日時・勢い計算を共通ユーティリティへ置き換える
- [ ] 2.2 `BoardRepository` の板一覧表示モデル生成時の作成日時・勢い計算を共通ユーティリティへ置き換え、既存の `lastFetchedAt` 基準を維持する
- [ ] 2.3 `ThreadViewModel` のスレッド読み込み成功時の作成日時・勢い計算を共通ユーティリティへ置き換える
- [ ] 2.4 旧 `calculateThreadDate` 参照を整理し、残す場合は共通ユーティリティへの委譲にする

## 3. タブ詳細 BottomSheet の補完

- [ ] 3.1 `TabDetailBottomSheets` でスレッドタブ詳細用 `ThreadInfo` を生成する前に、共通ユーティリティで作成日時と勢いを計算する
- [ ] 3.2 `ThreadInfo` に補完した `date` と `momentum` を渡し、既存のタイトル、URL、レス数、板情報の受け渡しは維持する
- [ ] 3.3 無効な thread key でもタブ詳細 BottomSheet が安全に表示されることを確認する

## 4. テストと検証

- [ ] 4.1 共通ユーティリティの単体テストを追加し、有効 key の日付、勢い、無効 key、境界値、レス数 0 のケースを検証する
- [ ] 4.2 `ThreadListParser` の既存テストまたは新規テストで、パース結果の日付・勢いが共通計算規則と一致することを検証する
- [ ] 4.3 タブ詳細用 `ThreadInfo` 補完の単体テストまたは ViewModel/Composable 周辺テストを追加できる範囲で検討し、少なくとも手動確認項目を整理する
- [ ] 4.4 Android CI workflow で build と unit test を確認する
