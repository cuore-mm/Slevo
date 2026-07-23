## Context

現在の Slevo は Room DB への書き込みが複数の Repository/DataSource に分散している。例として、板一覧キャッシュ、ブックマーク、履歴、タブ、NG、投稿履歴、スレッド客観状態、起動時 GC がそれぞれ DAO または `AppDatabase.withTransaction` を直接利用している。

バックアップ作成では SDK 24 互換のため `VACUUM INTO` を使わず、WAL checkpoint 後に main DB ファイルをコピーする方針にする。このとき、checkpoint 後からコピー完了までにアプリ内の新規 DB 書き込みが入ると WAL が再生成され、コピーした DB の一貫性が弱くなる。そのため、バックアップ本体とは別変更として、Room DB 書き込みを待機させる共通 gate を先に導入する。

## Goals / Non-Goals

**Goals:**

- `DatabaseWriteGate` を singleton として導入し、通常書き込みとバックアップ用停止区間を制御できるようにする。
- Room DB への既存書き込み経路を `DatabaseWriteGate.withWritePermit { ... }` 経由に移行する。
- バックアップ側が `DatabaseWriteGate.withWritesSuspended { ... }` 相当の API で新規 DB 書き込みを待機させられるようにする。
- 複数 Repository/DataSource をまたぐ書き込みで二重 gate が発生しない移行パターンにする。
- 既存のユーザー向け挙動、DB schema、DataStore schema を変更しない。

**Non-Goals:**

- バックアップ画面、ZIP writer、DB ファイルコピー、DataStore JSON export はこの変更では実装しない。
- DataStore 書き込みを `DatabaseWriteGate` の対象にしない。
- Room の読み取り、Flow observe、remote data source、parser を gate 対象にしない。
- 複数プロセス間の DB 書き込み制御は扱わない。対象は同一アプリプロセス内の Room DB 書き込み制御である。

## Decisions

### 1. `DatabaseWriteGate` の API

推奨配置は `app/src/main/java/com/websarva/wings/android/slevo/data/database/DatabaseWriteGate.kt` とする。バックアップ専用ではなく Room DB 書き込み制御の共通基盤であるため、`data/backup` ではなく DB 制御用 package に置く。

API 例:

```kotlin
class DatabaseWriteGate @Inject constructor() {
    suspend fun <T> withWritePermit(block: suspend () -> T): T

    suspend fun <T> withWritesSuspended(block: suspend () -> T): T
}
```

- `withWritePermit`: 通常の Room DB 書き込みを実行する入口。バックアップ停止区間中は待機し、停止解除後に処理を再開する。
- `withWritesSuspended`: バックアップなどの排他処理用。新規書き込みを待機させ、既に gate 内で開始済みの書き込みが完了してから block を実行する。

API 名は `withWritePermit` と `withWritesSuspended` を必須とする。実装時に改名が必要な強い理由が見つかった場合は、実装前に OpenSpec を更新する。

実装方式は coroutine friendly な reader/writer gate とする。単純な `Mutex` だけでは「既存書き込みの完了待ち」と「新規書き込みの待機」を表現しにくいため、状態管理は以下を満たすこと。

- 停止要求がない通常時の `withWritePermit` 同士は互いに待機してはならない。DB 自体の transaction/DAO レベルの待機は Room/SQLite に委ねる。
- `withWritesSuspended` は要求時点で新規書き込みを閉じ、その後に進行中の `withWritePermit` が 0 になるまで待つ。
- `withWritesSuspended` の要求後、block 開始前に到着した `withWritePermit` も停止区間終了まで待機する。
- 複数の `withWritesSuspended` は要求順に FIFO で実行し、同時実行しない。
- 最初の `withWritesSuspended` が要求されて gate が閉じた後に到着した `withWritePermit` は、その時点でキューに入っている `withWritesSuspended` がすべて完了して gate が再び開くまで待機する。
- gate が閉じた後に `withWritePermit` が待機状態になった場合、その待機書き込みより後に到着した `withWritesSuspended` は待機書き込みを追い越してはならない。既存 suspension queue が空になったら待機中の書き込みを再開し、後続 suspension は再開した書き込みの完了後に次の停止区間として実行する。
- ordering 例: `S1` が active または pending の間に `S2`、`W1`、`S3` の順で要求された場合、実行順は `S1 -> S2 -> W1 -> S3` とする。`W1` は先行する `S2` を追い越さず、後続の `S3` にも追い越されない。
- queued `withWritesSuspended` がキャンセルされた場合はキューから除外し、後続の停止要求または待機中書き込みが通常の順序で再評価される。
- block が例外を投げても gate 状態を必ず解除する。
- block 実行前の待機中、および `withWritesSuspended` が active になって新規書き込みを閉じた後から block 開始/実行中までのキャンセルでも、writer count、停止要求、待機キューを破損させない。

