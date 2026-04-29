## Context

現在のスレッド関連 DB は、板一覧キャッシュ、閲覧履歴、開いているスレッドタブの各テーブルにレス数・既読状態が分散している。板画面は `thread_summaries.resCount` と履歴の `resCount` から新着レス数を計算し、タブ一覧は `open_thread_tabs.resCount` と一時的な `_newResCounts` から新着レス数を表示している。

ユーザー視点では、既読位置や新着開始位置は「閲覧履歴」の一部である。履歴を削除したのに既読位置だけが残ると不自然なため、今回の設計では「スレッドの客観状態」と「ユーザーの閲覧状態」を分離し、既読位置は履歴に紐づける。

## Current DB Design

### `thread_summaries`

板の subject.txt 由来キャッシュを保持する。

```text
thread_summaries
  boardId             Long     PK part
  threadId            String   PK part / 板内 thread key
  title               String
  resCount            Int      subject.txt 上のレス数
  firstSeenAt         Long
  isArchived          Boolean
  subjectRank         Int
```

課題:
- `resCount` は板一覧キャッシュとしては必要だが、タブ一覧やスレッド画面が見る最新レス数の正本にはなっていない。
- `threadId` は板内 key であり、グローバルな `ThreadId` としては不足する場合がある。

### `thread_histories`

閲覧したスレッドの履歴と既読状態を保持する。

```text
thread_histories
  id                  Long     PK
  threadId            ThreadId unique
  boardUrl            String
  boardId             Long
  boardName           String
  title               String
  resCount            Int      履歴記録時/閲覧時のレス数
  prevResCount        Int      ThreadReadState
  lastReadResNo       Int      ThreadReadState
  firstNewResNo       Int?     ThreadReadState
```

評価:
- 既読位置・新着開始位置が履歴にあること自体は UX 的に自然。
- ただし `resCount` が最新レス数の正本としても扱われると、板・タブ側の値とズレる。

### `open_thread_tabs`

開いているスレッドタブの表示情報、レス数、既読状態、スクロール位置を保持する。

```text
open_thread_tabs
  threadId                        ThreadId PK
  boardUrl                        String
  boardId                         Long
  boardName                       String
  title                           String
  resCount                        Int
  prevResCount                    Int      ThreadReadState
  lastReadResNo                   Int      ThreadReadState
  firstNewResNo                   Int?     ThreadReadState
  sortOrder                       Int
  firstVisibleItemIndex           Int
  firstVisibleItemScrollOffset    Int
```

課題:
- タブを閉じると失われるべきタブ固有状態と、残したいスレッド/既読状態が混在している。
- 履歴にも同じ既読状態があり、`ThreadReadStateRepository` が二重更新している。
- タブ一覧更新で `open_thread_tabs.resCount` だけが進むと、板画面の新着レス数と同期しない。

## Target DB Design

### `thread_states` を追加する

`thread_states` はスレッドの客観状態の正本として追加する。ここには「このスレッドが現在何レスまで確認できているか」「最新タイトルは何か」といった、履歴を消しても板キャッシュ・タブ・お気に入りから参照されうる状態だけを持たせる。

```text
thread_states
  threadId            ThreadId PK
  boardId             Long
  boardUrl            String
  boardName           String
  threadKey           String   板内 thread key / thread_summaries 連携用
  title               String
  latestResCount      Int      板更新/タブ更新/スレ閲覧で確認した最大レス数
  updatedAt           Long
```

Index:
```text
PRIMARY KEY(threadId)
INDEX(boardId, threadKey)
INDEX(boardId)
INDEX(boardUrl)
INDEX(updatedAt)
```

`threadId` はタブ・履歴と JOIN するためのグローバル一意キーとして使う。`threadKey` は `thread_summaries` と照合するための板内キーとして保持する。`threadKey` は `threadId` から Kotlin 側で容易に取り出せるが、SQLite の JOIN・GC・移行処理で文字列分解に依存しないよう、検索用の冗長カラムとして保持する。

保持しないもの:
- `lastReadResNo`
- `firstNewResNo`
- `prevResCount`

