## 1. DB スキーマと移行

- [ ] 1.1 共通スレッド状態を表す Room Entity を追加し、`threadId` 主キー、板情報、タイトル、最新レス数、既読状態、新着開始レス番号、更新時刻を保持できるようにする
- [ ] 1.2 共通スレッド状態用 DAO を追加し、`threadId` 単位、板単位、開いているタブ単位で監視・取得・更新できるクエリを定義する
- [ ] 1.3 `open_thread_tabs` をタブ固有状態中心の構造へ移行するマイグレーションを設計し、レス数・既読状態を正本として扱わない形にする
- [ ] 1.4 既存 `open_thread_tabs`、`thread_histories`、`thread_summaries` から `thread_states` を生成する DB マイグレーションを追加する
- [ ] 1.5 `thread_states` の `threadId`、`boardId`、`boardUrl` に必要な index を追加し、一覧監視とタブ一覧監視の負荷を抑える

## 2. 共通スレッド状態 Repository

- [ ] 2.1 最新レス数、既読レス番号、最初の新着レス番号から新着レス数を導出する共通ロジックを追加する
- [ ] 2.2 板更新、タブ一覧更新、スレッド閲覧・更新、既読位置更新から利用する共通 Repository API を追加する
- [ ] 2.3 レス数更新と既読状態更新をトランザクション内で整合させ、`firstNewResNo` と `lastReadResNo` の不整合を防ぐ
- [ ] 2.4 既存の `ThreadReadStateRepository`、`ThreadHistoryRepository`、`TabsRepository` との責務分担を整理し、共通状態が正本になるように接続する

## 3. 板画面との接続

- [ ] 3.1 `BoardRepository.refreshThreadList` が subject.txt のレス数を共通スレッド状態へ反映するようにする
- [ ] 3.2 `BoardRepository.observeThreads` または `ThreadListCoordinator` が共通スレッド状態を参照して `ThreadInfo.newResCount` と既読状態を生成するようにする
- [ ] 3.3 板画面の検索・NG・ソート処理が、共通状態由来の新着レス数を維持したまま動作することを確認する

## 4. タブ一覧との接続

- [ ] 4.1 `TabsRepository.observeOpenThreadTabs` が `open_thread_tabs` と共通スレッド状態を合成して `ThreadTabInfo` を生成するようにする
- [ ] 4.2 `TabsRepository.saveOpenThreadTabs` がタブの並び順とスクロール位置を保存し、レス数・既読状態の正本を上書きしないようにする
- [ ] 4.3 `ThreadTabsCoordinator.refreshOpenThreads` が取得したレス数を共通スレッド状態へ保存し、タブ一覧の新着バッジを共通状態由来にする
- [ ] 4.4 タブ削除時にタブ固有状態だけを削除し、共通スレッド状態を保持するようにする

## 5. スレッド画面との接続

- [ ] 5.1 スレッド閲覧・更新時に判明したタイトルと最新レス数を共通スレッド状態へ保存する
- [ ] 5.2 スクロールまたは閲覧位置に応じた最終既読レス番号の更新を共通スレッド状態へ保存する
- [ ] 5.3 スレッド画面で新着範囲を表示するための `firstNewResNo` と `prevResCount` を共通状態から供給する

## 6. テストと検証

- [ ] 6.1 Room マイグレーションテストを追加し、タブのみ、履歴のみ、板キャッシュのみ、複数移行元ありのケースを検証する
- [ ] 6.2 新着レス数導出ロジックの単体テストを追加し、既読済み、未読あり、新着開始番号あり、レス数減少のケースを検証する
- [ ] 6.3 板更新後にタブ一覧の新着レス数が同期されることを Repository または ViewModel レベルで検証する
- [ ] 6.4 タブ一覧更新後に板画面の新着レス数が同期されることを Repository または ViewModel レベルで検証する
- [ ] 6.5 スレッド閲覧後に板画面とタブ一覧の新着レス数が同じ値へ更新されることを検証する
- [ ] 6.6 Android CI のビルドとユニットテストを実行し、失敗があれば修正する
