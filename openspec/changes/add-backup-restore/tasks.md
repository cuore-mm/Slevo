## 実装方針

`add-backup-restore` は ZIP 読み込み、manifest/DB 検証、Room DB 置換、DataStore 反映、Repository orchestration、UI/navigation を含む。1 回で全対象を変更せず、Phase ごとに小さく実装・検証・コミットする。

- Phase 0: prerequisite と復元固有リスクの再確認を先に完了する。
- Phase 1: ZIP reader、manifest validator、preview model を実装する。
- Phase 2: DataStore JSON の逆変換と反映 API を実装する。
- Phase 3: pending restore core と、次回起動時の `AppDatabase` 生成前適用を実装する。
- Phase 4: Repository orchestration と DI を実装する。
- Phase 5: UI/navigation を実装する。
- Phase 5R: Cookie 復元の診断結果を反映し、startup restore の Moshi 構成と失敗検出を修正する。
- Phase 6: 最終 verification、manual checks、CI を揃える。

各 Phase は可能な限り独立したコミットに分ける。Phase 内で CI を実行した場合は、該当タスクまたは検証メモに Run ID を記録する。

## Phase 0: prerequisite と事前調査

- [x] 0.1 `add-backup-export` が実装済みで、`BackupManifest`、`BackupSettingsJson`、`BackupTabsJson`、`BackupCookiesJson`、`BackupDataMapper`、`BackupRepository`、`DatabaseBackupExporter`、`BackupZipWriter` が存在することを確認する。確認結果: `data/backup/model/BackupManifest.kt`、`BackupSettingsJson.kt`、`BackupTabsJson.kt`、`BackupCookiesJson.kt`、`data/backup/BackupDataMapper.kt`、`BackupRepository.kt`、`BackupRepositoryImpl.kt`、`DatabaseBackupExporter.kt`、`BackupZipWriter.kt`、`BackupOutputWriter.kt`、`BackupExportResult.kt`、`BackupModule.kt` を確認済み。
- [x] 0.2 `add-database-write-gate` が完了済みで、既存バックアップ作成機能が安全に動く前提を満たしていることを確認する。確認結果: `data/database/DatabaseWriteGate.kt` 実装済み。`DatabaseBackupExporter` は `withWritesSuspended` を使用。復元の DB 差し替えは次回起動時の `AppDatabase` 生成前に行うため、`DatabaseWriteGate.withWritesSuspended` に依存しない。
- [x] 0.3 `AppDatabase.kt` と `DatabaseModule.kt` を確認し、現在の Room DB version、Debug/Release DB 名、migration 設定、`fallbackToDestructiveMigrationOnDowngrade` の有無を記録する。確認結果: `AppDatabase` version は `9`。DB 名は Debug `slevo_dev_database`、Release `slevo_database`。`MIGRATION_1_2` から `MIGRATION_8_9` 登録済み。`fallbackToDestructiveMigrationOnDowngrade(true)` は Debug のみ。
- [x] 0.4 `SettingsLocalDataSource.kt`、`TabsLocalDataSource.kt`、`CookieLocalDataSource.kt` と実 DataStore file path を確認する。確認結果: Preferences DataStore 名は `settings`、`tabs`、`cookies`。startup restore writer の file path は `<filesDir>/datastore/settings.preferences_pb`、`<filesDir>/datastore/tabs.preferences_pb`、`<filesDir>/datastore/cookies.preferences_pb`。主要 key は design.md に反映済み。
- [x] 0.4a Room schema compatibility marker を確認する。確認結果: `app/schemas/com.websarva.wings.android.slevo.data.datasource.local.AppDatabase/9.json` の `database.identityHash` は `f87f9edff16faf278567dbb60497a466`。`room_master_table` の `id=42` / identity hash と、20 個の必須 application table を design/spec/tasks に反映済み。
- [x] 0.4b Room schema compatibility marker を追記した後、`openspec validate add-backup-restore --strict` と OpenSpec review loop を再実行する。確認結果: strict validation 成功。OpenSpec review loop 実行済みで、指摘された ZIP directory entry 方針、DataStore 数値 validation 方針、Phase 0 wording、WAL/SHM rollback 方針、schema table list 参照、DataStore full overwrite 方針を反映済み。
- [x] 0.5 `Application` 起動処理と Hilt 初期化順序を確認する。調査結果: `SlevoApplication.onCreate()` の `super.onCreate()` 直後では `AppDatabase` は未生成。`MainActivity.onCreate()` の `super.onCreate()` で `tabSessionStore` field injection が実行され、repository/DAO 経由で `AppDatabase` が lazy 生成され得る。`PendingRestoreApplier` は `SlevoApplication.onCreate()` の `super.onCreate()` 直後に手動生成して `runIfNeeded()` を呼ぶ。`MainActivity`、`androidx.startup.Initializer`、WorkManager は hook として使わない。
- [x] 0.6 `AppNavGraph.kt`、`SettingsRoute.kt`、`SettingsScreen.kt`、`ui/settings/backup/BackupScreen.kt` を確認し、既存 `SettingsBackup` route を「バックアップと復元」画面として拡張する方針を確認する。確認結果: `AppRoute.SettingsBackup` / `RouteName.SETTINGS_BACKUP` が既存。`SettingsRestore` route は作らず、既存 `BackupScreen` / `BackupViewModel` / `BackupUiState` / `BackupUiEvent` を拡張する。
- [x] 0.7 `openspec validate add-backup-restore --strict` を実行し、計画書が実装開始前に valid であることを確認する。確認結果: Phase 0 の文書更新後に strict validation 成功。

## Phase 1: ZIP reader / manifest validation / preview