これらはユーザーの閲覧事実なので、`thread_histories` 側に保持する。

### `thread_histories` は閲覧履歴と既読状態の正本にする

履歴は「ユーザーが読んだ事実」を表す。既読位置や新着開始位置はこの意味に属するため、`thread_histories` に残す。

```text
thread_histories
  id                  Long     PK
  threadId            ThreadId unique
  boardUrl            String
  boardId             Long
  boardName           String
  title               String   履歴表示用タイトル
  resCount            Int      履歴表示用スナップショット、正本ではない
  prevResCount        Int
  lastReadResNo       Int
  firstNewResNo       Int?
```

削除方針:
- ユーザーが履歴を削除した場合、当該 `thread_histories` 行を削除する。
- それにより既読位置・新着開始位置も削除される。
- `thread_states` は履歴削除だけでは削除しない。板キャッシュ、開いているタブ、お気に入り等から参照される客観状態として残りうる。

履歴がないスレッドの表示:
- `thread_states.latestResCount` は表示できる。
- 既読済みとは扱わない。
- 新着レス数バッジは表示しない。
- 開いているタブにスクロール位置が残っていても、表示モデルでは先頭位置として扱う。

### `open_thread_tabs` はタブ固有状態に限定する

タブテーブルは「開いているか」「順番」「スクロール位置」を保持する。タイトル・レス数・既読状態は正本として持たない。

```text
open_thread_tabs
  threadId                        ThreadId PK
  sortOrder                       Int
  firstVisibleItemIndex           Int
  firstVisibleItemScrollOffset    Int
```

表示モデル生成時は以下を合成する。

```text
open_thread_tabs
  JOIN thread_states      ON threadId
  LEFT JOIN thread_histories ON threadId
```

スクロール位置は DB 上では `open_thread_tabs` に保存するが、有効なのは `thread_histories` が存在する場合だけとする。履歴がないスレッドは未訪問扱いなので、保存済みスクロール位置をそのまま使わず、表示モデル上は先頭位置へ丸める。

```text
ThreadTabInfo.firstVisibleItemIndex =
  if thread_histories exists then open_thread_tabs.firstVisibleItemIndex else 0

ThreadTabInfo.firstVisibleItemScrollOffset =
  if thread_histories exists then open_thread_tabs.firstVisibleItemScrollOffset else 0
```

履歴削除時に `open_thread_tabs` のスクロール位置を即時更新する必要はない。`thread_histories` の削除が Flow に反映されれば、表示モデル生成時にスクロール位置が無効化される。

タブを閉じた場合:
- `open_thread_tabs` の行だけ削除する。
- `thread_histories` は削除しない。
- `thread_states` は削除しない。

### `thread_summaries` は板一覧キャッシュとして維持する

`thread_summaries` は subject.txt の順序、dat 落ち状態、板一覧キャッシュとして残す。

```text
thread_summaries
  boardId
  threadId
  title
  resCount
  firstSeenAt
  isArchived
  subjectRank
```

板更新時は `thread_summaries` を更新しつつ、グローバル `ThreadId` を解決できる場合は `thread_states.latestResCount` と `title` も更新する。

`thread_summaries` との照合は `thread_states.boardId + thread_states.threadKey` を使う。

```text
thread_summaries.boardId = thread_states.boardId
thread_summaries.threadId = thread_states.threadKey
```

## Goals / Non-Goals

**Goals:**
- 最新レス数・タイトルなどの客観状態を `thread_states` に集約する。
- 既読位置・新着開始位置などの閲覧状態を `thread_histories` に紐づけ、履歴削除時に消えるようにする。
- 板画面とタブ一覧の新着レス数を、`thread_states` と `thread_histories` の同じ組み合わせから導出する。
- `open_thread_tabs` をタブ固有状態だけへ寄せ、タブ閉鎖と履歴/既読状態のライフサイクルを分離する。
- 既存 DB から安全に移行し、既存のタブ順・スクロール位置・履歴情報を維持する。