### 2. 二重 gate を避ける移行パターン

複数 Repository/DataSource をまたぐ orchestration method は外側で 1 回だけ `withWritePermit` を取得する。内側から呼ばれる書き込み処理は private/internal の ungated helper に分ける。

例:

```kotlin
suspend fun saveThreadStates(updates: List<ThreadStateUpdate>) {
    databaseWriteGate.withWritePermit {
        saveThreadStatesUngated(updates)
    }
}

internal suspend fun saveThreadStatesUngated(updates: List<ThreadStateUpdate>) {
    dao.upsertAllKeepingMaxResCount(updates.map { it.toEntity() })
}
```

外側の `TabsRepository.saveOpenThreadTabs` や `BoardRepository.refreshThreadList` は、自身で gate を取得した上で `saveThreadStatesUngated` を呼ぶ。これにより deadlock と不要な待機を避ける。

掲示板サービス更新の local data source 廃止は先行変更 `remove-bbs-local-data-source` で扱う。この変更では同変更後の `BbsServiceRepository` を write boundary とし、`addOrUpdateService` と `removeService` の Room DB write 部分を `withWritePermit` で囲む。

### 3. Room DB 書き込み経路の移行対象

以下は計画作成時点で確認した Room DB 書き込み経路である。実装時はこの一覧を migration checklist として使い、変更済み/対象外の判断をコードレビューで確認する。

| area | file | methods / operations to gate |
|---|---|---|
| 掲示板サービス・カテゴリ・板 | `data/repository/BbsServiceRepository.kt` | `addOrUpdateService`, `removeService`。先行変更 `remove-bbs-local-data-source` 後の repository method 単位で Room DB write 部分を gate する |
| 板キャッシュ・subject 更新 | `data/repository/BoardRepository.kt` | `updateBaseline`, `refreshThreadList`, `ensureBoard`, `deleteThreadSummariesInChunks`。`refreshThreadList` 内の `db.withTransaction` と thread summary / fetch meta / board visit / thread state 書き込みをまとめて gate する |
| 板ブックマーク | `data/repository/BookmarkBoardRepository.kt` | `reorderGroups`, `addGroupAtEnd`, `updateGroup`, `deleteGroup`, `upsertBookmark(BookmarkBoardEntity)`, `upsertBookmark(BoardInfo, groupId)`, `deleteBookmark` |
| スレッドブックマーク | `data/repository/ThreadBookmarkRepository.kt` | `insertBookmark`, `deleteBookmark`, `addGroupAtEnd`, `updateGroup`, `deleteGroup`, `updateGroupsOrder` |
| 開いているタブ | `data/repository/TabsRepository.kt` | `saveOpenBoardTabs`, `saveOpenThreadTabs`, `updateThreadTabScrollPosition`。`saveOpenThreadTabs` 内の `threadStateRepository.saveThreadStates` と `collectGarbage` も同じ gate 範囲で扱う |
| スレッド履歴・アクセス履歴 | `data/repository/ThreadHistoryRepository.kt` | `deleteHistories`, `recordHistory`。`recordHistory` 内の `threadStateRepository.saveThreadState`、`ThreadHistoryDao.insert/update`, access insert/update をまとめて gate する |
| 既読状態 | `data/repository/ThreadReadStateRepository.kt` | `saveReadState` |
| 投稿履歴・投稿 identity | `data/repository/PostHistoryRepository.kt` | `recordPost`, `recordIdentity`, `recordIdentityIfNeeded`, `deleteIdentity`。post history / identity history / last identity の insert/upsert/delete を gate する |
| スレッド客観状態 | `data/repository/ThreadStateRepository.kt` | `saveThreadState`, `saveThreadStates`, `collectGarbage`, `collectStartupGarbage` |
| NG 設定 | `data/repository/NgRepository.kt` | `addNg`, `remove` |
| 起動時 DB callback | `di/DatabaseCallback.kt` | `populateInitialData` は repository 経由で gate 対象にする。`collectStartupThreadStateGarbage` は direct `SupportSQLiteDatabase.execSQL("DELETE ...")` を廃止し、`Provider<ThreadStateRepository>` から取得した `ThreadStateRepository.collectStartupGarbage()` 経由へ移す |

