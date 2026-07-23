## Context

`add-backup-restore` の復元フローは、バックアップ ZIP の `database/slevo.db` を pending restore として staging し、次回 cold start 時に `SlevoApplication.onCreate()` から `PendingRestoreApplier` を同期実行して live Room DB file を差し替える。`PendingRestoreApplier` は Hilt/DAO/Repository/`AppDatabase` に依存せず、`AppDatabase` が生成される前に file swap と DataStore JSON 反映を完了する設計である。

現状の復元 validation は「バックアップ DB が現在 schema と完全一致すること」を前提にしている。主な拒否点は以下である。

- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/BackupReader.kt`
  - `manifest.databaseVersion` が現在の `AppDatabase` version と一致しない場合に拒否する。
- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/BackupDatabaseValidator.kt`
  - `PRAGMA user_version` が現在 version と一致しない場合に拒否する。
  - `room_master_table` の現在 identity hash と一致しない場合に拒否する。
  - 現在 schema の必須 application table が揃っていない場合に拒否する。
- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/PendingRestoreManager.kt`
  - staging 後の DB integrity check で同じ validator を使う。
- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/PendingRestoreApplier.kt`
  - live DB 置換後の検証で同じ validator を使う。

一方、通常のアプリ更新では Room が `PRAGMA user_version` を読み取り、登録済み migration chain に沿って古い schema から現在 schema へ移行する。古いバックアップ DB も、live DB path へ差し替えた後に Room が初回 open すれば同じ migration chain を利用できる。ただし、現在の pending restore は DataStore 反映成功後に marker/pending directory を cleanup するため、Room migration 成功前に rollback 材料を消すと migration 失敗時の復旧が弱くなる。

この変更は「案B」として、以下の方針を採用する。

1. バックアップ DB version は migration path がある古い version まで許可する。
2. DB validation を pre-migration と post-migration に分ける。
3. DB 差し替え後、Room migration が成功したことを確認するまで rollback backup と marker を保持する。
4. migration 成功確認後に pending restore を完了扱いとして cleanup する。

## Goals / Non-Goals

**Goals:**

- `manifest.databaseVersion` が現在 Room DB version より古くても、対応最小 DB version 以上で現在 version までの migration path が存在する場合は復元候補として受け付ける。
- 未来 DB version のバックアップは downgrade になるため拒否する。
- pre-migration validation では SQLite integrity と `PRAGMA user_version` の対応範囲を確認し、古い schema で正常に異なる Room identity hash や不足 table を理由に拒否しない。
- migration path は実装上の定数/registry として明示し、`DatabaseModule.provideAppDatabase()` に登録される migration と検証ロジックがズレないようにテストする。
- DB swap 後、current version のバックアップも含めて Room open 後の completion checker が成功を確認するまで rollback backup と marker を保持する。
- migration 失敗、migration 成功確認前のクラッシュ、または post-migration validation 失敗では、次回 cold start の `PendingRestoreApplier` で rollback backup から live DB を復旧し、pending restore を failed として記録する。
- current version のバックアップ復元は Room migration を必要としないが、完了 lifecycle は old version と同じ `migration-pending` → `DatabaseCallback.onOpen()` completion checker → cleanup に統一する。

**Non-Goals:**

- 未来 version のバックアップを downgrade 復元しない。
- Room migration chain が存在しない version の DB を独自変換しない。
- バックアップ ZIP format version を 2 に上げない。
- DB table/column 単位の data-only import は実装しない。
- historical schema ごとの Room identity hash や table list を pre-migration validation で検証しない。source-version schema sanity は migration path と Room migration 実行結果に委ねる。
- Room schema version や既存 entity をこの変更で追加・変更しない。
- DataStore JSON schema の versioning 変更は行わない。既存 `backupFormatVersion = 1` の JSON model 互換性に従う。
- 復元直後にアプリを自動再起動する機能は追加しない。
- 古い DB から migration されることを復元前確認ダイアログ、Snackbar、成功通知で追加表示しない。既存 UI と既存文言は維持する。

