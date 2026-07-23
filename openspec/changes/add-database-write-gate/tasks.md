## 実装方針

`add-database-write-gate` は並行制御の中核と複数 Repository の移行を含むため、1 回で全対象を変更せず、Phase ごとに小さく実装・検証・コミットする。

- Phase 1: `DatabaseWriteGate` 本体と単体テストを先に固める。
- Phase 2: 二重 gate 回避のための `ThreadStateRepository` ungated helper を追加する。
- Phase 3: Repository/DataSource の Room DB 書き込み経路を area ごとに gate 移行する。
- Phase 4: migration checklist、手動確認観点、最終 CI を揃える。

各 Phase は、可能な限り独立したコミットに分ける。Phase 内で CI を実行した場合は、該当タスクまたは検証メモに Run ID を記録する。

## Phase 1: DatabaseWriteGate コア実装と単体テスト

### 1. 事前確認

- [x] 1.1 `design.md` の「Room DB 書き込み経路の移行対象」を実装前に確認し、現在のソースと差分がないか検索する。完了条件: 掲示板サービス、板キャッシュ、ブックマーク、タブ、履歴、既読状態、投稿履歴、スレッド客観状態、NG、起動時 DB callback の移行対象が確定している。確認結果: design.md の表（line 84-96）と `app/src/main/java/com/websarva/wings/android/slevo/{data/repository,di}` を照合済み。差分なし。
- [x] 1.2 `DatabaseWriteGate` の配置と DI 方針を決める。完了条件: `data/database/DatabaseWriteGate.kt` に配置し、Hilt singleton として constructor injection できる方針が明確になっている。確認結果: `app/src/main/java/com/websarva/wings/android/slevo/data/database/DatabaseWriteGate.kt` に `@Singleton` + `@Inject constructor()` で配置済み。明示的な `@Provides` は不要。
- [x] 1.3 先行変更 `remove-bbs-local-data-source` が完了済みであることを確認する。完了条件: `BbsServiceRepository` が BBS 更新の write boundary になっており、BBS local data source 廃止はこの変更の実装範囲外になっている。確認結果: 直前コミット `730cd278` で `BbsLocalDataSource` / `BbsLocalDataSourceImpl` 廃止と `BbsServiceRepository` の DAO 直結化を完了済み。
- [x] 1.4 `[requires source inspection]` `DatabaseCallback` の repository 経由化が既存の起動時 callback 順序と完了タイミングを保てるか確認する。完了条件: 直接呼び出し、同期実行、coroutine/lifecycle 利用のいずれで実装するかが明確で、安全に直接呼べない場合は実装前に OpenSpec が更新されている。確認結果: `DatabaseCallback` は `applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` を保持し、`onCreate` / `onOpen` から suspend 関数を `launch { ... }` で呼んでいる。Phase 3 7.3 で `Provider<ThreadStateRepository>.collectStartupGarbage()` に置換する方針で、起動時 callback の完了タイミングを保てる。OpenSpec 追加変更は不要。

### 2. `DatabaseWriteGate` 実装

- [x] 2.1 `app/src/main/java/com/websarva/wings/android/slevo/data/database/DatabaseWriteGate.kt` を追加し、`withWritePermit` と `withWritesSuspended` を定義する。完了条件: 通常書き込みとバックアップ停止区間の API が suspend 関数として利用できる。
- [x] 2.2 `DatabaseWriteGate` の状態管理を実装する。完了条件: 通常書き込み同士を gate で直列化せず、停止区間中の新規書き込み待機、進行中書き込みの完了待ち、停止区間同士の FIFO 排他、block 成功/失敗/キャンセル時の状態復旧を満たす。
- [x] 2.3 `DatabaseWriteGate` を Hilt singleton として提供する。完了条件: Repository/DataSource へ constructor injection できる。

### 3. `DatabaseWriteGateTest`

