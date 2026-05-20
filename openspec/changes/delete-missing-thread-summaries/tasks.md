## 1. DAO と差分処理の切り替え

- [ ] 1.1 `ThreadSummaryDao` に、板内 thread ID のリストを安全に削除する DAO メソッドを追加する
- [ ] 1.2 削除対象 ID を SQLite 変数上限より十分小さいチャンクへ分割する処理を追加または既存チャンク処理を削除用に再利用する
- [ ] 1.3 `BoardRepository.refreshThreadList` で、subject.txt から消えた summary を `markArchived` ではなく削除処理へ渡す
- [ ] 1.4 subject.txt に残る既存 summary の更新と、新規 summary の挿入が従来通り行われることを確認する

## 2. 既存アーカイブ行の整理

- [ ] 2.1 既存 DB の `isArchived = 1` 行を削除する migration または cleanup 方針を実装前に選定する
- [ ] 2.2 選定した方針に従い、`isArchived = 1` の `thread_summaries` 行だけを削除対象にする処理を追加する
- [ ] 2.3 `isArchived = 0` の現役 summary が既存アーカイブ行整理で削除されないことを確認する

## 3. 独立データ保持の確認

- [ ] 3.1 summary 削除時に `thread_histories` が削除されないことを確認するテストを追加する
- [ ] 3.2 summary 削除時に `bookmark_threads` が削除されないことを確認するテストを追加する
- [ ] 3.3 summary 削除時に `open_thread_tabs` が削除されないことを確認するテストを追加する
- [ ] 3.4 summary 削除後も `thread_states` は既存の遅延 GC 規則に従うことを確認する

## 4. 板更新挙動のテスト

- [ ] 4.1 subject.txt から消えた summary が削除される単体テストを追加する
- [ ] 4.2 大量の削除対象 summary がチャンク分割で全件削除される単体テストを追加する
- [ ] 4.3 削除済みスレッドが subject.txt に再出現した場合、新規 summary として `firstSeenAt` が再挿入時点になることを確認する
- [ ] 4.4 通常の subject.txt 更新、新規追加、レス数更新、表示順位更新の既存挙動が維持されることを確認する

## 5. 検証と後片付け

- [ ] 5.1 `markArchived` と `isArchived` に依存する不要な呼び出しが残っていないか確認する
- [ ] 5.2 `isArchived` カラムを残す場合、当面の互換用途と将来削除候補であることをコメントまたは設計上明確にする
- [ ] 5.3 CI のユニットテストとビルドを実行し、追加・既存テストが成功することを確認する
- [ ] 5.4 変更範囲のコメントがリポジトリのコメント規約を満たしていることを確認する