## Decisions

### 1. 古い DB は migration path がある範囲だけ許可する

`BackupReader` の `databaseVersion` 検証は完全一致から範囲 + migration path 検証へ変更する。

```text
minimumSupportedDatabaseVersion <= manifest.databaseVersion <= currentDatabaseVersion
かつ manifest.databaseVersion から currentDatabaseVersion まで連続した migration path がある
```

実装候補:

- `AppDatabase.Companion` に以下のような DB migration contract を追加する。
  - `const val CURRENT_DATABASE_VERSION = 9` 既存値を利用または明示化する。
- Phase 1 の source inspection で確定した値として `MINIMUM_RESTORABLE_DATABASE_VERSION` を追加する。現在 version まで連続 migration できる最小 version を設定し、v1 と仮定して実装を進めてはならない。
  - `val REGISTERED_BACKUP_RESTORE_MIGRATIONS: List<Migration>` または version edge list を定義する。
  - `fun hasMigrationPathForRestore(fromVersion: Int, toVersion: Int = CURRENT_DATABASE_VERSION): Boolean` を追加する。
- `DatabaseModule.provideAppDatabase()` は上記 migration list を使って `.addMigrations(...)` する。すでに migration を個別列挙している場合は、重複定義を避けるため同じ list を参照する。

未来 version は `manifest.databaseVersion > currentDatabaseVersion` として明示的に拒否する。debug build の `fallbackToDestructiveMigrationOnDowngrade(true)` は restore path の許可条件には使わない。

### 2. DB validation を pre-migration と post-migration に分離する

`BackupDatabaseValidator` は用途ごとに結果を分ける。実装名は変更可能だが、coding agent は既存 call site を以下の意味に整理する。

#### Pre-migration validation

対象:

- `BackupReader.readBackup()` が ZIP から抽出した一時 DB
- `PendingRestoreManager` が staging した pending DB
- `PendingRestoreApplier` が live path へ DB を置換した直後、Room open 前の DB

確認内容:

- DB file を読み取り専用で開ける。
- `PRAGMA integrity_check` が `ok` を返す。
- `PRAGMA user_version` が `MINIMUM_RESTORABLE_DATABASE_VERSION..CURRENT_DATABASE_VERSION` に含まれる。
- `manifest.databaseVersion` と DB file の `PRAGMA user_version` が一致する。
- `hasMigrationPathForRestore(userVersion)` が true である。

確認しない内容:

- 現在 schema の Room identity hash 一致。
- 現在 schema の全 application table 存在。
- historical schema ごとの Room identity hash 一致。
- historical schema ごとの table list 存在。

古い DB では identity hash と table list が現在 schema と違うことが正常なため、pre-migration でそれを失敗条件にしてはならない。historical schema の hash/table list 検証も、この change では導入しない。理由は、全 historical schema contract の二重管理を避け、既存 Room migration chain を唯一の schema 互換性判定にするためである。非 Slevo SQLite DB が version 範囲と integrity check だけで通った場合も、Room migration または post-migration validation で失敗させる。

#### Post-migration validation

対象:

- Room が migration を完了して `AppDatabase` を open した後の live DB。

確認内容:

- `PRAGMA integrity_check` が `ok`。
- `PRAGMA user_version == CURRENT_DATABASE_VERSION`。
- `room_master_table` の identity hash が現在 schema の hash と一致する。
- 現在 schema の必須 application table が存在する。

post-migration validation は `AppDatabase` が正常 open した後に実行する。`PendingRestoreApplier` は Hilt/Room に依存しない制約があるため、Room open 前に post-migration validation を実行してはならない。

### 3. Room migration は通常の `AppDatabase` open に任せる

staging DB を一時 Room instance で事前 migration する案は採用しない。理由は以下である。

- Hilt 外で Room builder を作る必要があり、`DatabaseModule` と migration 登録が二重化しやすい。
- `RoomDatabase.Callback`、seed、WAL、query executor など通常起動との差異が増える。
- 既存の通常更新 path と同じ migration 実行経路を使う方が保守しやすい。