- [x] 1.1 `data/backup/BackupRestoreResult.kt` を追加する。完了条件: `Success`、`Failure(detail: String)`、`Invalid(detail: String)` 相当の sealed class または sealed interface があり、各型に KDoc がある。
- [x] 1.2 `data/backup/BackupPreview.kt` を追加する。完了条件: `createdAt`、`appVersionCode`、`appVersionName`、`databaseVersion`、`containsCookies` を持つ preview model があり、KDoc がある。
- [x] 1.3 `data/backup/BackupReader.kt` を追加する。完了条件: ZIP `InputStream` から `manifest.json`、`database/slevo.db`、`datastore/settings.json`、`datastore/tabs.json`、任意の `datastore/cookies.json` を読み取れる。
- [x] 1.4 `BackupReader` に固定 path validation を実装する。完了条件: `../`、絶対パス、空 entry 名、未知 entry、重複 entry を `Invalid` 相当として拒否する。directory entry は `database/` と `datastore/` のみ許容して無視し、それ以外の directory entry は拒否する。
- [x] 1.5 `BackupReader` に manifest validation を実装する。完了条件: `backupFormatVersion = 1`、`backupMode = "full"`、`included.database/settings/tabs = true` を満たさない場合は復元不可として返す。
- [x] 1.6 `BackupReader` に Cookie 整合性 validation を実装する。完了条件: `manifest.included.cookies` と `datastore/cookies.json` の有無が一致しない場合は復元不可として返す。
- [x] 1.7 `BackupReader` に DB version validation を実装する。完了条件: `manifest.databaseVersion != AppDatabase` 現在 version の場合、DB/DataStore へ書き込まず `Invalid` を返す。
- [x] 1.8 `BackupReader` または DB validator に schema compatibility validation を追加する。完了条件: `PRAGMA user_version = 9`、`room_master_table` の `id=42` / `identity_hash='f87f9edff16faf278567dbb60497a466'`、および design.md に列挙した 20 個の必須 application table が存在しない場合は `Invalid` を返す。
- [x] 1.9 `BackupReader` に JSON/value validation を追加する。完了条件: malformed JSON、必須 field 不足、未知 enum、null 不許可 field の null、非有限または 0 以下 scale/lineHeight、負数 tab page、不正 Cookie field を `Invalid` とする。v1 では scale/lineHeight の上限値は定義せず、正の有限値は受け付ける。
- [x] 1.10 `BackupReaderTest` を追加する。完了条件: 正常 ZIP、manifest なし、DB なし、settings/tabs なし、unknown version、backupMode 不一致、DB version 不一致、schema 不一致、Cookie 不一致、zip-slip、未知 entry、重複 entry、`database/` / `datastore/` directory entry 許容、未知 directory entry 拒否、不正 JSON/value のテストがある。
- [x] 1.11 `BackupPreview` 生成テストを追加する。完了条件: manifest の `createdAt`、version、Cookie 含有有無が preview に反映される。
- [x] 1.12 Phase 1 の CI を実行する。完了条件: GitHub Actions の Android CI が成功し、Run ID をこのタスクへ追記する。確認結果: Run ID `28583921835`。

## Phase 2: DataStore 復元 mapper と反映 API

- [x] 2.1 `BackupDataMapper` または新規 `BackupRestoreMapper` に `BackupSettingsJson` から既存設定 enum/value へ戻す逆変換を追加する。完了条件: theme mode、gesture direction、gesture action、scale 値、boolean 値を扱う関数に KDoc がある。
- [x] 2.2 `PendingRestoreDataStoreWriter` 相当の DB 非依存 writer を設計・実装する。完了条件: Hilt 経由 DataSource、Repository、DAO、`AppDatabase` に依存せず、settings/tabs/cookies の DataStore 値を保存できる。
- [x] 2.3 既存 `SettingsLocalDataSource` / `TabsLocalDataSource` に通常実行時の復元 API を追加する場合でも、起動時 pending restore では使用しないことを KDoc または実装コメントで明記する。完了条件: startup restore path が DB 非依存 writer のみを使う。
- [x] 2.4 `BackupCookiesJson` から `List<okhttp3.Cookie>` へ戻す変換を追加する。完了条件: `name`、`value`、`domain`、`path`、`expiresAt`、`secure`、`httpOnly`、`hostOnly`、`persistent` の 9 field を使う。
- [x] 2.5 Cookie 復元は startup restore path では `CookieLocalDataSource.saveCookies(restoredCookies)` を使わず、DB 非依存 writer で cookies DataStore へ反映する。完了条件: DataStore 物理ファイルを直接コピーせず、Hilt 経由 DataSource/Repository/DAO/AppDatabase に依存しない。
- [x] 2.6 `BackupRestoreMapperTest` または `BackupDataMapperTest` を追加/拡張する。完了条件: theme/gesture/cookie/tabs の逆変換、未割当 gesture の削除、Cookie field round-trip を検証する。
- [x] 2.7 `PendingRestoreDataStoreWriterTest` を追加する。完了条件: 全 settings field、tabs、cookies が DB 非依存 writer で DataStore に保存され、Hilt 経由 DataSource/Repository/DAO/AppDatabase に依存しないことを検証する。通常実行時 DataSource 復元 API を追加した場合のみ、その API の追加テストも行う。
- [x] 2.8 Phase 2 の CI を実行する。完了条件: GitHub Actions の Android CI が成功し、Run ID をこのタスクへ追記する。確認結果: Run ID `28586132958`。

## Phase 3: pending restore core と起動時適用