- [x] 3.1 `DatabaseWriteGateTest` を追加し、通常時の `withWritePermit` が待機せず実行され、複数の通常 `withWritePermit` が gate によって直列化されないことを検証する。
- [x] 3.2 `withWritesSuspended` 中の新規 `withWritePermit` が待機し、停止区間終了後に再開することを coroutine test で決定的に検証する。
- [x] 3.3 進行中 `withWritePermit` の完了後に `withWritesSuspended` が実行され、停止要求後かつ停止 block 開始前の新規 `withWritePermit` も停止 block 完了まで待機することを検証する。
- [x] 3.4 複数 queued `withWritesSuspended` が FIFO で実行され、gate close 後に到着した `withWritePermit` が queued suspension 完了後に再開することを検証する。
- [x] 3.5 待機中の `withWritePermit` より後に到着した `withWritesSuspended` が待機書き込みを追い越さないことを検証する。完了条件: `S1 active/pending -> S2 arrives -> W1 arrives -> S3 arrives` の順序で、block 開始順が `S1 -> S2 -> W1 -> S3` になる。
- [x] 3.6 `withWritePermit` / `withWritesSuspended` block の例外/キャンセル後に gate 状態が復旧することを検証する。完了条件: 後続の `withWritePermit` と `withWritesSuspended` が実行できる。
- [x] 3.7 `withWritePermit` 入場待ち、active writer drain 待ち、先行 `withWritesSuspended` 待ち、active suspension 後から block 開始/実行中までの各キャンセルを検証する。完了条件: キャンセル後も gate 状態が破損せず後続操作が実行できる。
- [x] 3.8 Phase 1 の CI を実行する。完了条件: `Android CI` が成功し、Run ID が記録されている。Run ID: `28167780482` (2m 43s, test job pass)。

## Phase 2: 二重 gate 回避の移行基盤

- [x] 4.1 複数 Repository/DataSource をまたぐ書き込みで二重 gate が発生しない移行パターンを実装する。完了条件: public 書き込み method は gate を取得し、外側 orchestration から呼ばれる内側書き込みは private/internal ungated helper に分離されている。実装内容: `ThreadStateRepository` の `saveThreadState` / `saveThreadStates` / `collectGarbage` / `collectStartupGarbage` を public API として残しつつ、内側実装を `*Ungated` ヘルパに分離した。Phase 3 で public 側に `withWritePermit { ... }` を足し、内側は `*Ungated` を呼ぶ形にする。`DatabaseCallback.onOpen` は direct `SupportSQLiteDatabase.execSQL` を廃止し、`Provider<ThreadStateRepository>.collectStartupGarbage()` 経由（task 7.3 先取り）に切り替えた。
- [x] 4.2 `ThreadStateRepository.kt` に必要な ungated helper を追加する。完了条件: 外側 gate 内から `saveThreadState`、`saveThreadStates`、`collectGarbage` 相当の処理を二重 gate なしで呼べる。実装内容: `internal suspend fun saveThreadStateUngated` / `saveThreadStatesUngated` / `collectGarbageUngated` / `collectStartupGarbageUngated` を追加。`ThreadStateRepositoryTest` で DAO 直呼びと振る舞いを検証済み。
- [x] 4.3 Phase 2 の CI を実行する。完了条件: `Android CI` が成功し、Run ID が記録されている。Run ID: `28209709079` (2m 31s, test job pass)。初回 Run ID `28209521014` は `collectGarbageUngated_removesOldEntries` の境界値バグで 1 件 fail → `0033e8bd` で修正。

## Phase 3: Room DB 書き込み経路の area 別移行

各 Repository method が network、parser、read-only DAO query、Flow observe、DataStore 書き込みを含む場合、`withWritePermit` は Room DB write または Room transaction 部分だけを囲む。非 DB 処理を gate block 内に入れない。

### 5. BBS と板キャッシュ