採用する流れ:

```text
復元確定
  -> BackupReader: manifest/ZIP/JSON/pre-migration DB validation
  -> PendingRestoreManager: pending directory へ staging
  -> 次回起動 PendingRestoreApplier: live DB file を old DB で置換
  -> PendingRestoreApplier: DataStore JSON を反映
  -> marker は migration-pending 相当で保持
  -> 通常初期化で AppDatabase open
  -> Room が old user_version から current へ migration
  -> migration 成功確認 component が post-migration validation
  -> pending/rollback cleanup
```

### 4. migration 成功確認用 component は DatabaseCallback.onOpen() から呼び出す

`PendingRestoreApplier` は `AppDatabase` に依存しないため、Room migration 成功確認は `AppDatabase` が Hilt から生成された後に行う別 component へ分ける。

調査結果として、専用の startup coordinator は存在しない。現在の post-DB-open startup hook は `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/DatabaseCallback.kt` の `onOpen()` であり、既に `collectStartupGarbage()` を `applicationScope` 上で実行している。

採用する呼び出し位置は `DatabaseCallback.onOpen()` とする。理由は以下である。

- `SlevoApplication.onCreate()` の `PendingRestoreApplier.runIfNeeded()` は Room open 前に動くため、migration 成功確認には早すぎる。
- `MainActivity.onCreate()` の `super.onCreate()` では Hilt field injection により `TabSessionStore` などの依存解決が走り、`AppDatabase` が生成され得る。したがって `MainActivity.onCreate()` 本体へ checker を置くと、Room migration 失敗時にはその行へ到達できない。
- `DatabaseCallback.onOpen()` は Room が DB を open し、必要な migration を完了した後に呼ばれるため、post-migration validation と cleanup のタイミングとして適切である。
- 既存 startup coordinator はないため、この change では新しい coordinator を導入しない。post-DB-open task が将来増える場合だけ別 change で抽出を検討する。

実装方針:

- `data/backup/PendingRestoreCompletionChecker.kt`
  - Hilt singleton として `DatabaseCallback.onOpen()` から `Provider<PendingRestoreCompletionChecker>` 経由で遅延取得される。
  - `PendingRestoreFileStore` または同等の marker/result I/O collaborator を使う。
  - marker が `migration-pending` の場合だけ post-migration validation を実行する。
  - 成功時は marker を `completed` に更新して rollback 禁止を durable にしてから、success result 記録、pending directory cleanup、rollback backup cleanup を行う。marker 削除は cleanup の最後に行う。
  - post-migration validation 失敗時は live DB file を即時置換せず、marker を `rollback-required` に更新し、rollback backup を保持する。
- `DatabaseCallback`
  - constructor に `Provider<PendingRestoreCompletionChecker>` を追加する。既存 repository provider と同様に `Provider` を使い、`AppDatabase` 作成中の循環依存を避ける。
  - `onOpen()` 内で `applicationScope.launch { pendingRestoreCompletionCheckerProvider.get().runIfNeeded() }` を呼ぶ。
  - 既存 `collectStartupGarbage()` と同じく非同期に実行し、DB open 自体を長時間 block しない。

completion checker は重い I/O を main thread で直接実行しない。`applicationScope` は `Dispatchers.IO` 相当を使い、DB validation や file cleanup をその上で実行する。`runIfNeeded()` は idempotent にし、marker がない場合または対象 status でない場合は即 return する。

`PendingRestoreCompletionChecker.runIfNeeded()` は外へ例外を投げない。post-migration validation 失敗後に `rollback-required` marker または result file の書き込みに失敗した場合、completion checker は live DB file を置換・削除せず、既存の `migration-pending` marker と rollback backup を残す。次回 cold start の `PendingRestoreApplier` が stale `migration-pending` として strict validation → completed cleanup または rollback/quarantine を判定できるようにする。書き込み失敗は best-effort でログへ残すが、ログだけを唯一の復旧状態にしてはならない。