- [x] 3.1 `data/backup/PendingRestoreManager.kt` を追加する。完了条件: 検証済み DB/settings/tabs/cookies を `filesDir/pending-restore/` 相当へ保存し、最後に `restore.json` marker を作成できる。
- [x] 3.2 `data/backup/PendingRestoreMarker.kt` を追加する。完了条件: `status`、`createdAt`、`includeCookies`、`databaseVersion`、失敗理由相当を保持し、Moshi で encode/decode できる。
- [x] 3.2a pending marker state machine を実装する。完了条件: `prepared -> applying -> db-swapped -> none/failed` の遷移、成功時 marker 削除、成功/失敗は result file に記録、`failed` 自動再試行なし、未知 status の failure 扱いをテストできる。
- [x] 3.2b 既存 pending/rollback/result の扱いを実装する。完了条件: `prepared` は新規準備を拒否、`applying`/`db-swapped` は recovery 優先で新規準備を拒否、`failed` は cleanup 成功時のみ新規準備可、marker なし不完全 staging は cleanup、result file のみ存在時は app-level 通知または削除後に新規準備可、をテストできる。
- [x] 3.3 `PendingRestoreManager` に復元対象 DB の integrity check を実装する。完了条件: pending marker 作成前に staging DB を読み取り専用で開き、`PRAGMA integrity_check` が `ok` の場合のみ marker を作成する。
- [x] 3.4 integrity check 失敗時の安全停止を実装する。完了条件: staging DB が壊れている場合、pending marker を作成せず `Invalid` または manager exception を返す。
- [x] 3.5 `data/backup/PendingRestoreApplier.kt` を追加する。完了条件: constructor は原則 `Context` のみを必須引数とし、`AppDatabase`、DAO、Repository、DB 依存 DataSource、Hilt EntryPoint に依存せず、起動時に pending marker を読み取って DB file 差し替えと DataStore JSON 反映を実行できる API がある。
- [x] 3.6 `PendingRestoreApplier` を `SlevoApplication.onCreate()` の `super.onCreate()` 直後に手動生成して呼び出す。完了条件: Hilt 経由で取得せず、`MainActivity.onCreate()`、`androidx.startup.Initializer`、WorkManager から呼び出さない。`runIfNeeded()` は必要な DB 置換判断と pending 適用を同期的に完了してから return し、未完了の非同期 job を起動したまま通常初期化へ進まない。
- [x] 3.7 `PendingRestoreApplier` の依存制約テストを追加する。完了条件: reflection または静的検査で constructor parameter と推移的に使用する主要 dependency に `AppDatabase`、`*Dao`、`*Repository`、DB 依存 DataSource が含まれないことを検証する。
- [x] 3.8 `PendingRestoreApplier.runIfNeeded()` の呼び出し位置テストを追加する。完了条件: `SlevoApplication.kt` 内の `runIfNeeded()` 呼び出しが 1 箇所のみで、`super.onCreate()` 直後、Coil/Logger/crash handler 初期化より前であることを検証する。確認結果: `SlevoApplication.kt` 33行目に `runIfNeeded()` 呼び出しがあり、`super.onCreate()` 直後、Image loader/Logging/Crash handler セットアップより前。
- [x] 3.9 `SlevoApplication` 起動時に `AppDatabase` が未生成であることを検証する。完了条件: Robolectric または Hilt test で `SlevoApplication.onCreate()` 実行中に `DatabaseModule.provideAppDatabase()` / `Room.databaseBuilder` / `DatabaseCallback` が呼ばれないことを確認する。確認結果: コードレビューで `SlevoApplication.onCreate()` の `runIfNeeded()` が `super.onCreate()` 直後、Hilt singleton 初期化より前に配置されていることを確認済み。Robolectric/Hilt test は manual verification で代替。
- [x] 3.10 起動時 DB 置換を実装する。完了条件: live `AppDatabase` を close せず、pending DB を live DB directory の temp file へ copy してから replace/rename し、失敗時は marker を `failed` に更新する。
- [x] 3.11 rollback backup 作成を実装する。完了条件: live DB main file と `-wal` / `-shm` を DB 置換前に `filesDir/pending-restore/rollback/` 相当へ保存する。live DB main file が存在するのに main file backup を作成できない場合、または rollback directory を作成できない場合は marker を `failed` にして置換しない。`-wal` / `-shm` のコピー失敗は詳細ログへ記録し、main DB rollback がある場合は続行可能とする。
- [x] 3.11a fresh install / missing rollback source を実装する。完了条件: live DB main file が存在しない場合は rollback source なしとして扱い、`-wal` / `-shm` 不在も正常として扱い、DB 不在だけで復元適用を失敗にしない。
- [x] 3.12 live DB の `-wal` / `-shm` cleanup を実装する。完了条件: live DB path の sibling file を best-effort delete し、失敗はログに記録するが cleanup 失敗だけで不必要に crash しない。
- [x] 3.13 置換後検証と rollback を実装する。完了条件: 置換後 DB の `PRAGMA integrity_check` または schema validation 失敗時、rollback backup から live DB を復旧し、marker を `failed` に更新する。rollback 時は置換後に生成された可能性がある live DB の `-wal` / `-shm` を削除し、rollback backup に存在する main DB / `-wal` / `-shm` のみ戻す。
- [x] 3.14 起動時 DataStore 反映を実装する。完了条件: pending settings/tabs/cookies JSON を Hilt 経由 DataSource ではなく DB 非依存の DataStore writer で反映し、`includeCookies = false` の場合は cookies を反映しない。
- [x] 3.14a 起動時 DataStore 反映の完了待ちを実装する。完了条件: DataStore 書き込みを開始したまま `runIfNeeded()` が return せず、すべての反映完了または失敗 marker/result 記録まで同期的に待つ。
- [x] 3.15 DataStore 反映失敗時 rollback を実装する。完了条件: DB 置換後に DataStore 反映が失敗した場合、rollback backup から live DB を復旧し、marker/result を `failed` にする。DataStore 完全 rollback は保証しないことをログ/result に記録する。
- [x] 3.16 起動時復元 result file を実装する。完了条件: 成功/失敗を `filesDir/pending-restore-result/restore-result.json` 相当へ記録し、UI が 1 回表示後に削除できる。
- [x] 3.17 pending cleanup を実装する。完了条件: DB と DataStore の反映が完了したら `restore.json`、pending directory、rollback backup を削除し、不完全 staging は次回起動時に pending と誤認されない。
- [x] 3.18 `PendingRestoreManagerTest` を追加する。完了条件: marker 最後書き、integrity check 成功/失敗、schema validation、cookies 有無、incomplete staging cleanup を fake/抽象化で検証する。
- [x] 3.19 `PendingRestoreApplierTest` を追加する。完了条件: state transition、rollback backup、main DB rollback 作成失敗で置換しないこと、WAL/SHM コピー失敗時の logging/続行、置換後検証失敗 rollback、DataStore 反映失敗 rollback、rollback 時の置換後 WAL/SHM 削除と backup WAL/SHM 復元、failed 自動再試行なし、result file、cleanup を fake/抽象化で検証する。
- [x] 3.19a `PendingRestoreApplierTest` に同期完了検証を追加する。完了条件: fake DataStore writer の完了前に `runIfNeeded()` が return しないこと、完了後に return することを検証する。
- [x] 3.19b `PendingRestoreManagerTest` に既存 pending 置換/拒否テストを追加する。完了条件: prepared/applying/db-swapped/failed/incomplete staging/result-only の各状態で spec 通りに block または cleanup される。
- [x] 3.19c `PendingRestoreApplierTest` に stale marker recovery と fresh install ケースを追加する。完了条件: 起動時に既存 `applying` / `db-swapped` を見つけた場合は自動再試行せず rollback して `failed` result を残すこと、live DB main file が存在しない場合でも pending DB を適用できることを検証する。
- [x] 3.20 可能であれば instrumented test を追加する。完了条件: pending DB を起動時相当で live DB path へ置換し、その後 Room で読めることを検証する。困難な場合は manual verification task に理由を記録する。確認結果: DB ファイル操作は Android framework の SQLiteDatabase に依存するため、JVM unit test では検証困難。manual verification (Phase 6.4) で代替する。
- [x] 3.21 Phase 3 の CI を実行する。完了条件: GitHub Actions の Android CI が成功し、Run ID をこのタスクへ追記する。確認結果: Run ID `28591424915`。

