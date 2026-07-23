## 1. 事前調査と migration contract

- [x] 1.1 `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/AppDatabase.kt` の現在 version、既存 `Migration` 定義、Room identity hash、`exportSchema` を確認する。完了条件: 現在 version、対応可能な最小 version、連続 migration edge を作業メモまたは PR 説明に記録する。ここで `MINIMUM_RESTORABLE_DATABASE_VERSION` を確定し、確定するまで 2.x 以降へ進まない。
- [x] 1.2 `app/src/main/java/com/websarva/wings/android/slevo/di/DatabaseModule.kt` の `provideAppDatabase()` が登録している migrations を確認する。完了条件: `AppDatabase` 側の migration 定義と Room 登録の差分がないことを確認する。
- [x] 1.3 `AppDatabase.Companion` または既存 migration 定義の近くに、backup restore 用の `MINIMUM_RESTORABLE_DATABASE_VERSION` と migration path helper を追加する。完了条件: 任意の `fromVersion` から現在 version まで path があるか判定できる。
- [x] 1.4 `DatabaseModule.provideAppDatabase()` が migration path helper と同じ migration 定義を参照して `.addMigrations(...)` するよう整理する。完了条件: migration edge の二重管理が増えず、既存 DB build が通る。
- [x] 1.5 migration chain 一貫性テストを追加する。完了条件: 対応最小 DB version から現在 version まで edge が連続し、Room 登録対象と path 判定対象が一致することを unit test で検証する。

## 2. manifest と backup reader の version validation

- [x] 2.1 `BackupReader.kt` の `validateManifest()` を確認し、`databaseVersion == currentDbVersion` の完全一致前提を範囲 + migration path validation に置き換える。完了条件: current version と supported old version は preview/restore 候補になり、future/too-old/path-missing は invalid になる。
- [x] 2.2 `BackupPreview` と preview result mapping を確認し、既存 UI 表示を変えずに supported old version を preview 成功として扱えるようにする。完了条件: `BackupUiState` の新規表示項目や確認ダイアログ文言を追加せず、古い DB backup が既存 preview flow に乗る。
- [x] 2.3 `BackupReaderTest` または既存 restore reader tests に current version、supported old version、future version、too-old version、migration path missing version のケースを追加する。完了条件: 期待する valid/invalid 結果が unit test で固定される。
- [x] 2.4 `manifest.databaseVersion` と DB file の `PRAGMA user_version` 不一致を検出するテストを追加する。完了条件: manifest と DB 本体の version が異なる ZIP は pending restore を作成しない。
- [x] 2.5 DB file 側の `PRAGMA user_version` が future、too-old、または migration path missing のケースを追加する。完了条件: manifest だけでなく DB file 本体の version 異常でも pending restore を作成しない。

## 3. DB validation の pre/post 分離

- [x] 3.1 `BackupDatabaseValidator.kt` の既存 validator API と call site を洗い出す。完了条件: `BackupReader`、`PendingRestoreManager`、`PendingRestoreApplier`、post-migration completion で必要な validation mode を一覧化する。
- [x] 3.2 pre-migration validation API を追加する。完了条件: 読み取り専用 open、`PRAGMA integrity_check = ok`、`PRAGMA user_version` 範囲、manifest databaseVersion との一致、migration path を検証する。
- [x] 3.3 pre-migration validation では現在 Room identity hash と現在 schema の必須 table 一致を要求しないようにする。完了条件: 古い version の DB が hash/table mismatch だけで拒否されない test が通る。
- [x] 3.4 post-migration validation API を追加または既存 strict validation として明確化する。完了条件: `PRAGMA user_version == current`、現在 identity hash、現在必須 table、SQLite integrity を確認できる。
- [x] 3.5 `BackupReader` と `PendingRestoreManager` の DB validation call site を pre-migration validation に切り替える。完了条件: supported old DB backup が staging まで進める。
- [x] 3.6 `PendingRestoreApplier` の DB swap 直後 validation を pre-migration validation に切り替える。完了条件: Room open 前の古い DB が current identity hash 不一致で rollback されない。
- [x] 3.7 corrupt DB、future user_version、too-old user_version、manifest/user_version mismatch、current DB strict success の validator unit tests を追加する。完了条件: pre/post validation の境界が test 名で明確になる。
- [x] 3.8 historical schema ごとの Room identity hash/table list validation を pre-migration validation に追加していないことを確認する。完了条件: historical schema sanity はこの change の non-goal であり、migration path と Room migration 結果に委ねることが test またはコメントで明確になる。