`DatabaseCallback` の repository 経由化では既存の起動時 callback 実行順序と完了タイミングを保つ。source inspection の結果、callback context から repository method を安全に直接呼べない場合は、coroutine/lifecycle/同期実行の選択を実装前に OpenSpec へ追記する。

実装前に `[requires source inspection]` として、`DatabaseCallback` が repository 経由の startup write を既存の完了タイミングを保ったまま呼べること、および先行変更 `remove-bbs-local-data-source` が完了していることを確認する。

Repository method が remote call、parser、read-only DAO query、Flow observe、DataStore 書き込みを含む場合でも、`withWritePermit` は Room DB write または Room transaction 部分だけを囲む。method 全体を gate して非 DB 処理中に書き込み停止区間を長引かせてはならない。

以下は Room DB 書き込みではないため `DatabaseWriteGate` 対象外とする。

- `SettingsRepository` / `SettingsLocalDataSourceImpl`: Preference DataStore 書き込み。
- `TabsRepository.setLastSelectedTabsPage` / `TabsLocalDataSourceImpl`: Preference DataStore 書き込み。
- `CookieRepository` / `CookieLocalDataSourceImpl`: Cookie DataStore 書き込み。
- remote data source、parser、read-only DAO query、Flow observe 系処理。

### 4. DI と利用方針

`DatabaseWriteGate` は Hilt singleton として提供する。Repository/DataSource は constructor injection で受け取り、Room DB 書き込み method の最外層で `withWritePermit` を使う。

バックアップ変更 `add-backup-export` はこの変更が実装済みであることを前提にし、DB export の checkpoint/copy 区間を `withWritesSuspended` の block 内で実行する。

## Risks / Trade-offs

- [Risk] 二重 gate により deadlock または不要な待機が起きる。 → 外側 orchestration で 1 回だけ gate を取得し、内側は ungated helper に分ける。
- [Risk] BBS local data source 廃止と gate 導入を同時に行うとレビュー範囲が大きくなる。 → `remove-bbs-local-data-source` を先行変更にし、この変更では gate 導入に集中する。
- [Risk] 移行漏れによりバックアップ中に WAL が再生成される。 → 上記 checklist を tasks とレビュー観点に含め、各 area ごとに完了条件を設定する。
- [Risk] gate 導入で既存の書き込みタイミングが遅延する。 → 通常時は待機しない設計にし、バックアップ停止区間中のみ新規書き込みを待機させる。
- [Risk] block 例外時に gate が閉じたままになる。 → `try/finally` で active writer count と停止状態を必ず復旧する。
- [Risk] 通常書き込みを gate の単一 mutex で直列化すると既存機能の体感性能が悪化する。 → 停止要求がない `withWritePermit` 同士は互いに待機しない reader/writer 方式にする。
- [Risk] 複数の停止要求と待機中書き込みの順序が曖昧だと starvation が起きる。 → `withWritesSuspended` は FIFO、gate close 後の新規書き込みは queued suspension 完了後に再開する。

## Migration Plan

1. `DatabaseWriteGate` を追加し、単体テストで gate 動作を確認する。
2. 先行変更 `remove-bbs-local-data-source` が完了済みであることを確認する。
3. `ThreadStateRepository` など内側 helper が必要な Repository から ungated helper を導入する。
4. checklist の Repository/DataSource 書き込み経路を area ごとに `withWritePermit` へ移行する。
5. `DatabaseCallback` の direct SQL 削除を廃止し、`Provider<ThreadStateRepository>` 経由の `collectStartupGarbage()` に移す。
6. 既存機能の build/test workflow を CI で確認する。
7. 問題がある場合は gate 利用箇所単位で revert できるよう、各 area の変更を小さく保つ。

## Implementation Contract