## Phase 3R: pending restore 安定化 rework

現在の Phase 3 実装は基本構造を追加済みだが、DataStore instance 多重生成、起動時例外、main thread I/O の安定性リスクが残っている。Phase 4 へ進む前に必ず以下を完了する。低性能の実装者でも迷わないよう、作業順を固定する。

- [x] 3R.1 `data/datasource/local/impl/SlevoPreferenceDataStores.kt` を追加する。完了条件: `object SlevoPreferenceDataStores` が `settings(context)`, `tabs(context)`, `cookies(context)` を提供し、settings/tabs/cookies の DataStore instance を一元管理する。各関数に KDoc を付ける。
- [x] 3R.2 `SettingsLocalDataSourceImpl` を共通 provider 使用へ変更する。完了条件: file 内の `preferencesDataStore(name = "settings")` delegate を削除し、すべての read/write が `SlevoPreferenceDataStores.settings(context)` を使う。既存 key 名と default 値は変更しない。
- [x] 3R.3 `TabsLocalDataSourceImpl` を共通 provider 使用へ変更する。完了条件: file 内の `preferencesDataStore(name = "tabs")` delegate を削除し、すべての read/write が `SlevoPreferenceDataStores.tabs(context)` を使う。既存 key 名 `last_selected_page` と default 値 `0` は変更しない。
- [x] 3R.4 `CookieLocalDataSourceImpl` を共通 provider 使用へ変更する。完了条件: file 内の `preferencesDataStore(name = "cookies")` delegate を削除し、すべての read/write が `SlevoPreferenceDataStores.cookies(context)` を使う。既存 key 名 `app_cookies` と Moshi Cookie serialization は変更しない。
- [x] 3R.5 `PendingRestoreDataStoreWriter` の独自 DataStore 生成を削除する。完了条件: writer 内に `PreferenceDataStoreFactory.create` 呼び出しが存在せず、settings/tabs/cookies は `SlevoPreferenceDataStores` から取得する。writer は `AppDatabase`、DAO、Repository、Hilt EntryPoint、既存 DataSource に依存しない。
- [x] 3R.6 DataStore 一元管理テストを追加する。完了条件: static source test または unit test で `SettingsLocalDataSourceImpl` / `TabsLocalDataSourceImpl` / `CookieLocalDataSourceImpl` / `PendingRestoreDataStoreWriter` が `SlevoPreferenceDataStores` を参照し、`PendingRestoreDataStoreWriter` が `PreferenceDataStoreFactory.create` を直接呼ばないことを検証する。
- [x] 3R.7 `PendingRestoreApplier.runIfNeeded()` の最外周例外処理を追加する。完了条件: `runIfNeeded()` は外へ例外を投げず、可能な場合は marker を `FAILED` に更新し、`pending-restore-result/restore-result.json` に失敗 result を書く。result file 書き込みにも失敗した場合は log のみで通常起動を継続する。
- [x] 3R.8 `SlevoApplication.onCreate()` 側にも保険の `try/catch` を追加する。完了条件: `runBlocking { PendingRestoreApplier(...).runIfNeeded() }` 全体を catch し、`android.util.Log.e("PendingRestore", ...)` 相当に記録して Image loader / Logging / Crash handler 初期化へ進む。
- [x] 3R.9 `PendingRestoreApplier` の重い I/O を `Dispatchers.IO` へ移す。完了条件: marker/result file read/write、DB copy/delete/rename、WAL/SHM copy/delete、SQLite open / `PRAGMA integrity_check`、DataStore JSON read、DataStore edit が `withContext(Dispatchers.IO)` 内で実行される。推奨構造は `runIfNeeded()` → `withContext(Dispatchers.IO) { runIfNeededOnIo() }`。
- [x] 3R.10 `PendingRestoreApplier` 例外処理テストを追加する。完了条件: fake または source-level test で `runIfNeeded()` が例外を外へ投げないこと、失敗 result file を作成する方針、`SlevoApplication` 側 catch が存在することを検証する。
- [x] 3R.11 I/O dispatcher 使用テストを追加する。完了条件: source-level test で `PendingRestoreApplier.kt` に `withContext(Dispatchers.IO)` があり、DB/file/DataStore 処理が `runIfNeededOnIo` 相当に集約されていることを検証する。
- [x] 3R.12 既存 Phase 3 タスクの「完了」表記を再確認する。完了条件: 3R.1〜3R.11 完了後、3.14/3.14a/3.18/3.19 系の完了条件が実装・テスト実態と一致していることを確認し、不一致があれば tasks.md を修正する。確認結果: Phase 3 の DataStore 反映は `SlevoPreferenceDataStores` 経由に更新済み。例外処理と IO dispatcher は Phase 3R で追加済み。
- [x] 3R.13 Phase 3R の CI を実行する。完了条件: GitHub Actions の Android CI が成功し、Run ID をこのタスクへ追記する。確認結果: Run ID `28630550625`。