## 4. pending marker state と recovery 拡張

- [x] 4.1 `PendingRestoreMarker.kt` の marker status enum/data class を確認し、`migration-pending`、`rollback-required`、`completed` 相当の状態を追加する。完了条件: Moshi serialization/deserialization と既存 marker tests が通る。
- [x] 4.2 `PendingRestoreApplier.kt` の state machine を更新し、DB swap + DataStore 反映成功後に pending directory を cleanup せず、current/old version を問わず `migration-pending` へ遷移させる。完了条件: rollback backup と marker が Room open 後の completion checker 成功まで残る。
- [x] 4.3 `PendingRestoreApplier` の stale marker recovery に `migration-pending` を追加する。完了条件: 次回 cold start 時に migration-pending が残っている場合、rollback 前に live DB strict validation を実行し、成功なら completed cleanup、失敗かつ rollback backup があれば live DB を戻して failed result を記録する。
- [x] 4.4 `PendingRestoreApplier` の recovery に `rollback-required` を追加する。完了条件: 次回 cold start 時に rollback-required が残っている場合、Room open 前に rollback backup から live DB を戻して failed result を記録する。
- [x] 4.5 `PendingRestoreApplier` の recovery に `completed` を追加する。完了条件: completed marker が残っている場合は rollback せず、success result 書き込みまたは cleanup を再試行し、完了後に marker を削除する。
- [x] 4.6 rollback backup がない migration-pending / rollback-required の recovery を実装する。完了条件: live DB strict validation が失敗し rollback backup もない場合、invalid live DB main file、`-wal`、`-shm` を quarantine して fresh DB 起動を優先し、failed result を残す。
- [x] 4.7 `PendingRestoreManager.prepareRestore()` または既存 pending 検出経路で `migration-pending` / `rollback-required` / `completed` がある場合の新規復元準備拒否を実装する。完了条件: 既存 marker/rollback/pending files を保持したまま新しい pending restore を作成しない。
- [x] 4.8 terminal failed marker の新規復元準備処理を実装する。完了条件: failed result を保持したまま active failed marker/staging/rollback を cleanup できた場合のみ新しい restore を prepared にできる。
- [x] 4.9 `PendingRestoreApplierTest` に migration-pending 遷移、current version でも migration-pending を通るケース、migration-pending stale strict-validation success、migration-pending stale strict-validation success + rollback backup missing、migration-pending stale rollback、rollback-required rollback、completed cleanup retry、rollback backup missing、failed marker cleanup のケースを追加する。完了条件: state transition と cleanup/保持対象が test で確認できる。
- [x] 4.10 `PendingRestoreManagerTest` または repository restore tests に migration-pending / rollback-required / completed 中の新規復元準備拒否と failed marker cleanup 後の新規復元準備許可を追加する。完了条件: spec の pending 中拒否と terminal failed 後再準備が test で固定される。
- [x] 4.11 `PendingRestoreFileStore.kt` / `PendingRestoreDbSwapper.kt` の cleanup API が migration-pending / rollback-required / completed で rollback を誤って消さないことを確認し、必要なら API を分ける。完了条件: success cleanup と failed/recovery cleanup の責務が明確になる。
- [x] 4.12 rollback backup/restore の file-set と ordering を確認する。完了条件: rollback 対象は main DB、`-wal`、`-shm` であり、cold-start rollback では live 側 `-wal` / `-shm` 削除 → main DB 復元 → backup 済み WAL/SHM 復元の順に処理される。
- [x] 4.13 quarantine failure handling を実装する。完了条件: rollback backup がなく invalid live DB file-set の quarantine/delete にも失敗した場合、manual intervention required 相当を failed result に記録する。
- [x] 4.14 quarantine success の fresh DB readiness を検証する。完了条件: quarantine 成功後に live DB path に invalid main DB、`-wal`、`-shm` が残らず、Room が fresh DB を作成できる状態になる。
- [x] 4.15 pre-swap rollback backup creation failure test を追加する。完了条件: live main DB が存在し、その rollback backup 作成に失敗した場合、DB swap を実行せず、failed result を記録する。

