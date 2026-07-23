## 1. 事前確認

- [x] 1.1 `PendingRestoreApplier.kt` の `runIfNeeded()` と `recoverFromMigrationPending(marker)` を確認し、現在 `MIGRATION_PENDING` で `dbValidator.validate(liveDbFile)` を呼ぶ箇所を特定する。完了条件: strict validation が migration 前 DB を失敗扱いにする経路を実装メモまたはコメントで把握している。
- [x] 1.2 `BackupDatabaseValidator.kt` の interface / real implementation / fake implementation を確認する。完了条件: `preValidate()`、`validate()`、SQLite open helper の既存構造を把握している。
- [x] 1.3 `PendingRestoreCompletionChecker` と Room `DatabaseCallback` の呼び出し順序を確認する。完了条件: completion checker が Room migration 後に実行される前提を source inspection で確認している。
- [x] 1.4 `PendingRestoreApplierTest` の `MIGRATION_PENDING` 関連 tests を列挙する。完了条件: 既存 tests に `getUserVersion()` fake 値を追加すべき箇所が分かっている。

## 2. BackupDatabaseValidator 更新

- [x] 2.1 `BackupDatabaseValidator` interface に `getUserVersion(dbFile: File): Int?` を追加する。完了条件: interface 実装が compile error として検出される。
- [x] 2.2 real validator に `getUserVersion()` を実装し、Room / DAO / Hilt に依存せず SQLite file から `PRAGMA user_version` を読む。完了条件: 読み取り成功時は Int、失敗時は null を返す。
- [x] 2.3 `getUserVersion()` の KDoc を追加し、読み取り専用の version 判定 helper であること、失敗時 null であることを明記する。完了条件: AGENTS.md の class/interface/function comment rule に合う。
- [x] 2.4 test fake validator に `userVersion: Int?` または同等の設定値を追加する。完了条件: tests が case ごとに `getUserVersion()` の戻り値を制御できる。
- [x] 2.5 validator の dependency/source inspection test がある場合、`getUserVersion()` が Room / Hilt / DAO に依存しないことを検証対象に追加する。完了条件: dependency test が新 method を認識している。

## 3. MIGRATION_PENDING recovery 分岐実装

- [x] 3.1 `PendingRestoreApplier.recoverFromMigrationPending(marker)` の冒頭で `dbValidator.getUserVersion(liveDbFile)` を取得する。完了条件: strict validation より前に userVersion 分岐が行われる。
- [x] 3.2 `userVersion == null` の場合、既存 rollback backup の有無に応じて `rollbackAndFail(...)` または `quarantineAndFail(...)` を呼ぶ。完了条件: unreadable DB で待機継続しない。
- [x] 3.3 `userVersion >= currentDbVersion` の場合、既存の `dbValidator.validate(liveDbFile)` による strict validation path を維持する。完了条件: migration 済み DB の success/failure behavior が既存 tests と一致する。
- [x] 3.4 `userVersion == marker.databaseVersion` の場合、`dbValidator.preValidate(liveDbFile, marker.databaseVersion)` を実行する。完了条件: migration 前 DB で strict validation を呼ばない。
- [x] 3.5 `userVersion == marker.databaseVersion` かつ `preValidate()` 成功の場合、marker / rollback backup / pending directory / result file を変更せず return する。完了条件: `MIGRATION_PENDING` が残り、Room open 後の completion checker に処理を委ねる。
- [x] 3.6 `userVersion == marker.databaseVersion` かつ `preValidate()` 失敗の場合、既存 rollback/quarantine failure path を使う。完了条件: 破損または不整合な migration 前 DB を待機継続しない。
- [x] 3.7 `userVersion` が `marker.databaseVersion` と一致せず currentDbVersion 未満の場合、version mismatch として rollback/quarantine する。完了条件: marker=7/current=9/userVersion=8 のような中間 version で failure path に入る。
- [x] 3.8 failure reason / log message に `MIGRATION_PENDING` と userVersion / marker.databaseVersion / currentDbVersion の関係が分かる情報を入れる。完了条件: debugging 時にどの分岐で失敗したか追跡できる。

## 4. Tests 更新

- [x] 4.1 既存の `MIGRATION_PENDING` strict validation success test に `getUserVersion() = currentDbVersion` を設定する。完了条件: migration 済みとして既存 success path が通る。
- [x] 4.2 既存の `MIGRATION_PENDING` strict validation failure / rollback / quarantine tests に `getUserVersion() = currentDbVersion` を設定する。完了条件: migration 済み failure として既存 failure path が通る。
- [x] 4.3 `migrationPending_roomNotMigratedYet_preValidatePasses_keepsMigrationPending` を追加する。完了条件: `userVersion = marker.databaseVersion`、`preValidate = null` で marker が `MIGRATION_PENDING` のまま、rollback/quarantine/cleanup が呼ばれない。
- [x] 4.4 `migrationPending_roomNotMigratedYet_preValidateFails_rollsBackWhenBackupExists` を追加する。完了条件: rollback backup ありで `preValidate` failure 時に rollback failure result が記録される。
- [x] 4.5 `migrationPending_roomNotMigratedYet_preValidateFails_quarantinesWithoutRollback` を追加する。完了条件: rollback backup なしで `preValidate` failure 時に quarantine/failure path が実行される。
- [x] 4.6 `migrationPending_unreadableDb_rollsBackOrQuarantines` を追加する。完了条件: `getUserVersion() = null` で待機継続せず failure path に入る。
- [x] 4.7 `migrationPending_unexpectedIntermediateVersion_rollsBackOrQuarantines` を追加する。完了条件: `marker.databaseVersion < userVersion < currentDbVersion` で failure path に入る。
- [x] 4.8 `BackupDatabaseValidatorTest` または既存 validator test に `getUserVersion()` success/null tests を追加する。完了条件: 実 DB file の `PRAGMA user_version` を読めること、読めない file で null になることを検証する。

## 5. ドキュメント・コメント確認

- [x] 5.1 追加した interface method / real implementation / non-trivial branch に KDoc または brief comment を追加する。完了条件: AGENTS.md の comment rule に違反しない。
- [x] 5.2 `recoverFromMigrationPending()` が長くなる場合、`// --- Version classification ---`、`// --- Pre-migration wait ---` などの section comment で分割する。完了条件: 30行超の処理が読みやすく区切られている。
- [x] 5.3 migration 前で return する branch に「Room migration / completion checker に委ねる」旨のコメントを付ける。完了条件: なぜ cleanup しないのかが source 上で分かる。

## 6. 検証

- [x] 6.1 `openspec validate fix-migration-pending-recovery --strict` を実行する。完了条件: OpenSpec validation が成功する。
- [x] 6.2 Android CI を current branch に対して実行する（例: `gh workflow run "Android CI" --ref <current-branch> --repo cuore-mm/Slevo`）。完了条件: unit tests と CI APK build が成功する。
- [x] 6.3 CI が利用できない場合のみ repository-standard test command で `PendingRestoreApplierTest`、`BackupDatabaseValidatorTest`、関連 pending restore tests を実行する。完了条件: 変更対象 tests が成功する。
- [x] 6.4 実装後に `git diff` を確認し、P2-5（SAF truncate mode）や別 issue の修正をこの change に混ぜていないことを確認する。完了条件: diff scope が P2-4 の migration pending recovery に限定されている。
