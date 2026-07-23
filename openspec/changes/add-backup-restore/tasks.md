## 実装方針

`add-backup-restore` は ZIP 読み込み、manifest/DB 検証、Room DB 置換、DataStore 反映、Repository orchestration、UI/navigation を含む。1 回で全対象を変更せず、Phase ごとに小さく実装・検証・コミットする。

- Phase 0: prerequisite と復元固有リスクの再確認を先に完了する。
- Phase 1: ZIP reader、manifest validator、preview model を実装する。
- Phase 2: DataStore JSON の逆変換と反映 API を実装する。
- Phase 3: pending restore core と、次回起動時の `AppDatabase` 生成前適用を実装する。
- Phase 4: Repository orchestration と DI を実装する。
- Phase 5: UI/navigation を実装する。
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

- [ ] 3.1 `data/backup/PendingRestoreManager.kt` を追加する。完了条件: 検証済み DB/settings/tabs/cookies を `filesDir/pending-restore/` 相当へ保存し、最後に `restore.json` marker を作成できる。
- [ ] 3.2 `data/backup/PendingRestoreMarker.kt` を追加する。完了条件: `status`、`createdAt`、`includeCookies`、`databaseVersion`、失敗理由相当を保持し、Moshi で encode/decode できる。
- [ ] 3.2a pending marker state machine を実装する。完了条件: `prepared -> applying -> db-swapped -> none/failed` の遷移、成功時 marker 削除、成功/失敗は result file に記録、`failed` 自動再試行なし、未知 status の failure 扱いをテストできる。
- [ ] 3.2b 既存 pending/rollback/result の扱いを実装する。完了条件: `prepared` は新規準備を拒否、`applying`/`db-swapped` は recovery 優先で新規準備を拒否、`failed` は cleanup 成功時のみ新規準備可、marker なし不完全 staging は cleanup、result file のみ存在時は app-level 通知または削除後に新規準備可、をテストできる。
- [ ] 3.3 `PendingRestoreManager` に復元対象 DB の integrity check を実装する。完了条件: pending marker 作成前に staging DB を読み取り専用で開き、`PRAGMA integrity_check` が `ok` の場合のみ marker を作成する。
- [ ] 3.4 integrity check 失敗時の安全停止を実装する。完了条件: staging DB が壊れている場合、pending marker を作成せず `Invalid` または manager exception を返す。
- [ ] 3.5 `data/backup/PendingRestoreApplier.kt` を追加する。完了条件: constructor は原則 `Context` のみを必須引数とし、`AppDatabase`、DAO、Repository、DB 依存 DataSource、Hilt EntryPoint に依存せず、起動時に pending marker を読み取って DB file 差し替えと DataStore JSON 反映を実行できる API がある。
- [ ] 3.6 `PendingRestoreApplier` を `SlevoApplication.onCreate()` の `super.onCreate()` 直後に手動生成して呼び出す。完了条件: Hilt 経由で取得せず、`MainActivity.onCreate()`、`androidx.startup.Initializer`、WorkManager から呼び出さない。`runIfNeeded()` は必要な DB 置換判断と pending 適用を同期的に完了してから return し、未完了の非同期 job を起動したまま通常初期化へ進まない。
- [ ] 3.7 `PendingRestoreApplier` の依存制約テストを追加する。完了条件: reflection または静的検査で constructor parameter と推移的に使用する主要 dependency に `AppDatabase`、`*Dao`、`*Repository`、DB 依存 DataSource が含まれないことを検証する。
- [ ] 3.8 `PendingRestoreApplier.runIfNeeded()` の呼び出し位置テストを追加する。完了条件: `SlevoApplication.kt` 内の `runIfNeeded()` 呼び出しが 1 箇所のみで、`super.onCreate()` 直後、Coil/Logger/crash handler 初期化より前であることを検証する。
- [ ] 3.9 `SlevoApplication` 起動時に `AppDatabase` が未生成であることを検証する。完了条件: Robolectric または Hilt test で `SlevoApplication.onCreate()` 実行中に `DatabaseModule.provideAppDatabase()` / `Room.databaseBuilder` / `DatabaseCallback` が呼ばれないことを確認する。
- [ ] 3.10 起動時 DB 置換を実装する。完了条件: live `AppDatabase` を close せず、pending DB を live DB directory の temp file へ copy してから replace/rename し、失敗時は marker を `failed` に更新する。
- [ ] 3.11 rollback backup 作成を実装する。完了条件: live DB main file と `-wal` / `-shm` を DB 置換前に `filesDir/pending-restore/rollback/` 相当へ保存する。live DB main file が存在するのに main file backup を作成できない場合、または rollback directory を作成できない場合は marker を `failed` にして置換しない。`-wal` / `-shm` のコピー失敗は詳細ログへ記録し、main DB rollback がある場合は続行可能とする。
- [ ] 3.11a fresh install / missing rollback source を実装する。完了条件: live DB main file が存在しない場合は rollback source なしとして扱い、`-wal` / `-shm` 不在も正常として扱い、DB 不在だけで復元適用を失敗にしない。
- [ ] 3.12 live DB の `-wal` / `-shm` cleanup を実装する。完了条件: live DB path の sibling file を best-effort delete し、失敗はログに記録するが cleanup 失敗だけで不必要に crash しない。
- [ ] 3.13 置換後検証と rollback を実装する。完了条件: 置換後 DB の `PRAGMA integrity_check` または schema validation 失敗時、rollback backup から live DB を復旧し、marker を `failed` に更新する。rollback 時は置換後に生成された可能性がある live DB の `-wal` / `-shm` を削除し、rollback backup に存在する main DB / `-wal` / `-shm` のみ戻す。
- [ ] 3.14 起動時 DataStore 反映を実装する。完了条件: pending settings/tabs/cookies JSON を Hilt 経由 DataSource ではなく DB 非依存の DataStore writer で反映し、`includeCookies = false` の場合は cookies を反映しない。
- [ ] 3.14a 起動時 DataStore 反映の完了待ちを実装する。完了条件: DataStore 書き込みを開始したまま `runIfNeeded()` が return せず、すべての反映完了または失敗 marker/result 記録まで同期的に待つ。
- [ ] 3.15 DataStore 反映失敗時 rollback を実装する。完了条件: DB 置換後に DataStore 反映が失敗した場合、rollback backup から live DB を復旧し、marker/result を `failed` にする。DataStore 完全 rollback は保証しないことをログ/result に記録する。
- [ ] 3.16 起動時復元 result file を実装する。完了条件: 成功/失敗を `filesDir/pending-restore-result/restore-result.json` 相当へ記録し、UI が 1 回表示後に削除できる。
- [ ] 3.17 pending cleanup を実装する。完了条件: DB と DataStore の反映が完了したら `restore.json`、pending directory、rollback backup を削除し、不完全 staging は次回起動時に pending と誤認されない。
- [ ] 3.18 `PendingRestoreManagerTest` を追加する。完了条件: marker 最後書き、integrity check 成功/失敗、schema validation、cookies 有無、incomplete staging cleanup を fake/抽象化で検証する。
- [ ] 3.19 `PendingRestoreApplierTest` を追加する。完了条件: state transition、rollback backup、main DB rollback 作成失敗で置換しないこと、WAL/SHM コピー失敗時の logging/続行、置換後検証失敗 rollback、DataStore 反映失敗 rollback、rollback 時の置換後 WAL/SHM 削除と backup WAL/SHM 復元、failed 自動再試行なし、result file、cleanup を fake/抽象化で検証する。
- [ ] 3.19a `PendingRestoreApplierTest` に同期完了検証を追加する。完了条件: fake DataStore writer の完了前に `runIfNeeded()` が return しないこと、完了後に return することを検証する。
- [ ] 3.19b `PendingRestoreManagerTest` に既存 pending 置換/拒否テストを追加する。完了条件: prepared/applying/db-swapped/failed/incomplete staging/result-only の各状態で spec 通りに block または cleanup される。
- [ ] 3.19c `PendingRestoreApplierTest` に stale marker recovery と fresh install ケースを追加する。完了条件: 起動時に既存 `applying` / `db-swapped` を見つけた場合は自動再試行せず rollback して `failed` result を残すこと、live DB main file が存在しない場合でも pending DB を適用できることを検証する。
- [ ] 3.20 可能であれば instrumented test を追加する。完了条件: pending DB を起動時相当で live DB path へ置換し、その後 Room で読めることを検証する。困難な場合は manual verification task に理由を記録する。
- [ ] 3.21 Phase 3 の CI を実行する。完了条件: GitHub Actions の Android CI が成功し、Run ID をこのタスクへ追記する。