## 5. Room migration 完了確認 component

- [x] 5.1 `DatabaseCallback.kt` の `onOpen()` と既存 `applicationScope` 利用を確認する。完了条件: `collectStartupGarbage()` と同じ post-DB-open hook で completion checker を非同期実行する前提を確認する。
- [x] 5.2 `PendingRestoreCompletionChecker` 相当の component を追加する。完了条件: marker が migration-pending の場合だけ post-migration validation を実行し、成功/失敗を marker/result に反映できる。
- [x] 5.3 completion checker 成功時 cleanup を実装する。完了条件: post-migration validation 成功時に marker を completed に更新してから success result、staging files、rollback backup を処理し、marker は cleanup の最後に削除する。
- [x] 5.4 completion checker 失敗時処理を実装する。完了条件: post-migration validation 失敗時に live DB file を即時置換せず、success result ではなく rollback-required result と診断情報を先に result file へ記録し、その後 marker を rollback-required へ atomic replace し、rollback backup を保持する。
- [x] 5.5 completion checker の operational exception と marker/result 書き込み失敗処理を修正する。完了条件: `CancellationException` は再 throw し、それ以外の `Exception` はログへ残して swallow する。成功経路は completed marker → success result → cleanup、validation 失敗経路は rollback-required result → rollback-required marker の順序を維持し、各 write 失敗時は後続 write/cleanup を停止する。live DB file を変更せず、直近の durable marker と rollback backup を次回 cold start recovery 用に残す。
- [x] 5.6 rollback-required 記録後の current session behavior を固定する。完了条件: rollback-required 記録後、同じ session では live DB file 置換、rollback、quarantine、process restart を行わず、次回 cold start recovery に委ねる。
- [x] 5.7 `DatabaseCallback` の constructor に `Provider<PendingRestoreCompletionChecker>` を追加し、`onOpen()` 内で `applicationScope.launch { pendingRestoreCompletionCheckerProvider.get().runIfNeeded() }` を呼ぶ。完了条件: `SlevoApplication.onCreate()` と `MainActivity.onCreate()` 本体には completion checker 呼び出しを追加しない。
- [x] 5.8 completion checker の I/O を `Dispatchers.IO` 相当で実行する。完了条件: marker/result/DB validation/file cleanup が main thread 直接 I/O にならない。
- [x] 5.9 completion checker の success 経路 unit tests と failure fake を修正する。完了条件: production と同様に marker/result write failure を `Exception` として注入し、completed marker write failureでは result/cleanup を実行せず migration-pending と rollback backup を保持すること、completed marker 成功後の success result write failureでは cleanup を実行せず completed marker、rollback backup、staging file を保持すること、いずれの operational exception も外へ出ないことを検証する。さらに各失敗後の cold-start recovery を実行し、前者は migration-pending の strict-validation success から false rollback なしで completed cleanup へ進み、後者は completed marker から success result 書き込みを再試行して成功後にだけ cleanup すること、および latest result が復旧後の success へ更新されることを確認する。既存の「completed marker write failure 後も継続する」期待値は停止する期待値へ置き換える。
- [x] 5.10 completion checker の validation failure 経路 unit tests を修正する。完了条件: rollback-required result write failureでは marker write を実行せず migration-pending と rollback backup を保持すること、result 成功後の rollback-required marker write failureでは migration-pending marker を recovery authority として保持すること、各 write failure 後に後続処理を行わず operational exception が外へ出ないことを個別に検証する。さらに各失敗後の cold-start recovery を実行し、result file の有無や rollback-required 表示より migration-pending marker が優先されること、strict-validation success なら false rollback せず completed cleanup へ進むこと、strict-validation failure なら rollback backup の有無に応じて rollback または quarantine へ進むこと、途中 result が recovery 後の latest success/failed status で上書きされることを確認する。marker 読み取りまたは post-migration validation の `Exception` でも直近の durable state を保持して return し、`CancellationException` は再 throw することを検証する。
- [x] 5.11 cleanup 部分失敗の ordering test を追加する。完了条件: post validation success 後に completed marker が durable に残る場合、次回 cold start が rollback ではなく cleanup retry へ進むことを確認する。
- [x] 5.12 success result 書き込み失敗時の cleanup 停止 test を追加する。完了条件: completed marker 後に success result 書き込みが失敗した場合、rollback backup と staging file を削除せず、次回 cold start で result 書き込みと cleanup を再試行する。
- [x] 5.13 `DatabaseCallback` の wiring test または DI/build test を確認する。完了条件: `Provider<PendingRestoreCompletionChecker>` 追加で Hilt/Room callback の循環依存が発生しない。
- [x] 5.14 `DatabaseCallback.onOpen()` の completion checker launch に局所的な防御境界を追加する。完了条件: coroutine 内で `CancellationException` を再 throw し、provider 取得または checker から漏れたそれ以外の `Exception` をログへ残して swallow する。checker 自身の non-throwing contract に依存せず、既存の startup garbage collection launch と UI を変更しない。
- [x] 5.15 `DatabaseCallback` の防御境界 tests を追加する。完了条件: checker の通常完了、`CancellationException` の再 throw、それ以外の `Exception` のログ記録と swallow を deterministic に検証し、checker failure が marker を上書きせず次回 cold start recovery authority を変えないことを確認する。