`rollback-required` の durable state は marker を source of truth とする。completion checker は rollback-required result を先に書き、その後で marker を `rollback-required` へ atomic replace する。result 書き込みが失敗した場合は marker を変更しない。marker replace が失敗した場合も既存 `migration-pending` marker と rollback backup を残し、次回 cold start recovery に委ねる。この場合、best-effort で書けた result file が rollback-required を示していても、recovery 判定は marker を優先し、次回処理で result file を最新状態へ上書きする。

### 5. pending marker state を拡張する

既存 marker state の意味を以下へ整理する。実装済み enum 名が異なる場合は、既存 naming に合わせてよいが、意味と遷移は一致させる。

| state | 意味 |
|---|---|
| `prepared` | staging 完了。次回起動時に DB swap できる。 |
| `applying` | 起動時に DB swap 処理中。クラッシュ時は rollback 対象。 |
| `db-swapped` | live DB file を staging DB で置換済み。Room migration は未確認。 |
| `migration-pending` | DataStore 反映まで完了し、Room migration または current DB open の成功確認待ち。rollback backup と marker を保持する。 |
| `rollback-required` | Room open 後の completion checker が post-migration validation 失敗を検出した状態。Room が live DB を開いている可能性があるため、その場では DB file を置換せず次回 cold start で rollback する。 |
| `completed` | post-migration validation 成功を durable に記録済み。cleanup が未完了または marker 削除に失敗しても rollback してはならない。 |
| `failed` | 復元失敗。自動再試行しない。failed result は保持するが、次回の新規 restore 準備を永久にはブロックしない。 |

状態遷移:

| current | event | next | behavior |
|---|---|---|---|
| none | 復元準備完了 | `prepared` | staging 完了後に marker を最後に作成する。 |
| `prepared` | 起動時適用開始 | `applying` | rollback backup を作成してから DB swap へ進む。 |
| `applying` | DB swap + pre-migration validation 成功 | `db-swapped` | DataStore 反映へ進む。 |
| `applying` | DB swap 前後で失敗 | `failed` | rollback できる場合は live DB を元に戻す。 |
| `db-swapped` | DataStore 反映成功 | `migration-pending` | rollback backup と marker を保持して通常初期化へ進む。 |
| `db-swapped` | DataStore 反映失敗 | `failed` | rollback できる場合は live DB を元に戻す。 |
| `migration-pending` | Room open + post validation 成功 | `completed` | success result を記録し、marker を completed に更新する。current DB version の場合もこの経路で完了する。 |
| `migration-pending` | Room open 後の post validation 失敗 | `rollback-required` | completion checker は live DB を即時置換せず、rollback-required result を記録する。 |
| `migration-pending` | migration 成功確認前の次回 cold start + live DB post validation 成功 | `completed` | 前回 cleanup 前に中断された可能性として rollback せず completed cleanup へ進む。 |
| `migration-pending` | migration 成功確認前の次回 cold start + live DB post validation 失敗 | `failed` | 前回 migration 途中で落ちた可能性として rollback を試行し、failed result を残す。 |
| `rollback-required` | 次回 cold start | `failed` | Room が開く前に rollback backup から live DB を復旧し、failed result を残す。 |
| `completed` | cleanup 成功 | none | pending marker を最後に削除する。 |
| `completed` | cleanup 失敗後の次回 cold start | `completed` または none | rollback せず cleanup を再試行する。cleanup が完了したら marker を削除する。 |
| `failed` | 次回起動 | `failed` | 自動再試行しない。通常起動を優先する。 |
| `failed` | 新規 restore 準備開始 | none または `prepared` | failed result を保持したまま active failed marker、staging、rollback を cleanup できた場合だけ新規 restore 準備へ進む。quarantine は調査用に保持してよい。 |