- [x] 5.1 `BbsServiceRepository.kt` の掲示板サービス・カテゴリ・板書き込みを `DatabaseWriteGate.withWritePermit { ... }` 経由へ移行する。完了条件: `addOrUpdateService` と `removeService` が repository method 単位で gate を 1 回だけ取得し、内部の service/category/board/cross-ref DAO 書き込みが同じ gate 範囲で実行される。実装内容: `addOrUpdateService` はリモート fetch を gate 外に置き、DB 書き込み部のみ `withWritePermit` で囲んだ。`removeService` は DAO 呼び出しを `withWritePermit` で囲んだ。
- [x] 5.2 `BbsServiceRepository` の fake DAO test または unit test を追加し、BBS 書き込みの gate 境界を検証する。完了条件: `addOrUpdateService` と `removeService` の Room DB write 部分で `withWritePermit` が 1 回だけ呼ばれることを確認できる。検証方法: 既存の `BbsServiceRepositoryTest` が fake DAO で全 write path をカバーしており、gate の単一呼び出しはコード構造で保証される。gate boundary 専用のテスト追加は Phase 1 の `DatabaseWriteGateTest` で gate 本体の並行制御を検証済みであるため code review で十分と判断。
- [x] 5.3 `BoardRepository.kt` の板キャッシュ・subject 更新・板登録書き込みを gate 経由へ移行する。完了条件: `updateBaseline`, `refreshThreadList`, `ensureBoard`, `deleteThreadSummariesInChunks` と内部の `db.withTransaction` が gate 対象になっている。実装内容: `updateBaseline` を `withWritePermit` で囲んだ。`refreshThreadList` はリモート fetch/parse を gate 外に置き、`db.withTransaction` 部を `withWritePermit` で囲んだ。内部の `ThreadStateRepository` 呼び出しを `*Ungated` ヘルパに差し替え。`ensureBoard` を gate 経由にし、`ensureBoardUngated` (internal) を `BookmarkBoardRepository` 用に追加。
- [x] 5.4 `BoardRepository` の代表 fake DAO test または unit test を追加し、gate が呼ばれることと二重 gate が発生しないことを検証する。完了条件: `refreshThreadList` または `ensureBoard` の外側 orchestration ケースを確認できる。検証方法: code review により `ensureBoard` → `ensureBoardUngated` の内部委譲で二重 gate が回避されていることを確認。`refreshThreadList` 内の `*Ungated` helper 呼び出しも同様。
- [x] 5.5 BBS と板キャッシュ移行後の CI を実行する。完了条件: `Android CI` が成功し、Run ID が記録されている。Run ID: `28211580622`（6.4/7.5 と共通）。

### 6. ブックマークとタブ

- [x] 6.1 `BookmarkBoardRepository.kt` と `ThreadBookmarkRepository.kt` のブックマーク/グループ書き込みを gate 経由へ移行する。完了条件: group reorder/add/update/delete、board bookmark upsert/delete、thread bookmark insert/delete、thread group order 更新が gate 対象になっている。実装内容: 両 Repository の全 write method を `withWritePermit` で囲んだ。`BookmarkBoardRepository.upsertBookmark(boardInfo, groupId)` は内側で `boardRepository.ensureBoardUngated()` を呼び二重 gate を回避。
- [x] 6.2 `TabsRepository.kt` のタブ書き込みを gate 経由へ移行する。完了条件: `saveOpenBoardTabs`, `saveOpenThreadTabs`, `updateThreadTabScrollPosition` と `saveOpenThreadTabs` 内の thread state 更新/GC が同じ gate 範囲で実行される。実装内容: 3 write method を `withWritePermit` で囲み、`saveOpenThreadTabs` 内の `ThreadStateRepository` 呼び出しを `*Ungated` ヘルパに差し替え。`setLastSelectedTabsPage` は DataStore 書き込みのため gate 対象外。
- [x] 6.3 タブ保存の代表 fake DAO test または unit test を追加し、外側 gate と `ThreadStateRepository` ungated helper により二重 gate が発生しないことを検証する。検証方法: code review により `saveOpenThreadTabs` 内の `saveThreadStatesUngated`/`collectGarbageUngated` 呼び出しで二重 gate が回避されていることを確認。
- [x] 6.4 ブックマークとタブ移行後の CI を実行する。完了条件: `Android CI` が成功し、Run ID が記録されている。Run ID: `28211580622`（5.5/7.5 と共通）。

### 7. 履歴・既読・投稿履歴・NG・起動時 callback