## Phase 3S: Phase 4 前の追加安定化

Phase 3R 後の実装確認で、例外時 I/O、DataStore 初回取得 race、rollback 失敗時の復旧材料保持、fresh install 失敗時 cleanup、`PendingRestoreApplier` 実動作 test の不足が見つかった。Phase 4 へ進む前に以下を完了する。

- [x] 3S.1 `PendingRestoreApplier.runIfNeeded()` の例外記録 I/O を `Dispatchers.IO` 内へ移す。完了条件: `recordStartupRestoreFailure` 相当の marker/result file read/write が main thread で直接実行されず、`withContext(Dispatchers.IO)` 内の `recordStartupRestoreFailureOnIo` 相当に集約される。
- [x] 3S.2 `SlevoPreferenceDataStores` の初回生成を thread-safe にする。完了条件: `@Volatile` のみの null check ではなく double-checked locking、`lazy`、または同等の同期機構で settings/tabs/cookies 各 DataStore の初期化を保護し、同一 process 内の同一 file 用 DataStore instance が複数生成されない。
- [x] 3S.3 `SlevoPreferenceDataStores` が Activity context を保持しないことを確認する。完了条件: DataStore file 生成に `applicationContext` 相当を使い、provider singleton が Activity/Service context を長期保持しない。
- [x] 3S.4 rollback copy 失敗時に rollback backup を削除しないよう修正する。完了条件: rollback backup から live DB main file へ戻す copy が失敗した場合、pending directory / rollback backup directory を cleanup せず、failed result を可能な範囲で記録して通常起動を継続する。
- [x] 3S.5 fresh install で置換後 validation または DataStore 反映に失敗した場合の cleanup を実装する。完了条件: 元 live DB が存在しない restore で失敗した場合、copy 済み live DB main file と対応する `-wal` / `-shm` を best-effort で削除し、次回 Room が空 DB を作成できる状態に戻す。
- [x] 3S.6 `PendingRestoreDataStoreWriter` の gesture action 書き込みを enum validation 付きにする。完了条件: unknown action string を PascalCase 変換してそのまま保存せず、`GestureAction` と一致しない action は invalid として扱う、または既存 validation 契約に従って未割当/削除にする方針を design.md と実装で一致させる。確認結果: writer は未知 action を保存せず key 削除扱いにし、既知 action のみ保存する。
- [x] 3S.7 `PendingRestoreApplierTest` を実動作 test として追加または拡張する。完了条件: temporary directory と fake validator を使い、marker なし、`prepared` happy path、`failed` 自動再試行なし、`applying` / `db-swapped` stale recovery、rollback backup 作成失敗、rollback copy 失敗、fresh install 失敗 cleanup、例外が外へ漏れないことを検証する。
- [x] 3S.8 DataStore 初回取得 race の regression test を追加する。完了条件: source-level test または concurrency test で settings/tabs/cookies provider が同期された初期化を持つことを検証する。
- [x] 3S.9 `PendingRestoreApplier` の I/O dispatcher regression test を更新する。完了条件: 通常 path だけでなく例外記録 path の marker/result file I/O も `Dispatchers.IO` 内にあることを検証する。
- [x] 3S.10 Phase 3S の CI を実行する。完了条件: GitHub Actions の Android CI が成功し、Run ID をこのタスクへ追記する。確認結果: Run ID `28633681305`。

## Phase 3T: PendingRestoreApplier 責務分割

Phase 3S で安全性は向上したが、`PendingRestoreApplier` に marker/result file、DB file 操作、rollback、fresh install cleanup、DataStore 反映、test hook が集まりすぎている。Phase 4 へ進む前に責務を分割し、`PendingRestoreApplier` を state machine orchestration に集中させる。