`migration-pending` が次回 cold start の `PendingRestoreApplier.runIfNeeded()` で見つかった場合、前回起動で Room migration、completion checker、または cleanup 前後に失敗した可能性がある。誤 rollback を避けるため、rollback backup の有無を確認する前に、必ず live DB に対して post-migration validation と同等の strict validation を実行する。strict validation が成功する場合は rollback backup が存在しなくても marker を `completed` に更新し、rollback せず cleanup を再試行する。strict validation が失敗する場合だけ rollback backup から live DB を rollback し、marker を `failed` にする。strict validation が失敗し rollback backup もない場合は、invalid live DB file-set を quarantine して fresh DB 起動を優先し、failed result に「rollback backup missing / invalid restored DB quarantined」相当を記録する。

`rollback-required` は completion checker が Room open 後に post-migration validation 失敗を検出した場合だけ書く。Room が live DB を開いている可能性があるため、completion checker は live DB main file、`-wal`、`-shm` を置換または削除してはならない。次回 cold start の `PendingRestoreApplier` が `AppDatabase` 生成前に rollback backup から復旧し、復旧後に `failed` へ遷移する。

`rollback-required` の次回 cold start で rollback backup が存在しない場合も、invalid live DB file-set を quarantine して fresh DB 起動を優先し、failed result に「rollback backup missing / invalid restored DB quarantined」相当を記録する。quarantine は live DB main file、`-wal`、`-shm` を `pending-restore/quarantine/` 相当の復旧調査用 directory へ move する。move できない場合は copy → delete を試し、quarantine/delete も失敗した場合は failed result に「manual intervention required」相当を記録する。この場合は通常起動を保証できないため、ログ/result file で明示する。

`rollback-required` を記録した現在 session では、in-session DB recovery、live DB file 置換、process restart は行わない。completion checker は marker/result を永続化して return し、既存 UI 文言方針の範囲で汎用失敗として扱える状態に留める。実際の rollback/quarantine は次回 cold start の `PendingRestoreApplier` に限定する。

quarantine 成功後は、live DB path に invalid main DB、`-wal`、`-shm` が残っていないことを fresh DB 起動可能条件にする。quarantine directory は failed result の診断情報として path を記録し、次回の新規 restore 準備を妨げない診断 artifact として扱う。新規 restore 準備時に terminal `failed` marker が残っている場合は、failed result と quarantine directory を保持したまま active marker、staging、rollback を cleanup し、cleanup に成功した場合だけ新しい `prepared` marker を作成する。quarantine directory を自動削除する場合は、別途明示的な cleanup task と test を追加する。failed result は latest status document なので、新しい restore が開始された後は新しい処理結果で上書きされてよい。

`completed` は post-migration validation 成功を marker に durable に残すための状態である。completion checker は marker を `completed` に更新してから success result、rollback backup、staging files を処理する。completed marker には success result を再構築できる最小診断情報（backup/current DB version、migrationRequired、migrationCompleted、完了時刻相当）を含める。success result の書き込みに失敗した場合は、rollback backup と staging files を削除してはならない。次回 cold start で `completed` marker から success result 書き込みを再試行し、成功してから rollback backup と staging files を cleanup する。marker file は cleanup の最後に削除する。completed marker 書き込み後に rollback backup 削除、staging cleanup、marker 削除のいずれかが失敗しても、次回 cold start で `completed` marker が残っていれば rollback してはならない。post-migration validation 成功後に completed marker 書き込み自体が失敗した場合は `migration-pending` と rollback backup を残し、次回 cold start の strict validation success 経路で completed へ進ませる。

rollback backup の file-set は live DB main file と、その sibling の `-wal` / `-shm` である。main DB が存在する場合は main DB rollback backup の作成に成功しなければ DB swap へ進まない。`-wal` / `-shm` は存在する場合に best-effort で backup し、失敗は詳細ログ/result file に記録する。cold-start rollback では、Room が live DB を開く前に live DB path の `-wal` / `-shm` を先に best-effort で削除し、rollback backup の main DB を戻し、rollback backup に `-wal` / `-shm` が存在する場合だけそれらを戻す。rollback backup に `-wal` / `-shm` が存在しない場合は、元々存在しなかったものとして live 側にも残さない。

