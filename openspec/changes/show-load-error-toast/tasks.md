## 1. UI event 設計

- [ ] 1.1 板画面の読み込み失敗 Toast 用 one-shot UI event 型と `SharedFlow` を `BoardViewModel` に追加する
- [ ] 1.2 スレ画面の読み込み失敗 Toast 用 one-shot UI event 型と `SharedFlow` を `ThreadViewModel` に追加する
- [ ] 1.3 既存の画像保存 Toast event と用途が混ざらない命名にする

## 2. 板画面の失敗通知

- [ ] 2.1 `BoardViewModel.loadData` の例外捕捉を `catch (e: Exception)` に変更し、`Timber` に詳細ログを出力する
- [ ] 2.2 `repository.refreshThreadList` が `false` を返した場合に板読み込み失敗 Toast event を発行する
- [ ] 2.3 例外発生時に板読み込み失敗 Toast event を発行し、1 回の読み込み試行で重複発行しないようにする
- [ ] 2.4 `BoardScaffold` で板読み込み失敗 event を収集し、Toast を表示する

## 3. スレ画面の失敗通知

- [ ] 3.1 `ThreadViewModel.loadData` の失敗分岐で `Timber` に詳細ログを出力する
- [ ] 3.2 dat 取得や変換で例外が発生した場合にスレッド読み込み失敗 Toast event を発行する
- [ ] 3.3 dat 取得が失敗結果として終了した場合にスレッド読み込み失敗 Toast event を発行する
- [ ] 3.4 `ThreadScaffold` でスレッド読み込み失敗 event を収集し、Toast を表示する

## 4. 文言と副作用整理

- [ ] 4.1 Toast 文言を string resource 化するか既存方針に合わせ、板用・スレ用の短い文言を定義する
- [ ] 4.2 Toast には例外詳細を直接表示せず、詳細はログへ出すことを確認する
- [ ] 4.3 画面再コンポーズだけで同じ読み込み失敗 Toast が再表示されないことを確認する

## 5. テストと検証

- [ ] 5.1 板読み込みで例外が発生した場合に Toast event が発行される ViewModel テストを追加する
- [ ] 5.2 板読み込みが `false` を返した場合に Toast event が発行される ViewModel テストを追加する
- [ ] 5.3 スレ読み込みで例外または失敗結果が発生した場合に Toast event が発行される ViewModel テストを追加する
- [ ] 5.4 既存の画像保存 Toast と URL 不正 Toast の挙動を壊していないことを確認する
- [ ] 5.5 CI のユニットテストとビルドを実行し、追加・既存テストが成功することを確認する
- [ ] 5.6 変更範囲のコメントがリポジトリのコメント規約を満たしていることを確認する