**Non-Goals:**
- スレッド本文・レス本文のキャッシュ方式は変更しない。
- subject.txt や dat の通信方式、差分取得方式は変更しない。
- タブ一覧 UI の見た目そのものは、共通状態を参照するために必要な表示更新以外は変更しない。
- お気に入り、投稿履歴、NG 設定の DB 設計は対象外とする。

## Decisions

### Decision 1: `thread_states` は客観状態だけを持つ

`thread_states` は `latestResCount`、`title`、板情報、`threadKey`、更新時刻を保持する。`lastReadResNo`、`firstNewResNo`、`prevResCount` は保持しない。

理由:
- 履歴を削除しても最新レス数やタイトルが残ることは自然だが、既読位置が残ることは不自然。
- 板更新・タブ更新・スレッド閲覧で確認した最新レス数は、画面や履歴の有無に依存しない客観状態として扱える。
- `threadKey` を持つことで、`thread_summaries` との JOIN や孤立状態の GC を `boardId + threadKey` で単純に実行できる。

代替案として `thread_states` に既読状態も持たせる方法があるが、履歴削除時の UX と矛盾するため採用しない。

代替案として `threadId` 文字列から SQL 側で `threadKey` を取り出す方法もあるが、文字列表現に依存し index も効きにくいため採用しない。`thread_states.threadKey` は `threadId` に含まれる thread key と一致しなければならない。

### Decision 2: 既読状態は `thread_histories` に紐づける

`thread_histories` の `ThreadReadState` を既読状態の正本として扱う。履歴削除は既読位置削除も意味する。

理由:
- 「どこまで読んだか」はユーザーの閲覧履歴そのものに近い。
- 履歴がないスレッドは未訪問として扱い、新着レス数バッジを表示しない方が既存の板画面挙動にも近い。

代替案として `thread_read_states` を別テーブルに分離する方法もあるが、現行 DB では `thread_histories` がすでに `ThreadReadState` を持っているため、まずは既存構造を活かす。

### Decision 3: `open_thread_tabs` はタブ固有状態に限定する

`open_thread_tabs` には `threadId`、`sortOrder`、スクロール位置のみを残す。タブ一覧の表示に必要なタイトル・レス数・既読状態は `thread_states` と `thread_histories` から合成する。

理由:
- タブを閉じても履歴や既読位置は残るべきで、タブテーブルに入れるとライフサイクルが混ざる。
- `open_thread_tabs` と `thread_histories` の二重更新をなくせる。

スクロール位置はタブ固有状態として保存するが、履歴がない場合は未訪問扱いを優先して使用しない。これにより、履歴削除後にタブを開いたとき、過去の途中位置から再開せず先頭から表示できる。

代替案として履歴削除時に `open_thread_tabs.firstVisibleItemIndex` と `firstVisibleItemScrollOffset` を 0 へ更新する方法もあるが、履歴削除処理がタブテーブルを直接更新する必要があり責務が混ざるため採用しない。

### Decision 4: 新着レス数は保存せず導出する

新着レス数は `thread_states.latestResCount` と `thread_histories` の既読状態から導出する。

```text
history がない:
  isVisited = false
  newResCount = 0

history がある && firstNewResNo が有効:
  newResCount = max(0, latestResCount - firstNewResNo + 1)

history がある && firstNewResNo が null:
  newResCount = max(0, latestResCount - lastReadResNo)
```

`latestResCount <= lastReadResNo` の場合は常に 0 とする。

### Decision 5: 更新経路は状態の種類ごとに分ける

板更新、タブ一覧更新、スレッド閲覧・更新で判明した最新レス数は `thread_states` に保存する。スレッド閲覧中の既読位置更新は `thread_histories` に保存する。

更新例:
```text
板更新:
  thread_summaries 更新
  thread_states.latestResCount/title 更新

タブ一覧更新:
  thread_states.latestResCount/title 更新
  open_thread_tabs は上書きしない

スレッド閲覧:
  thread_states.latestResCount/title 更新
  thread_histories 作成/更新
  thread_histories.lastReadResNo/firstNewResNo 更新
```