result file は event history ではなく「latest status + diagnostics」を保持する単一 document として扱う。`rollback-required` が書かれた後、次回 cold start で rollback 成功、quarantine 成功、または quarantine 失敗が確定したら、同じ result file を final `failed` status で上書きする。final failed result には `previousStatus = "rollback-required"`、`rollbackRequiredAt`、`finalFailureReason` 相当を含め、途中状態が失われないようにする。

preview-only の invalid 判定では、まだ pending restore/result file を作成していない場合があるため、詳細 reason は詳細ログのみでもよい。ユーザーが復元を確定して `BackupRepository.restoreBackup(...)` の commit path が開始し、pending restore/result area または result writer を確保できた後に検出した invalid/failure reason は result file に永続化する。result area 自体を作成できない場合は result file を書けないため、詳細ログへ記録し、既存の failure result mapping で呼び出し元へ返す。

診断 field の値は以下に固定する。

| 状態 | backupDatabaseVersion | currentDatabaseVersion | migrationRequired | migrationCompleted |
|---|---:|---:|---|---|
| current version success | current | current | false | true |
| old version success | backup version | current | true | true |
| rollback-required | backup version | current | backup version < current | false |
| failed after rollback | backup version | current | backup version < current | false |
| rollback backup missing + quarantine success/failure | backup version | current | backup version < current | false |

`backupDatabaseVersion` が marker/result から取得できない異常系では `null` または `unknown` 相当を許容するが、その場合も `currentDatabaseVersion` と failure reason は result file に記録する。

### 6. UI は変更せず result file に診断情報を永続化する

古い DB version のバックアップを許可しても、復元前確認ダイアログ、Snackbar、成功通知、`BackupUiState` の表示項目は変更しない。既存 UI は `databaseVersion` を現在通り表示するだけに留め、古い DB から migration されたことを追加の user-facing message として出さない。

内部的には、調査や不具合解析のために以下を restore result file に永続化する。詳細ログにも同じ情報を出してよいが、ログだけに依存してはならない。

- `backupDatabaseVersion`
- `currentDatabaseVersion`
- `migrationRequired`
- `migrationCompleted`

これらは UI 表示のための必須 state ではなく、復元処理の診断情報である。post-migration validation 失敗時は success result を記録せず、`rollback-required` と診断情報を result file に記録する。この change では追加 UI 文言を出さず、既存の result/通知読み取り経路がある場合も既存の汎用失敗表示に留める。次回 cold start rollback 後は failed result を記録する。

### 7. DataStore JSON は現行 backup format version 1 の互換性を前提にする

古い DB version のバックアップでも `backupFormatVersion = 1` の `settings.json`、`tabs.json`、`cookies.json` を読み込む。Moshi model の default 値で後方互換できる field は受け付ける。未知 enum、null 不許可 field の null、不正 scale など既存 validation で拒否する条件は維持する。

この変更では DataStore JSON の個別 versioning は導入しない。将来 JSON schema を非互換変更する場合は別 change で `backupFormatVersion` を上げる。

## Risks / Trade-offs

- [Risk] Room migration が通常初期化時に失敗すると、DB swap と DataStore 反映後にアプリ起動が中断される可能性がある。
  - Mitigation: `migration-pending` state と rollback backup を migration 成功確認まで保持し、次回 cold start 時に rollback する。
- [Risk] migration list と `DatabaseModule.provideAppDatabase()` の `.addMigrations(...)` がズレると、preview では許可したのに Room open で失敗する。
  - Mitigation: migration list を共有定義に寄せ、登録済み migration edge の連続性を unit test で検証する。
- [Risk] pre-migration validation を緩めることで、schema が想定外の DB を受け付ける可能性がある。
  - Mitigation: `manifest.databaseVersion == PRAGMA user_version`、version 範囲、migration path、SQLite integrity を必須にする。現在 schema の hash/table check は post-migration で行う。historical schema の hash/table check は二重管理を避けるため導入しない。