## 6. 内部診断情報と既存UI維持

- [x] 6.1 古い DB migration の有無を result file に永続化する項目を追加する。完了条件: `backupDatabaseVersion`、`currentDatabaseVersion`、`migrationRequired`、`migrationCompleted` 相当が再起動後も成功/失敗調査に使える形で残る。ログ出力は任意補助とし、唯一の記録先にしない。
- [x] 6.2 result file lifecycle を single latest status として実装する。完了条件: rollback-required 後の rollback/quarantine/manual-intervention 確定時は latest `failed` status で上書きし、`previousStatus = "rollback-required"`、`rollbackRequiredAt`、final failure reason 相当を保持する。
- [x] 6.3 `BackupConfirmDialogs.kt`、`BackupScreen.kt`、`BackupUiState.kt`、`BackupViewModel.kt` を確認し、古い DB migration 専用の user-facing 表示を追加していないことを確認する。完了条件: 確認ダイアログ、Snackbar、成功通知の文言がこの変更で変わらない。
- [x] 6.4 invalid/failure message mapping を確認し、future/too-old/path-missing/corrupt DB/rollback-required のユーザー通知は既存の無効バックアップ/復元失敗通知方針を維持する。完了条件: ユーザー向けには既存文言を使い、詳細 reason は result file に残る。
- [x] 6.5 ViewModel/UI tests がある場合、既存 UI 文言が古い DB 対応で変わらないことを regression test または snapshot/状態検証で確認する。完了条件: 古い DB preview 成功時も既存確認ダイアログ flow と同じ状態遷移になる。

## 7. migration 実動作テスト