### Decision 6: `thread_states` は参照がなくなった時だけ GC する

`thread_states` はタブ閉鎖や履歴削除では即時削除しない。以下のどこからも参照されず、必要に応じて `updatedAt` が一定期間より古い場合に削除対象とする。

```text
open_thread_tabs
thread_histories
bookmark_threads
thread_summaries（現役または保持対象の板キャッシュ）
```

## Risks / Trade-offs

- [Risk] Room マイグレーションで既存データの統合条件が複雑になる → マイグレーションテストでタブのみ、履歴のみ、板キャッシュのみ、複数に存在するスレッドのケースを検証する。
- [Risk] `ThreadId` 生成に必要な host/board/threadKey が移行元によって不足する → 既存の `ThreadId` カラムを優先し、板キャッシュ由来の行は `boardId + threadKey` で既存 `thread_states` の補完に使う。
- [Risk] `thread_states.threadKey` と `threadId` 内の thread key が不一致になる → Entity 作成・Repository 更新時に `ThreadId.threadKey` から設定し、マイグレーションテストで一致を検証する。
- [Risk] 履歴がないタブの新着バッジが表示されなくなる → スレッドを開く経路で履歴を作成する既存挙動を確認し、履歴未作成タブは未訪問として扱う。
- [Risk] 履歴削除後も `open_thread_tabs` に古いスクロール位置が残る → 表示モデル生成時に履歴の有無を見てスクロール位置を無効化し、DB 上の残存値を UI に使わない。
- [Risk] JOIN や Flow 合成が増えて一覧表示の負荷が増える → `thread_states.threadId`、`boardId`、`boardUrl` に index を付与し、Repository 側では必要な板・開いているタブに絞って監視する。
- [Risk] 既読更新とレス数更新が競合して新着範囲が不整合になる → 最新レス数更新は `thread_states`、既読更新は `thread_histories` に分けつつ、UI 表示用の合成はトランザクションまたは Flow 合成時の一貫したスナップショットで扱う。

## Migration Plan

1. `thread_states` テーブルを作成し、主キーと index を定義する。`threadKey` は `threadId` から取り出した板内 key として保存する。
2. 既存 `open_thread_tabs` から `thread_states` を作成する。`resCount` は `latestResCount`、`title` と板情報は客観状態の初期値、`threadKey` は `threadId` から取り出した値として移行する。
3. 既存 `thread_histories` から `thread_states` を作成または補完する。同じ `threadId` がある場合、`latestResCount` は `max(existing.latestResCount, thread_histories.resCount)` とする。既読状態は `thread_histories` に残す。
4. 既存 `thread_summaries` は、`boardId + threadKey` から既存 `thread_states` を特定できる場合に `latestResCount` と `title` の補完に使う。単独で安全な `ThreadId` を生成できない場合は新規 `thread_states` 作成には使わない。
5. `open_thread_tabs_new` を作成し、`threadId`、`sortOrder`、`firstVisibleItemIndex`、`firstVisibleItemScrollOffset` のみをコピーする。
6. 旧 `open_thread_tabs` を削除し、`open_thread_tabs_new` を `open_thread_tabs` にリネームする。
7. Repository と Coordinator を `open_thread_tabs + thread_states + thread_histories` の合成へ接続する。
8. マイグレーションテストと状態同期テストを追加し、既存ユーザーの代表的なデータで同期結果を確認する。

Rollback は DB バージョンを進める変更のため、アプリ内で旧スキーマへ自動的に戻すことは想定しない。問題発生時は修正マイグレーションを追加し、`thread_states` を再計算できるようにする。

## Open Questions

- 履歴削除時に、開いているタブが同じスレッドを参照している場合でも既読状態を消すか。現方針では「履歴削除 = 既読状態削除」とする。
- `thread_histories.resCount` を履歴表示用スナップショットとして残すか、将来的に `thread_states.latestResCount` へ完全移行して削除するか。
- `thread_states` の GC をどのタイミングで実行するか。候補は履歴削除後、板キャッシュ整理後、アプリ起動時、明示的なキャッシュ削除時。