- [Risk] DataStore は DB より先に反映されるため、migration 失敗 rollback 後に DataStore は完全には戻らない。
  - Mitigation: 既存 `add-backup-restore` と同様に DataStore rollback は non-goal とし、DB rollback と failed result/log でユーザーに通知する。必要なら将来 change で DataStore rollback を検討する。
- [Risk] very old DB の migration に時間がかかり、初回起動が遅くなる。
  - Mitigation: 大きな migration は既存 Room migration に従う。この変更では追加 UI 表示を行わず、必要な診断情報は result file に永続化する。
- [Risk] `DatabaseCallback.onOpen()` から checker を非同期起動するため、UI の DB read と cleanup が並行する可能性がある。
  - Mitigation: checker は marker file 確認、post-migration validation、pending/rollback cleanup に限定し、Room table へ書き込まない。`runIfNeeded()` は idempotent にし、marker がなければ即 return する。
- [Risk] post-migration validation 失敗時に Room open 中の live DB file を置換すると DB 破損や connection inconsistency を招く可能性がある。
  - Mitigation: completion checker は post-migration validation 失敗時に DB file を即時 rollback しない。`rollback-required` を記録し、次回 cold start の `PendingRestoreApplier` が `AppDatabase` 生成前に rollback する。
- [Risk] rollback backup がない状態で invalid restored DB が live path に残ると、Room open が再失敗して通常起動できない。
  - Mitigation: 次回 cold start で rollback backup がなく strict validation も失敗する場合は、invalid live DB file-set を quarantine して fresh DB 起動を優先する。quarantine/delete も失敗した場合は manual intervention required として failed result に記録する。
- [Risk] terminal failed marker が残ることで、以後の復元準備が永久に拒否される可能性がある。
  - Mitigation: failed marker は自動再試行には使わない。新規 restore 準備時は failed result を保持したまま active failed marker/staging/rollback を cleanup できた場合に限り、新しい restore を準備できる。
- [Risk] completion checker が rollback-required marker/result の書き込みに失敗し、Room open 中に不完全な recovery を試みる可能性がある。
  - Mitigation: completion checker は例外を外へ投げず、live DB file を変更せず、migration-pending と rollback backup を残して次回 cold start recovery に委ねる。
- [Risk] post-migration validation 成功後の cleanup が部分的に失敗すると、次回 cold start で stale `migration-pending` を migration 未完了と誤認して rollback する可能性がある。
  - Mitigation: `completed` marker を cleanup より先に書き、marker 削除を cleanup の最後に行う。stale `migration-pending` では rollback backup の有無を見る前に live DB strict validation を再実行し、成功していれば completed cleanup へ進む。

## Migration Plan

1. `AppDatabase` と `DatabaseModule` の migration 定義を調査し、現在 version と最小復元対応 version、連続 migration path を確認する。
2. migration path helper と migration registration 共有化を追加する。
3. `BackupReader` の `databaseVersion` validation を range/path validation に変更する。
4. `BackupDatabaseValidator` を pre/post migration 用途へ分離する。
5. `PendingRestoreManager` と `PendingRestoreApplier` の call site を pre-migration validation へ切り替える。
6. marker state に `migration-pending`、`rollback-required`、`completed` 相当を追加し、DB swap + DataStore 反映後は current/old version を問わず cleanup せず marker/rollback を保持する。
7. `DatabaseCallback.onOpen()` から `Provider<PendingRestoreCompletionChecker>` 経由で completion checker を起動し、post-migration validation 成功時に cleanup する。
8. stale `migration-pending`、`rollback-required`、`completed` recovery を追加する。stale migration-pending は rollback 前に live DB strict validation を再実行する。
9. current/old/future/corrupt/migration failure の unit tests と必要な instrumented/manual tests を追加する。
10. `openspec validate support-legacy-backup-restore --strict`、Android CI、実機/エミュレータで current backup と old DB backup の復元を確認する。