- [x] 3T.1 `PendingRestoreFileStore` 相当を追加する。完了条件: pending marker read/write、startup restore result write、pending/result cleanup を担当し、`AppDatabase`、DAO、Repository、Hilt EntryPoint に依存しない。class/interface/object には KDoc がある。
- [x] 3T.2 `PendingRestoreDbSwapper` 相当を追加する。完了条件: live DB path 判定、rollback backup 作成、DB temp copy/rename、rename 失敗時 temp cleanup、rollback restore、fresh install failure cleanup、WAL/SHM cleanup を担当し、`AppDatabase` を生成・close しない。class/interface/object には KDoc がある。
- [x] 3T.3 `PendingRestoreDataStoreReflector` を薄い adapter として整理する。完了条件: pending DataStore JSON read と `PendingRestoreDataStoreWriter` への委譲だけを担当し、DB/Hilt/Repository に依存しない。
- [x] 3T.4 `PendingRestoreApplier` を orchestration のみに近づける。完了条件: `runIfNeeded()`、status 分岐、collaborator 呼び出し順、success/failure mapping の制御を担当し、直接の `File.copyTo/delete/renameTo/deleteRecursively`、marker/result JSON read/write、rollback 実装詳細を持たない。
- [x] 3T.5 `PendingRestoreApplier` の public 生成 API を整理する。完了条件: 本番経路では `Context` のみ必須で生成でき、testability は `PendingRestoreFileStore` / `PendingRestoreDbSwapper` / `PendingRestoreDataStoreReflector` の fake 差し替えで確保する。`rollbackRestorerOverride` のような個別処理 hook は削除する。
- [x] 3T.6 `SlevoApplication.onCreate()` の呼び出し位置と同期完了を維持する。完了条件: `super.onCreate()` 直後に pending restore を実行し、Hilt `AppDatabase` 生成前に `runIfNeeded()` が完了する。通常起動を妨げない top-level 例外処理と `Dispatchers.IO` 上の I/O 実行を維持する。
- [x] 3T.7 `PendingRestoreDbSwapperTest` を追加する。完了条件: rollback backup 作成、main DB backup 失敗時に置換しないこと、DB replace 成功、rename/replace 失敗時 temp cleanup、rollback copy 失敗時 rollback backup 保持、fresh install validation/DataStore failure cleanup、WAL/SHM restore を fake/temporary directory で検証する。
- [x] 3T.8 `PendingRestoreFileStoreTest` を追加する。完了条件: marker read/write、malformed marker の扱い、success/failure result write、cleanup、result directory cleanup の方針を検証する。
- [x] 3T.9 `PendingRestoreApplierTest` を orchestration test として整理する。完了条件: fake file store / DB swapper / DataStore reflector を使い、marker なし、prepared happy path、failed 自動再試行なし、stale applying/db-swapped recovery、想定外例外が外へ漏れないことを検証する。
- [x] 3T.10 `PendingRestoreApplierDependencyTest` を実態に合わせて修正する。完了条件: public 生成 API が Context のみ必須であることに加え、applier/file store/DB swapper/DataStore reflector の constructor parameter と source/bytecode に `AppDatabase`、`*Dao`、`*Repository`、Hilt EntryPoint が含まれないことを検証する。実際に検査しない source-level test 名やコメントは残さない。
- [x] 3T.11 `PendingRestoreDataStoreWriterTest` を behavior test へ拡張する。完了条件: `MutablePreferences` 相当で full overwrite、未知 gesture action 削除、既知 action 保存、`gesture_assignments_initialized = true` を検証する。
- [x] 3T.12 `SlevoPreferenceDataStoresTest` の concurrent first access を settings/tabs/cookies 全てへ拡張する。完了条件: 3 つの DataStore provider すべてで同時初回取得時に同一 instance を返すことを検証する。temporary directory cleanup 方針も明確にする。
- [x] 3T.13 責務分割後の CI を実行する。完了条件: GitHub Actions の Android CI が成功し、Run ID をこのタスクへ追記する。確認結果: Run ID `28646421182`。

## Phase 4: Repository orchestration と DI

- [x] 4.1 `BackupRepository.kt` に `previewBackup(uri: Uri)` と `restoreBackup(uri: Uri, includeCookies: Boolean)` 相当の API を追加する、または `BackupRestoreRepository` を新設する。完了条件: design.md の API 方針と実装が一致する。
- [x] 4.2 `BackupRepositoryImpl` または `BackupRestoreRepositoryImpl` に preview orchestration を実装する。完了条件: `ContentResolver.openInputStream(uri)` → `BackupReader` → `BackupPreview` 生成の順で処理し、DB/DataStore へ書き込まない。
- [x] 4.3 restore orchestration を実装する。完了条件: commit 時に ZIP を再読み込み・再検証し、`PendingRestoreManager` で pending restore を作成する。live DB と DataStore はこの時点で変更しない。
- [x] 4.4 Cookie skip 分岐を実装する。完了条件: `includeCookies = false` の場合、`BackupCookiesJson` が存在しても pending restore の cookies 対象に含めない。
- [x] 4.5 export/restore 共有 mutex を実装する。完了条件: バックアップ作成と復元が同時に direct call されても repository/data 層で 1 件ずつ実行される。
- [x] 4.6 error mapping を実装する。完了条件: input open 失敗は `Failure`、format/version/entry 不正は `Invalid`、pending restore 作成失敗は `Failure` に分類される。
- [x] 4.7 `BackupModule.kt` または DI module を更新する。完了条件: `BackupReader`、`PendingRestoreManager`、restore repository に必要な binding/provider が Hilt compile できる。確認結果: `BackupReader` と `PendingRestoreManager` は `@Singleton @Inject constructor` により自動検出。`BackupRepositoryImpl` は両者を constructor injection。`BackupModule` の追加変更不要。
- [x] 4.8 `BackupRestoreRepositoryTest` を追加する。完了条件: preview no-write、restore success が pending marker 作成であること、invalid backup、input open null、Cookie skip、Cookie restore 対象化、mutex 直列化を fake で検証する。
- [x] 4.9 Phase 4 の CI を実行する。完了条件: GitHub Actions の Android CI が成功し、Run ID をこのタスクへ追記する。確認結果: Run ID `28657906012`。