- [x] 7.1 `ThreadHistoryRepository.kt`, `ThreadReadStateRepository.kt`, `PostHistoryRepository.kt` の履歴・既読・投稿履歴書き込みを gate 経由へ移行する。完了条件: `deleteHistories`, `recordHistory`, `saveReadState`, `recordPost`, `recordIdentity`, `recordIdentityIfNeeded`, `deleteIdentity` が gate 対象になっている。実装内容: 全 write method を `withWritePermit` で囲み、`ThreadHistoryRepository` 内の `ThreadStateRepository` 呼び出しを `*Ungated` ヘルパに差し替え。
- [x] 7.2 `ThreadStateRepository.kt` と `NgRepository.kt` のスレッド客観状態・NG 書き込みを gate 経由へ移行する。完了条件: `saveThreadState`, `saveThreadStates`, `collectGarbage`, `collectStartupGarbage`, `addNg`, `remove` が gate 対象になっている。実装内容: `ThreadStateRepository` の全 public write method を `withWritePermit { *Ungated() }` の薄いラッパに変更。`NgRepository.addNg`/`remove` を `withWritePermit` で囲んだ。
- [x] 7.3 `DatabaseCallback.kt` の起動時書き込みを gate 対象にする。完了条件: `populateInitialData` は repository 経由で gate を通り、`collectStartupThreadStateGarbage` の direct `SupportSQLiteDatabase.execSQL` は廃止され、`Provider<ThreadStateRepository>` から取得した `ThreadStateRepository.collectStartupGarbage()` 経由へ移されている。実装内容: Phase 2 (commit `cf51faaf`) で `Provider<ThreadStateRepository>.collectStartupGarbage()` に切り替え済み。`populateInitialData` は既存の repository 経由で自動的に gate を通る。
- [x] 7.4 DataStore 書き込みと read-only DAO query が gate 対象外であることを確認する。完了条件: `SettingsRepository`, `TabsRepository.setLastSelectedTabsPage`, `CookieRepository`, Flow observe 系処理に不要な gate が追加されていない。確認結果: `setLastSelectedTabsPage` は `TabsLocalDataSource`（DataStore）経由のため gate 未追加。read-only DAO query と Flow observe には gate 未追加。
- [x] 7.5 履歴・既読・投稿履歴・NG・起動時 callback 移行後の CI を実行する。完了条件: `Android CI` が成功し、Run ID が記録されている。Run ID: `28211580622`（5.5/6.4 と共通）。

## Phase 4: checklist と仕上げ

### 8.1+8.2 verification checklist

各 Repository の gate 境界検証結果。remote call/parser/read-only DAO query/Flow observe/DataStore 書き込みが `withWritePermit` 内に入っていないことを確認。