## Phase 4: Repository orchestration と DI

- [ ] 4.1 `BackupRepository.kt` に `previewBackup(uri: Uri)` と `restoreBackup(uri: Uri, includeCookies: Boolean)` 相当の API を追加する、または `BackupRestoreRepository` を新設する。完了条件: design.md の API 方針と実装が一致する。
- [ ] 4.2 `BackupRepositoryImpl` または `BackupRestoreRepositoryImpl` に preview orchestration を実装する。完了条件: `ContentResolver.openInputStream(uri)` → `BackupReader` → `BackupPreview` 生成の順で処理し、DB/DataStore へ書き込まない。
- [ ] 4.3 restore orchestration を実装する。完了条件: commit 時に ZIP を再読み込み・再検証し、`PendingRestoreManager` で pending restore を作成する。live DB と DataStore はこの時点で変更しない。
- [ ] 4.4 Cookie skip 分岐を実装する。完了条件: `includeCookies = false` の場合、`BackupCookiesJson` が存在しても pending restore の cookies 対象に含めない。
- [ ] 4.5 export/restore 共有 mutex を実装する。完了条件: バックアップ作成と復元が同時に direct call されても repository/data 層で 1 件ずつ実行される。
- [ ] 4.6 error mapping を実装する。完了条件: input open 失敗は `Failure`、format/version/entry 不正は `Invalid`、pending restore 作成失敗は `Failure` に分類される。
- [ ] 4.7 `BackupModule.kt` または DI module を更新する。完了条件: `BackupReader`、`PendingRestoreManager`、restore repository に必要な binding/provider が Hilt compile できる。
- [ ] 4.8 `BackupRestoreRepositoryTest` を追加する。完了条件: preview no-write、restore success が pending marker 作成であること、invalid backup、input open null、Cookie skip、Cookie restore 対象化、mutex 直列化を fake で検証する。
- [ ] 4.9 Phase 4 の CI を実行する。完了条件: GitHub Actions の Android CI が成功し、Run ID をこのタスクへ追記する。