- [x] 7.1 旧 schema DB fixture または test helper を作成する。完了条件: 少なくとも現在 version - 1 と `MINIMUM_RESTORABLE_DATABASE_VERSION` の SQLite DB file を unit/instrumented test で用意できる。
- [x] 7.2 supported old DB backup を pending restore に staging できる integration-style test を追加する。完了条件: pre-migration validation と marker 作成が成功する。
- [x] 7.3 old DB を live DB path に差し替えた後、Room open で migration されることを確認する test を追加する。完了条件: open 後に `PRAGMA user_version == current` と post-migration validation success を確認できる。
- [x] 7.4 minimum restorable DB からの multi-hop migration test を追加する。完了条件: `MINIMUM_RESTORABLE_DATABASE_VERSION` の fixture を live DB path に差し替えた後、Room open で current まで migration されることを確認する。
- [x] 7.5 migration 失敗を模擬する test を追加する。完了条件: migration-pending が stale として扱われ、次回 cold start で rollback と failed result が記録される。
- [x] 7.6 post-migration validation 失敗を模擬する test を追加する。完了条件: completion checker は rollback-required を記録し、次回 cold start の applier が rollback する。
- [x] 7.7 rollback-required result の test を追加する。完了条件: post-migration validation 失敗時に success result が記録されず、rollback-required と診断情報が result file に永続化され、追加 UI 文言が発生しない。
- [x] 7.8 post-migration validation 成功後の cleanup 部分失敗を模擬する test を追加する。完了条件: stale marker が残っても次回 cold start で false rollback せず cleanup を再試行する。
- [x] 7.9 current version backup の restore regression test を追加または更新する。完了条件: current backup も migration-pending → DatabaseCallback.onOpen completion checker → completed cleanup の lifecycle で成功する。
- [x] 7.10 rollback backup missing + invalid live DB の test を追加する。完了条件: migration-pending / rollback-required のどちらでも quarantine が実行され、fresh DB 起動を妨げない状態になり、failed result に final failure reason が残る。
- [x] 7.11 diagnostic field value test を追加する。完了条件: current success、old success、rollback-required、failed after rollback、rollback backup missing の `migrationRequired` と `migrationCompleted` が design.md の表通りになる。
- [x] 7.12 quarantine 非ブロック test を追加する。完了条件: quarantine directory が残っていても新規 restore 準備を拒否せず、quarantine directory を暗黙に削除しない。
- [x] 7.13 version rejection diagnostic test を追加する。完了条件: manifest/DB file の future、too-old、mismatch、migration path missing の詳細 reason が、preview-only 拒否では詳細ログ、ユーザー復元確定後に result writer または pending result area を確保できた後の拒否では result file に残る。

## 8. ドキュメント・検証・CI

- [x] 8.1 新規/変更 class/interface/object/data class/sealed class/enum の KDoc を確認する。完了条件: repository のコメント規則に違反しない。
- [x] 8.2 `openspec validate support-legacy-backup-restore --strict` を実行する。完了条件: strict validation が成功する。
- [x] 8.3 GitHub Actions Android CI を実行する。完了条件: build と unit tests が成功し、Run ID を記録する。
- [x] 8.4 実機またはエミュレータで current DB version バックアップの export → restore を確認する。完了条件: current backup 復元が regression していない。
- [x] 8.5 実機またはエミュレータで old DB version バックアップの restore → 再起動 → migration 完了 → cleanup を確認する。完了条件: アプリ起動後にデータが表示され、pending/rollback が cleanup される。
- [x] 8.6 実機またはエミュレータで future DB version バックアップが拒否されることを確認する。完了条件: DB/DataStore が変更されず、invalid 通知が表示される。
- [x] 8.7 実機またはエミュレータで migration 失敗または completion 前クラッシュ相当の recovery を確認する。完了条件: 次回 cold start 時に rollback/failed result が記録され、通常起動を妨げない。
- [x] 8.8 実機またはエミュレータで post-migration validation 失敗相当の rollback-required recovery を確認する。完了条件: Room open 中には live DB file を置換せず、次回 cold start で rollback/failed result が記録される。