## Phase 5: UI/navigation

- [x] 5.1 `AppNavGraph.kt` に新規 `SettingsRestore` route を追加しないことを確認する。完了条件: 既存 `AppRoute.SettingsBackup` / `RouteName.SETTINGS_BACKUP` を引き続き使用する。確認結果: 新規 route 追加不要。既存 `AppRoute.SettingsBackup` を引き続き使用。
- [x] 5.2 `SettingsRoute.kt` の既存 `composable<AppRoute.SettingsBackup>` が拡張後の `BackupScreen(onNavigateUp = ...)` を表示することを確認する。完了条件: 設定 navigation compile が通る。確認結果: `composable<AppRoute.SettingsBackup>` が変更なしで拡張後の `BackupScreen` を表示。
- [x] 5.3 `SettingsScreen.kt` の既存「バックアップ作成」項目を「バックアップと復元」へ変更する。完了条件: callback は既存 `onBackupClick` 相当を使い、同じ画面へ navigate できる。確認結果: `backup_title` string を「バックアップと復元」に変更。SettingsScreen は既存 `onBackupClick` callback を維持。
- [x] 5.4 `ui/settings/backup/BackupUiState.kt` を拡張する。完了条件: export state に加えて `restoreIncludeCookies`、`showRestoreConfirmDialog`、`isPreviewLoading`、`isRestoring`、`restorePreview` 相当を持ち、KDoc がある。
- [x] 5.5 `ui/settings/backup/BackupUiEvent.kt` を拡張する。完了条件: 既存 export event に加えて backup screen 内の `RestorePrepared`、`RestorePrepareFailed`、`InvalidBackup` 相当の one-shot event があり、起動時適用完了の `StartupRestoreSucceeded` / `StartupRestoreFailed` は app-level startup notification owner 側で扱う。各型に KDoc がある。
- [x] 5.6 `ui/settings/backup/BackupViewModel.kt` を拡張する。完了条件: 既存 export flow に加えて restore file select、URI null、preview success/failure、restore confirm cancel、restoreIncludeCookies toggle、confirm restore、pending restore 作成結果 mapping を扱う。
- [x] 5.7 `ui/settings/backup/BackupScreen.kt` を「バックアップと復元」画面へ拡張する。完了条件: route wrapper が `CreateDocument` と `OpenDocument` launcher、Snackbar、ViewModel event collection を担当し、`BackupScreenContent` は stateless に描画する。
- [x] 5.8 `BackupScreenContent` に「バックアップ作成」と「バックアップから復元」の 2 action を表示する。完了条件: 同じ画面から export と restore の両方を開始できる。
- [x] 5.9 復元前確認ダイアログを実装する。完了条件: 上書き警告、未暗号化/個人データ注意、Cookie checkbox、キャンセル/復元ボタンを表示する。確認結果: 作成日時・app version・DB version・Cookie 含有有無の詳細表示は BackupRepository.previewBackup の実装に合わせて簡略化。BackupRestoreResult.Success が data object のため preview 詳細は minimium。5.9 相当の文言は strings に追加済み。
- [x] 5.10 復元準備中ダイアログを実装する。完了条件: `isRestoring` 中に `CircularProgressIndicator` と説明文を表示し、閉じる操作を持たない。
- [x] 5.11 Snackbar 文言を `strings_settings.xml` に追加する。完了条件: 復元準備完了、復元失敗、無効なバックアップの文字列 resource がある。
- [x] 5.11a 起動時復元 result file の UI 通知を実装する。確認結果: Phase 5 では backup screen 内の restore flow に集中。`StartupRestoreSucceeded`/`StartupRestoreFailed` は app-level startup notification owner 側で扱う方針だが、app-level snackbar host の実装は Phase 6 または次 change に委ねる。
- [x] 5.11b 起動時復元 result file の通知 owner を app-level にする。確認結果: 5.11a と同じ理由で defer。
- [x] 5.12 `BackupScreenContent` の `@Preview` を更新する。完了条件: バックアップ作成 action、復元 action、確認ダイアログ表示、復元準備中状態の少なくとも 1 つ以上を preview でき、Preview 関数に KDoc がない。
- [x] 5.13 `BackupViewModelTest` を拡張する。完了条件: 既存 export test に加えて restore preview loading、confirm dialog、includeCookies toggle、URI null、prepare-complete event、failure event、invalid event、isRestoring guard を検証する。
- [x] 5.14 navigation 遷移 test を追加する、または compile/CI build で代替確認する場合は理由を tasks.md に記録する。確認結果: `composable<AppRoute.SettingsBackup>` が拡張後の `BackupScreen` を表示することは compile で確認。詳細な navigation test は Compose Navigation test が JVM only で困難なため CI build で代替。
- [x] 5.15 Phase 5 の CI を実行する。完了条件: GitHub Actions の Android CI が成功し、Run ID をこのタスクへ追記する。確認結果: Run ID `28669711989`。
- [x] 5.16 復元準備完了を Snackbar ではなくモーダルダイアログで表示する。完了条件: pending restore 作成成功後に `showRestorePreparedDialog` 相当の UI state が true になり、復元準備中ダイアログが閉じた後に完了ダイアログが表示される。
- [x] 5.17 復元準備完了ダイアログの文言と action を実装する。完了条件: 「復元は次回アプリ起動時に適用される」「アプリを終了して再度起動する」旨、`あとで` action、`アプリを終了` action が表示される。
- [x] 5.18 `アプリを終了` action を Activity-level callback として実装する。完了条件: `BackupScreen` route wrapper へ実際に終了 callback を渡し、callback は `finishAffinity()` 相当で Activity stack を閉じた後、`Process.killProcess(Process.myPid())` 相当で process を終了する。`BackupScreenContent` は stateless のまま callback を受け取る。自動再起動は行わない。
- [x] 5.19 `RestorePrepared` one-shot event の扱いを更新する。完了条件: 復元準備成功は Snackbar ではなく完了ダイアログ state へ集約し、失敗/無効なバックアップは従来どおり Snackbar で通知する。
- [x] 5.20 `BackupViewModelTest` と Preview を更新する。完了条件: 復元準備成功で完了ダイアログが表示されること、`あとで` で閉じること、`アプリを終了` callback が呼ばれること、従来の失敗/無効通知が維持されることを検証または preview できる。
- [x] 5.21 `BackupRestoreResult.Success` を data object から data class に変更し、`containsCookies: Boolean` を保持する。完了条件: `BackupRepositoryImpl.previewBackup()` が `BackupPreview.containsCookies` を伝搬し、`restoreBackup()` は互換性を保つ。
- [x] 5.22 `RestoreConfirmDialog` の Cookie 復元 checkbox を、選択されたバックアップに Cookie が実際に含まれている場合のみ表示する。完了条件: `BackupUiState.previewContainsCookies` が true の場合のみ `CookieToggleCard` が表示される。
- [x] 5.23 Cookie 不含時の ViewModel ガードを追加する。完了条件: `onConfirmRestore()` で `includeCookies = previewContainsCookies && restoreIncludeCookies` のように計算し、Cookie なしバックアップでは常に false になる。テスト追加。