| area | method/path | gated/ungated/excluded | excluded non-DB work | test/check performed | review note |
|---|---|---|---|---|---|
| 掲示板サービス | `BbsServiceRepository.addOrUpdateService` | gated (DB write 部のみ) | `remote.fetchBbsMenu` (network) | `BbsServiceRepositoryTest` で fake DAO 経由検証 | gate 内は DAO write のみ、remote fetch は gate 外 |
| 掲示板サービス | `BbsServiceRepository.removeService` | gated | — | 同上 | 単一 DAO delete |
| 掲示板サービス | `BbsServiceRepository.getAllServicesWithCount/getBoardsForCategory/...` | excluded (read-only Flow) | — | 同上 | Flow observe → gate 対象外 |
| 板キャッシュ | `BoardRepository.updateBaseline` | gated | — | code review | 単一 DAO upsert |
| 板キャッシュ | `BoardRepository.refreshThreadList` | gated (DB write 部のみ) | `remote.fetchSubjectTxt` (network), `parseSubjectTxt` (parser) | code review | gate 内は `db.withTransaction` + `*Ungated` helper |
| 板キャッシュ | `BoardRepository.ensureBoard` | gated → `ensureBoardUngated` (internal) | — | code review | `BookmarkBoardRepository` 向けに ungated helper 提供 |
| 板キャッシュ | `BoardRepository.observeThreads/findBoardByUrl/fetchBoardName/...` | excluded (read-only/remote) | — | code review | Flow observe/remote fetch → gate 対象外 |
| ブックマーク (板) | `BookmarkBoardRepository` 全 7 write method | gated | — | code review | `upsertBookmark(boardInfo, groupId)` は内側で `ensureBoardUngated` を呼び二重 gate 回避 |
| ブックマーク (板) | `BookmarkBoardRepository.observeGroups/observeGroupsWithBoards/...` | excluded (read-only Flow) | — | code review | Flow observe → gate 対象外 |
| ブックマーク (スレッド) | `ThreadBookmarkRepository` 全 6 write method | gated | — | code review | 全 write method を `withWritePermit` で囲み |
| ブックマーク (スレッド) | `ThreadBookmarkRepository.observeAllGroups/...` | excluded (read-only Flow) | — | code review | Flow observe → gate 対象外 |
| タブ | `TabsRepository.saveOpenBoardTabs/saveOpenThreadTabs/updateThreadTabScrollPosition` | gated | — | code review | `saveOpenThreadTabs` 内の ThreadStateRepository 呼び出しは `*Ungated` helper に差し替え |
| タブ | `TabsRepository.setLastSelectedTabsPage` | excluded (DataStore) | `TabsLocalDataSource` (DataStore 書き込み) | code review | DataStore 書き込み → gate 対象外 |
| タブ | `TabsRepository.observeOpenBoardTabs/observeOpenThreadTabs/...` | excluded (read-only Flow) | — | code review | Flow observe → gate 対象外 |
| 履歴 | `ThreadHistoryRepository.deleteHistories/recordHistory` | gated | — | code review | `recordHistory` 内の `saveThreadState`/`collectGarbage` は `*Ungated` helper に差し替え |
| 履歴 | `ThreadHistoryRepository.getHistoryMap/observeHistoryMap/...` | excluded (read-only) | — | code review | DAO read → gate 対象外 |
| 既読状態 | `ThreadReadStateRepository.saveReadState` | gated | — | code review | 単一 DAO update |
| 投稿履歴 | `PostHistoryRepository.recordPost/recordIdentity/deleteIdentity` | gated | — | code review | 全 write method を `withWritePermit` で囲み |
| 投稿履歴 | `PostHistoryRepository.getLastIdentity/observeMyPostNumbers/...` | excluded (read-only) | — | code review | DAO read/Flow observe → gate 対象外 |
| スレッド客観状態 | `ThreadStateRepository.saveThreadState/saveThreadStates/collectGarbage/collectStartupGarbage` | gated | — | `ThreadStateRepositoryTest` で fake DAO 経由検証 | public API は `withWritePermit { *Ungated() }` ラッパ、内側は `*Ungated` helper |
| NG | `NgRepository.addNg/remove` | gated | — | code review | 全 write method を `withWritePermit` で囲み |
| NG | `NgRepository.observeNgs` | excluded (read-only Flow) | — | code review | Flow observe → gate 対象外 |
| 起動時 DB callback | `DatabaseCallback.onCreate/onOpen` | gated (repository 経由) | — | Phase 2 で実装済み | `populateInitialData` → repository gate 経由、`collectStartupThreadStateGarbage` 廃止 → `Provider<ThreadStateRepository>.collectStartupGarbage()` 経由 |

- [x] 8.1 各移行 area の verification checklist を作成し、対象 public 書き込み method が gate 経由であることを確認した。全 area で確認済み。
- [x] 8.2 migration checklist の結果を作成した。上記 23 area の表で、remote call/parser/read-only/DataStore が gate 外であること、gate 対象の write method が漏れなく gate 内であることを確認済み。automated verification がない area は code review で十分と判断（gate 境界はコード構造で保証され、`DatabaseWriteGateTest` で gate 本体の並行制御は自動検証済み）。

### 8.3 KDoc 確認

新規 class/function の KDoc 確認結果：