Rollback strategy:

- この変更の実装を revert すれば、復元対象は current DB version 完全一致へ戻る。
- 実行時に migration が失敗したユーザー端末では `migration-pending` stale recovery または `rollback-required` recovery により、次回 cold start で rollback backup から元 live DB を戻し、failed result を残す。
- post-migration validation 成功後に cleanup が失敗した端末では `completed` marker または stale `migration-pending` strict validation success により rollback を避け、cleanup を再試行する。

## Implementation-time Source Inspection

- `MINIMUM_RESTORABLE_DATABASE_VERSION` は Phase 1 の source inspection で確定する。確定するまで Phase 2 以降の実装へ進まない。

## Resolved Decisions

- completion checker の呼び出し位置は `DatabaseCallback.onOpen()` に確定する。`MainActivity.onCreate()` 本体ではなく、Room migration 完了後に呼ばれる post-DB-open hook で実行する。
- UI 変更は行わず、古い DB から migration した旨は user-facing result へ追加表示しない方針で確定する。

## Implementation Contract

- アプリコード実装時は、`PendingRestoreApplier` に `AppDatabase`、DAO、Repository、Hilt EntryPoint を注入しない。
- `PendingRestoreApplier.runIfNeeded()` は外へ例外を投げない。例外時は marker/result を `failed` として記録し、通常初期化を継続できる状態を優先する。
- `BackupReader` は preview と commit の両方で同じ databaseVersion range/path validation を行う。
- pre-migration validation では現在 Room identity hash と現在 table list を要求しない。
- post-migration validation は Room が DB を open して migration した後にだけ行う。
- completion checker は `DatabaseCallback.onOpen()` から `Provider<PendingRestoreCompletionChecker>` 経由で起動する。`MainActivity.onCreate()` 本体や `SlevoApplication.onCreate()` には置かない。
- `PendingRestoreCompletionChecker.runIfNeeded()` は外へ例外を投げない。失敗時も Room open 中の live DB file を変更せず、retry 可能な marker/rollback 状態を残す。
- completion checker は post-migration validation 失敗時に live DB file を即時 rollback しない。`rollback-required` を記録し、次回 cold start の `PendingRestoreApplier` が Room open 前に rollback する。
- current DB version の restore も DB swap + DataStore 反映後は `migration-pending` に入り、`DatabaseCallback.onOpen()` の completion checker で cleanup する。
- completion checker は post-migration validation 成功後、`completed` marker を cleanup より先に書く。success result 書き込みや cleanup の途中失敗時に残った `completed` marker は rollback 禁止の durable signal として扱う。
- rollback backup/restore の対象 file-set は main DB、`-wal`、`-shm` である。cold-start rollback では live 側 `-wal` / `-shm` を削除してから main DB と backup 済み WAL/SHM を戻す。
- rollback backup がなく strict validation も失敗する場合は、invalid live DB file-set を quarantine して fresh DB 起動を優先する。result file は単一 latest status document とし、rollback-required から final failed へ上書きする。
- terminal failed marker は新規 restore を永久にブロックしない。新規 restore 準備時に failed marker/staging/rollback cleanup が成功した場合、新しい restore を準備できる。
- quarantine directory は診断 artifact であり、新規 restore 準備をブロックしない。failed marker cleanup で quarantine を暗黙に削除しない。
- future DB version は debug/release に関係なく拒否する。
- 既存 backup ZIP entry 構造と `backupFormatVersion = 1` は維持する。
- 既存 UI、確認ダイアログ、Snackbar、成功通知文言は変更しない。migration 有無は内部ログ/result file の診断情報として扱う。
- migration 診断情報は restore result file に永続化する。ログ出力は任意補助であり、唯一の記録先にしてはならない。
- すべての新規 class/interface/object/data class/sealed class/enum には KDoc を付け、`@Preview` 関数には KDoc を付けない。