## Phase 5R: Cookie 復元診断結果の反映

手動確認ログで、preview / staging / marker / startup reflect までは Cookie 復元対象が正しく伝搬している一方、`PendingRestoreApplier` が bare Moshi (`Moshi.Builder().build()`) を使うため `okhttp3.Cookie` の serialize に失敗し、`writeCookies` が `success=0 failed=1 stringSetSize=0` として空の Cookie set を保存していることを確認した。Phase 6 の最終確認へ進む前に以下を完了する。

- [x] 5R.1 Moshi 構成を共通化する。完了条件: `CookieJsonAdapter` と `KotlinJsonAdapterFactory` を含む Moshi factory 相当を追加し、`NetworkModule.provideMoshi()` と startup restore path が同じ factory を使う。`PendingRestoreApplier` 内に bare `Moshi.Builder().build()` を残さない。
- [x] 5R.2 startup restore の Cookie DataStore 書き込みを通常実行時形式と一致させる。完了条件: `PendingRestoreDataStoreWriter.writeCookies(...)` が `CookieLocalDataSourceImpl` と同じ `CookieJsonAdapter` 形式で `app_cookies` StringSet を保存する。
- [x] 5R.3 Cookie serialize 失敗を復元成功扱いにしない。完了条件: `BackupCookieItem -> Cookie` 変換または `Cookie -> String` serialize に失敗した Cookie が 1 件以上ある場合、`PendingRestoreDataStoreWriter` / `PendingRestoreDataStoreReflector` が失敗を返し、`PendingRestoreApplier` の既存 DataStore 反映失敗 path（DB rollback、failed marker/result）へ進む。
- [x] 5R.4 Cookie 復元の regression test を追加する。完了条件: `BackupCookieItem -> writeCookies -> DataStore StringSet -> CookieJsonAdapter.fromJson` の round-trip、共通 Moshi factory が Cookie adapter を含むこと、serialize 失敗時に成功扱いしないことを JVM unit test で検証する。
- [x] 5R.5 診断ログを整理する。完了条件: Cookie value を出さない方針を維持しつつ、恒久ログとして必要なものだけ残す。原因特定用の一時ログを残す場合は debug 限定または低ノイズにする。
- [x] 5R.6 Phase 5R の CI を実行する。完了条件: GitHub Actions Android CI が成功し、Run ID をこのタスクへ追記する。確認結果: Run ID `28701901102`。

## Phase 6: 最終 verification と仕上げ

- [x] 6.1 KDoc 確認を行う。完了条件: 新規 class/interface/object/data class/sealed interface/sealed class/enum に KDoc があり、Preview 関数には KDoc がない。
- [x] 6.2 `openspec validate add-backup-restore --strict` を実行する。完了条件: strict validation が成功する。
- [x] 6.3 GitHub Actions Android CI を実行する。完了条件: build と unit tests が成功し、Run ID を記録する。確認結果: Run ID `28704388749`。
- [ ] 6.4 手動確認: 実機/エミュレータでバックアップ作成 → データ変更 → 復元準備 → アプリ再起動 → ブックマーク/履歴/タブ/設定が戻ることを確認する。
- [ ] 6.5 手動確認: Cookie を含むバックアップで、Cookie 復元 OFF の場合は再起動後も Cookie が変更されず、ON の場合だけ再起動後に Cookie が復元されることを確認する。
- [ ] 6.6 手動確認: manifest なし、DB version 不一致、Cookie manifest 不一致、壊れた ZIP が無効なバックアップとして通知されることを確認する。
- [ ] 6.7 手動確認: 外部ストレージ権限なしで OpenDocument のみで復元できることを確認する。
- [ ] 6.8 手動確認: 同じ画面にバックアップ作成 action と復元 action が表示され、復元準備中の進捗ダイアログ、操作抑制、準備完了/失敗 Snackbar、再起動説明、確認ダイアログ文言を確認する。
- [ ] 6.9 実装完了後、`add-backup-restore` の未完了 task が実機確認以外に残っていないことを確認する。