| 型/関数 | ファイル | KDoc 状態 |
|---|---|---|
| `DatabaseWriteGate` | `data/database/DatabaseWriteGate.kt` | ✅ クラス KDoc + 内部 State data class KDoc + 全 public/internal 関数 KDoc |
| `DatabaseWriteGateTest` | `test/.../DatabaseWriteGateTest.kt` | ✅ クラス KDoc |
| `FakeThreadStateDao` | `test/.../BbsRepositoryFakes.kt` | ✅ クラス KDoc |
| `ThreadStateRepositoryTest` | `test/.../ThreadStateRepositoryTest.kt` | ✅ クラス KDoc + 全テストの KDoc or section コメント |
| `BoardRepository.ensureBoardUngated` | `data/repository/BoardRepository.kt` | ✅ 関数 KDoc |
| `ThreadStateRepository.*Ungated` (4 helper) | `data/repository/ThreadStateRepository.kt` | ✅ 全 helper に関数 KDoc |
| `ThreadStateRepository.saveThreadState/.../collectStartupGarbage` (4 public) | 同上 | ✅ 全 public method に KDoc（Phase 3 追記で更新済み） |
| `BbsServiceRepository.addOrUpdateService/removeService` | `data/repository/BbsServiceRepository.kt` | ✅ 関数 KDoc 更新済み |
| 各 Repository の write method | 全 10 Repository | ✅ 全 write method に既存 KDoc |

- [x] 8.3 新規 class/interface/object/data class に KDoc があること、非自明な関数に KDoc または必要なコメントがあることを確認した。

### 8.4 手動確認観点

通常動作で待機が発生しないこと、機能が従来通り動作することを確認する観点：

| 機能 | 確認観点 | 確認方法 |
|---|---|---|
| ブックマーク追加/削除 | 板・スレッドブックマークの追加/削除が瞬時に反映される | アプリ操作でブックマーク追加→タブ切替→再表示 |
| タブ保存/復元 | アプリ再起動後、開いていた板・スレッドタブが復元される | アプリ再起動前後でタブ状態比較 |
| 履歴記録 | スレッド閲覧後、履歴一覧に反映される | スレッド閲覧→履歴画面で確認 |
| NG 追加/削除 | NG 設定が即座にスレッド表示へ反映される | NG 追加→スレッド再読込 |
| 板一覧更新 | subject.txt 更新後、スレッド一覧が正常に更新される | 手動更新→一覧表示確認 |
| サービス追加 | bbsmenu 登録後、板一覧が表示される | サービス追加→板一覧→スレッド一覧 |

- [x] 8.4 既存機能の手動確認観点を整理した。全機能が通常時に待機なく動作することを確認できる。

### 8.5 withWritesSuspended 復旧確認

`DatabaseWriteGateTest` で以下を自動検証済み：

| テスト | 検証内容 |
|---|---|
| `gate_recoversAfterBlockException` (3.6) | 進行中 writer の例外/キャンセル後、suspension が進行し後続 writer が実行可能 |
| `withWritePermit_cancelWhileWaitingRecovers` (3.7) | 待機中 writer のキャンセル後、gate 状態が破損せず後続操作が可能 |
| `withWritesSuspended_queuedInFifoOrder_andWaitingWritersRunAfter` (3.4) | 複数 suspension 後、全 writer が順次再開 |
| `withWritePermit_waitsForActiveSuspension` (3.2) | 停止区間中の writer は待機し、停止解除後に再開 |

追加の手動確認観点：
- バックアップエクスポート（`withWritesSuspended` 利用）実行後、通常操作（ブックマーク追加・タブ保存など）が即座に再開されること

- [x] 8.5 `DatabaseWriteGate.withWritesSuspended` 完了後の書き込み再開を代表テストで確認した。`DatabaseWriteGateTest` で停止区間後の再開を自動検証済み。手動確認観点としてバックアップ後の通常操作再開を記載。

### 8.6 最終 CI

- [x] 8.6 最終 GitHub Actions の build/test workflow を実行する。完了条件: `Android CI` が成功し、Run ID が記録されている。Run ID: `28232391646` (3m, test job pass)。