## Phase 5: UI/navigation

- [ ] 5.1 `AppNavGraph.kt` に新規 `SettingsRestore` route を追加しないことを確認する。完了条件: 既存 `AppRoute.SettingsBackup` / `RouteName.SETTINGS_BACKUP` を引き続き使用する。
- [ ] 5.2 `SettingsRoute.kt` の既存 `composable<AppRoute.SettingsBackup>` が拡張後の `BackupScreen(onNavigateUp = ...)` を表示することを確認する。完了条件: 設定 navigation compile が通る。
- [ ] 5.3 `SettingsScreen.kt` の既存「バックアップ作成」項目を「バックアップと復元」へ変更する。完了条件: callback は既存 `onBackupClick` 相当を使い、同じ画面へ navigate できる。
- [ ] 5.4 `ui/settings/backup/BackupUiState.kt` を拡張する。完了条件: export state に加えて `restoreIncludeCookies`、`showRestoreConfirmDialog`、`isPreviewLoading`、`isRestoring`、`restorePreview` 相当を持ち、KDoc がある。
- [ ] 5.5 `ui/settings/backup/BackupUiEvent.kt` を拡張する。完了条件: 既存 export event に加えて backup screen 内の `RestorePrepared`、`RestorePrepareFailed`、`InvalidBackup` 相当の one-shot event があり、起動時適用完了の `StartupRestoreSucceeded` / `StartupRestoreFailed` は app-level startup notification owner 側で扱う。各型に KDoc がある。
- [ ] 5.6 `ui/settings/backup/BackupViewModel.kt` を拡張する。完了条件: 既存 export flow に加えて restore file select、URI null、preview success/failure、restore confirm cancel、restoreIncludeCookies toggle、confirm restore、pending restore 作成結果 mapping を扱う。
- [ ] 5.7 `ui/settings/backup/BackupScreen.kt` を「バックアップと復元」画面へ拡張する。完了条件: route wrapper が `CreateDocument` と `OpenDocument` launcher、Snackbar、ViewModel event collection を担当し、`BackupScreenContent` は stateless に描画する。
- [ ] 5.8 `BackupScreenContent` に「バックアップ作成」と「バックアップから復元」の 2 action を表示する。完了条件: 同じ画面から export と restore の両方を開始できる。
- [ ] 5.9 復元前確認ダイアログを実装する。完了条件: 作成日時、app version、DB version、Cookie 含有有無、上書き警告、未暗号化/個人データ注意、Cookie checkbox、キャンセル/復元ボタンを表示する。
- [ ] 5.10 復元準備中ダイアログを実装する。完了条件: `isRestoring` 中に `CircularProgressIndicator` と説明文を表示し、閉じる操作を持たない。
- [ ] 5.11 Snackbar 文言を `strings_settings.xml` に追加する。完了条件: 復元準備完了、復元失敗、無効なバックアップ、preview 失敗、再起動後に適用される説明の文字列 resource がある。
- [ ] 5.11a 起動時復元 result file の UI 通知を実装する。完了条件: app-level startup notification owner が result file を読み取り、成功/失敗を 1 回だけ Snackbar で表示し、表示後に result file を削除する。
- [ ] 5.11b 起動時復元 result file の通知 owner を app-level にする。完了条件: バックアップ画面を開かなくても、root-level snackbar host または app-level startup notification owner が result file を読み取り、成功/失敗を 1 回だけ表示して削除する。
- [ ] 5.12 `BackupScreenContent` の `@Preview` を更新する。完了条件: バックアップ作成 action、復元 action、確認ダイアログ表示、復元準備中状態の少なくとも 1 つ以上を preview でき、Preview 関数に KDoc がない。
- [ ] 5.13 `BackupViewModelTest` を拡張する。完了条件: 既存 export test に加えて restore preview loading、confirm dialog、includeCookies toggle、URI null、prepare-complete event、failure event、invalid event、isRestoring guard を検証する。
- [ ] 5.14 navigation 遷移 test を追加する、または compile/CI build で代替確認する場合は理由を tasks.md に記録する。
- [ ] 5.15 Phase 5 の CI を実行する。完了条件: GitHub Actions の Android CI が成功し、Run ID をこのタスクへ追記する。

