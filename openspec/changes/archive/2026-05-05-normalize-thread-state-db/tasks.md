## 1. Phase 1: `thread_states` 導入・移行・並行更新

- [x] 1.1 共通客観状態を表す `thread_states` Room Entity を追加し、`threadId` 主キー、`threadKey`、板情報、タイトル、最新レス数、更新時刻を保持できるようにする
- [x] 1.2 `thread_states` 用 DAO を追加し、`threadId` 単位、板単位、開いているタブ単位で監視・取得・更新できるクエリを定義する
- [x] 1.3 DB version を上げ、既存 `open_thread_tabs`、`thread_histories`、`thread_summaries` から `thread_states` を生成・補完するマイグレーションを追加する
- [x] 1.4 `threadId` から取り出した `threadKey` を `thread_states.threadKey` として保存し、`boardId + threadKey` index を追加する
- [x] 1.5 `thread_states.threadKey` が `threadId` に含まれる thread key と一致することを Entity 作成・Repository 更新・マイグレーションで保証する
- [x] 1.6 `ThreadStateRepository` を追加し、板更新、タブ一覧更新、スレッド閲覧・更新から最新レス数とタイトルを保存できる API を用意する
- [x] 1.7 `BoardRepository.refreshThreadList` が subject.txt のレス数とタイトルを既存 `thread_summaries` に加えて `thread_states` にも並行更新するようにする
- [x] 1.8 `ThreadTabsCoordinator.refreshOpenThreads` が取得したレス数を既存タブ状態に加えて `thread_states` にも並行更新するようにする
- [x] 1.9 スレッド閲覧・更新時に判明したタイトルと最新レス数を `thread_states` にも並行更新するようにする
- [x] 1.10 Phase 1 では UI 表示の正本を既存 `open_thread_tabs` / `thread_histories` / `thread_summaries` のまま維持し、挙動変化を最小化する
- [x] 1.11 Phase 1 の検証として、Room マイグレーションテストと `thread_states` 並行更新の Repository テストを追加する

## 2. Phase 2: 板画面・タブ一覧・スレッド画面の参照切替

- [x] 2.1 `thread_states.latestResCount` と `thread_histories` の既読状態から新着レス数を導出する共通ロジックを追加する
- [x] 2.2 `thread_histories.resCount` を履歴表示用スナップショットとして残し、最新レス数の正本や新着計算には使わないように整理する
- [x] 2.3 `ThreadReadStateRepository` を整理し、既読位置更新は `thread_histories` の `ThreadReadState` のみに保存するようにする
- [x] 2.4 `BoardRepository.observeThreads` または `ThreadListCoordinator` が `thread_states` と `thread_histories` を参照して `ThreadInfo.newResCount` と既読状態を生成するようにする
- [x] 2.5 `TabsRepository.observeOpenThreadTabs` が `open_thread_tabs`、`thread_states`、`thread_histories` を合成して `ThreadTabInfo` を生成するようにする
- [x] 2.6 `TabsRepository.saveOpenThreadTabs` がタブの並び順とスクロール位置を保存し、レス数・既読状態の正本を上書きしないようにする
- [x] 2.7 `ThreadTabsCoordinator.refreshOpenThreads` が `thread_states` を最新レス数の正本として更新し、タブ一覧の新着バッジを `thread_states + thread_histories` 由来にする
- [x] 2.8 `TabsRepository.observeOpenThreadTabs` が履歴なしタブの保存済みスクロール位置を無効化し、表示モデル上は先頭位置として返すようにする
- [x] 2.9 スレッド画面で新着範囲を表示するための `firstNewResNo` と `prevResCount` を `thread_histories` から供給する
- [x] 2.10 スレッド閲覧・履歴記録時のみ `thread_histories.resCount` を履歴表示用スナップショットとして更新する
- [x] 2.11 Phase 2 の検証として、新着レス数導出、板更新→タブ同期、タブ更新→板同期、スレッド閲覧→新着反映、履歴なしスクロール位置のテストを追加する

## 3. Phase 3: `open_thread_tabs` 整理・GC・仕上げ

- [x] 3.1 `open_thread_tabs` をタブ固有状態中心の構造へ移行するマイグレーションを追加し、`threadId`、`sortOrder`、スクロール位置のみを保持する形にする
- [x] 3.2 `OpenThreadTabDao.updateReadState()` など、タブテーブルへレス数・既読状態を書き込む不要 DAO / Repository 処理を削除する
- [x] 3.3 タブ削除時にタブ固有状態だけを削除し、`thread_states` と `thread_histories` を保持するようにする
- [x] 3.4 参照なし、30日TTL、削除件数上限を条件にした `thread_states` GC 用 Repository API を追加する
- [x] 3.5 GC 対象判定で `open_thread_tabs`、`thread_histories`、ブックマーク、保持対象 `thread_summaries` の参照有無を確認する
- [x] 3.6 履歴削除後、タブ削除後、板キャッシュ整理後、明示的キャッシュ削除後、アプリ起動時に `thread_states` の遅延 GC を起動する
- [x] 3.7 起動時 GC は削除件数上限つきで実行し、UI 起動を阻害しないようにする
- [x] 3.8 `thread_states` GC が参照あり行を削除せず、参照なしで30日以上古い行だけを削除対象にすることを検証する
- [x] 3.9 Phase 1〜3 全体のマイグレーション、Repository、ViewModel レベルの回帰テストを整理する
- [x] 3.10 Android CI のビルドとユニットテストを実行し、失敗があれば修正する