- 新規 `DatabaseWriteGate` class には KDoc を付ける。
- `withWritePermit` は Room DB 書き込みのみを囲み、read-only query、Flow observe、DataStore 書き込みを囲まない。
- `withWritesSuspended` は要求時点で新規書き込みを待機させ、進行中書き込みが 0 になってから block を実行する。
- `withWritesSuspended` の要求後から block 終了までに開始された `withWritePermit` は block 終了まで待機する。
- 通常時の `withWritePermit` 同士は gate によって直列化してはならない。
- 複数の `withWritesSuspended` は FIFO で実行し、queued suspension がある間に到着した `withWritePermit` は queued suspension 完了後に再開する。
- 待機中の `withWritePermit` より後に到着した `withWritesSuspended` は、その待機中書き込みを追い越してはならない。
- `withWritePermit` 入場待ち、active writer drain 待ち、先行 `withWritesSuspended` 待ちの各待機中に coroutine がキャンセルされても、後続の gate 操作は継続可能でなければならない。
- `withWritesSuspended` が active になって新規書き込みを閉じた後、block 開始前または block 実行中にキャンセルされても、gate 状態は復旧しなければならない。
- block が成功/失敗/キャンセルのどれでも gate 状態は復旧する。
- 複数 Repository/DataSource をまたぐ書き込みでは外側で 1 回だけ gate を取得し、内側 helper を ungated に分ける。
- `BbsServiceRepository` は先行変更 `remove-bbs-local-data-source` 後の構造を前提にし、`addOrUpdateService` と `removeService` の Room DB write 部分で gate を取得する。

## Testing Strategy

- JVM unit test:
  - 通常時の `withWritePermit` は待機せず block を実行する。
  - 停止要求がない通常時に複数の `withWritePermit` が互いに gate で直列化されず開始できること。
  - `withWritesSuspended` 実行中に開始した `withWritePermit` は待機し、block 完了後に再開する。
  - 進行中の `withWritePermit` がある状態で `withWritesSuspended` を要求した後、新規 `withWritePermit` は停止 block が完了するまで待機する。
  - 進行中の `withWritePermit` がある場合、`withWritesSuspended` は完了を待ってから block を実行する。
  - 複数 queued `withWritesSuspended` が FIFO で実行され、gate close 後の `withWritePermit` が queued suspension 完了後に再開する。
  - 待機中の `withWritePermit` より後に到着した `withWritesSuspended` が、その待機書き込みを追い越さないこと。
  - `withWritePermit` 入場待ち、active writer drain 待ち、先行 `withWritesSuspended` 待ちでキャンセルしても gate 状態が復旧する。
  - `withWritesSuspended` active 後から block 開始/実行中までにキャンセルしても gate 状態が復旧する。
  - `withWritePermit` または `withWritesSuspended` の block が例外/キャンセルしても gate 状態が復旧する。
  - `withWritesSuspended` 同士は同時実行しない。
- Repository test または fake DAO test:
  - 代表的な書き込み Repository で gate が呼ばれることを確認する。
  - 外側 orchestration から内側 ungated helper を呼ぶケースで二重 gate が発生しないことを確認する。
  - `BbsServiceRepository` では `addOrUpdateService` と `removeService` の Room DB write 部分で `withWritePermit` を 1 回だけ取得することを確認する。
  - remote call、parser、read-only DAO query、Flow observe、DataStore 書き込みが `withWritePermit` block 内に入っていないことを migration checklist に記録する。
- Migration checklist:
  - PR 本文またはコミット済みの検証メモに、area ごとの表を残す。最低限の列は `area`、`method/path`、`gated/ungated/excluded decision`、`excluded non-DB work`、`test/check performed`、`review note` とする。
  - 各 area は automated verification または checklist/code review のどちらで確認したかを必ず記録する。checklist-only の area は、その理由（単純な DAO 書き込み wrapper、既存 test で間接確認済み、または fake test の費用対効果が低い等）を `review note` に残す。
  - BBS と代表 Repository 以外の移行 area は、この checklist とコードレビューを受け入れ基準にできる。必要に応じて軽量な fake DAO/unit test を追加するが、全 area の自動テスト追加は必須にしない。
- CI:
  - 実装時に `gh workflow list` またはリポジトリの CI 定義で build/test workflow 名を確認し、GitHub Actions の該当 workflow を実行する。workflow 名を確認できない場合は停止して報告する。

## Open Questions

- なし。移行対象の Room DB 書き込み経路は本 design の checklist を基準にする。