## Phase 6: 最終 verification と仕上げ

- [ ] 6.1 KDoc 確認を行う。完了条件: 新規 class/interface/object/data class/sealed interface/sealed class/enum に KDoc があり、Preview 関数には KDoc がない。
- [ ] 6.2 `openspec validate add-backup-restore --strict` を実行する。完了条件: strict validation が成功する。
- [ ] 6.3 GitHub Actions Android CI を実行する。完了条件: build と unit tests が成功し、Run ID を記録する。
- [ ] 6.4 手動確認: 実機/エミュレータでバックアップ作成 → データ変更 → 復元準備 → アプリ再起動 → ブックマーク/履歴/タブ/設定が戻ることを確認する。
- [ ] 6.5 手動確認: Cookie を含むバックアップで、Cookie 復元 OFF の場合は再起動後も Cookie が変更されず、ON の場合だけ再起動後に Cookie が復元されることを確認する。
- [ ] 6.6 手動確認: manifest なし、DB version 不一致、Cookie manifest 不一致、壊れた ZIP が無効なバックアップとして通知されることを確認する。
- [ ] 6.7 手動確認: 外部ストレージ権限なしで OpenDocument のみで復元できることを確認する。
- [ ] 6.8 手動確認: 同じ画面にバックアップ作成 action と復元 action が表示され、復元準備中の進捗ダイアログ、操作抑制、準備完了/失敗 Snackbar、再起動説明、確認ダイアログ文言を確認する。
- [ ] 6.9 実装完了後、`add-backup-restore` の未完了 task が実機確認以外に残っていないことを確認する。
