## 1. DB スキーマと移行

- [ ] 1.1 共通客観状態を表す `thread_states` Room Entity を追加し、`threadId` 主キー、`threadKey`、板情報、タイトル、最新レス数、更新時刻を保持できるようにする
- [ ] 1.2 `thread_states` 用 DAO を追加し、`threadId` 単位、板単位、開いているタブ単位で監視・取得・更新できるクエリを定義する
- [ ] 1.3 `thread_histories` の `ThreadReadState` を既読状態の正本として扱う方針に合わせ、履歴削除時に既読位置も削除されることを確認・整理する
- [ ] 1.4 `open_thread_tabs` をタブ固有状態中心の構造へ移行するマイグレーションを設計し、レス数・既読状態を正本として扱わない形にする
- [ ] 1.5 既存 `open_thread_tabs`、`thread_histories`、`thread_summaries` から `thread_states` を生成・補完し、`threadId` から取り出した `threadKey` を保存する DB マイグレーションを追加する
- [ ] 1.6 `thread_states` の `threadId`、`boardId`、`boardUrl`、`boardId + threadKey` に必要な index を追加し、一覧監視・タブ一覧監視・板一覧キャッシュ照合の負荷を抑える
- [ ] 1.7 `thread_states.threadKey` が `threadId` に含まれる thread key と一致することを Entity 作成・Repository 更新・マイグレーションで保証する

## 2. スレッド状態 Repository

- [ ] 2.1 `thread_states.latestResCount` と `thread_histories` の既読状態から新着レス数を導出する共通ロジックを追加する
- [ ] 2.2 板更新、タブ一覧更新、スレッド閲覧・更新から最新レス数を保存する `thread_states` Repository API を追加する
- [ ] 2.3 既読位置更新は `thread_histories` の `ThreadReadState` に保存し、履歴削除で既読状態も消えるようにする
- [ ] 2.4 `thread_histories.resCount` を履歴表示用スナップショットとして残し、最新レス数の正本や新着計算には使わないように整理する
- [ ] 2.5 既存の `ThreadReadStateRepository`、`ThreadHistoryRepository`、`TabsRepository` との責務分担を整理し、客観状態は `thread_states`、既読状態は `thread_histories` が正本になるように接続する
- [ ] 2.6 参照なし、30日TTL、削除件数上限を条件にした `thread_states` GC 用 Repository API を追加する

## 3. 板画面との接続

- [ ] 3.1 `BoardRepository.refreshThreadList` が subject.txt のレス数を `thread_states.latestResCount` へ反映するようにする
- [ ] 3.2 `BoardRepository.observeThreads` または `ThreadListCoordinator` が `thread_states` と `thread_histories` を参照して `ThreadInfo.newResCount` と既読状態を生成するようにする
- [ ] 3.3 板画面の検索・NG・ソート処理が、共通状態由来の新着レス数を維持したまま動作することを確認する

## 4. タブ一覧との接続

- [ ] 4.1 `TabsRepository.observeOpenThreadTabs` が `open_thread_tabs`、`thread_states`、`thread_histories` を合成して `ThreadTabInfo` を生成するようにする
- [ ] 4.2 `TabsRepository.saveOpenThreadTabs` がタブの並び順とスクロール位置を保存し、レス数・既読状態の正本を上書きしないようにする
- [ ] 4.3 `ThreadTabsCoordinator.refreshOpenThreads` が取得したレス数を `thread_states` へ保存し、タブ一覧の新着バッジを `thread_states + thread_histories` 由来にする
- [ ] 4.4 タブ削除時にタブ固有状態だけを削除し、`thread_states` と `thread_histories` を保持するようにする
- [ ] 4.5 `TabsRepository.observeOpenThreadTabs` が履歴なしタブの保存済みスクロール位置を無効化し、表示モデル上は先頭位置として返すようにする
- [ ] 4.6 タブ削除後に `thread_states` の遅延 GC を起動するようにする

## 5. スレッド画面との接続

- [ ] 5.1 スレッド閲覧・更新時に判明したタイトルと最新レス数を `thread_states` へ保存する
- [ ] 5.2 スクロールまたは閲覧位置に応じた最終既読レス番号の更新を `thread_histories` の `ThreadReadState` へ保存する
- [ ] 5.3 スレッド画面で新着範囲を表示するための `firstNewResNo` と `prevResCount` を `thread_histories` から供給する
- [ ] 5.4 スレッド閲覧・履歴記録時のみ `thread_histories.resCount` を履歴表示用スナップショットとして更新する

## 6. GC とライフサイクル

- [ ] 6.1 履歴削除後、板キャッシュ整理後、明示的キャッシュ削除後、アプリ起動時に `thread_states` の遅延 GC を起動する
- [ ] 6.2 GC 対象判定で `open_thread_tabs`、`thread_histories`、ブックマーク、保持対象 `thread_summaries` の参照有無を確認する
- [ ] 6.3 起動時 GC は削除件数上限つきで実行し、UI 起動を阻害しないようにする

## 7. テストと検証

- [ ] 7.1 Room マイグレーションテストを追加し、タブのみ、履歴のみ、板キャッシュのみ、複数移行元ありのケースを検証する
- [ ] 7.2 新着レス数導出ロジックの単体テストを追加し、履歴なし、既読済み、未読あり、新着開始番号あり、レス数減少のケースを検証する
- [ ] 7.3 板更新後にタブ一覧の新着レス数が同期されることを Repository または ViewModel レベルで検証する
- [ ] 7.4 タブ一覧更新後に板画面の新着レス数が同期されることを Repository または ViewModel レベルで検証する
- [ ] 7.5 スレッド閲覧後に板画面とタブ一覧の新着レス数が同じ値へ更新されることを検証する
- [ ] 7.6 履歴削除後に開いているタブを表示した場合、保存済みスクロール位置ではなく先頭位置から開くことを検証する
- [ ] 7.7 `thread_states` GC が参照あり行を削除せず、参照なしで30日以上古い行だけを削除対象にすることを検証する
- [ ] 7.8 Android CI のビルドとユニットテストを実行し、失敗があれば修正する
