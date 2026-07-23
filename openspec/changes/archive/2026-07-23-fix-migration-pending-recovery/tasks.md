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

## 7. Same-startup migration finalization

- [x] 7.1 marker の既存 `AtomicPendingRestoreMarkerFile` contract を finalization の commit point として確認し、result write に同等の temporary write、sync、atomic replace、failure rollback を追加する。完了条件: pre-commit/commit failure 後も reader は最後の valid marker/result JSON を読み、partial JSON は公開されない。
- [x] 7.2 `PendingRestoreFileStore.cleanupPending()` の contract を cleanup 成否が判定できる形にし、real/fake implementations を marker-last cleanup に揃える。完了条件: cleanup 対象は `pending-restore/` 内の staged DB、staged DataStore、rollback backup、DataStore rollback snapshot、marker に限定され、success result と quarantine incident は保持される。
- [x] 7.3 cleanup を partial deletion から idempotent に再開できるようにする。完了条件: 既にない payload は成功扱い、残存 payload または marker の削除失敗は failure 扱い、payload の全削除を確認するまで active marker を除去せず、marker 除去後の空 directory だけの残存は成功扱いになる。
- [x] 7.4 `recoverFromCompleted()` に result write と cleanup failure の局所処理を追加する。完了条件: exception/failure を outer startup failure handler へ伝播せず、`COMPLETED` を `FAILED` に変更せず、result failure 時は cleanup を呼ばず、cleanup failure 時は durable result、active marker、残存 artifact を次回 retry に保つ。
- [x] 7.5 `recoverFromMigrationPending()` の migration 済み strict validation success branch で、durable な `COMPLETED` marker を書いた直後に hardened `recoverFromCompleted()` を呼ぶ。完了条件: required restore restart の同じ startup 内で `COMPLETED marker -> migrationCompleted=true success result -> cleanup` が完了し、追加 restart を要求しない。
- [x] 7.6 active `MIGRATION_PENDING` / `COMPLETED` marker がある全 failure state では同じ session の次 prepare を block し、cleanup の marker-removal commit point 成功後だけ unblock する。完了条件: result consumption または success Snackbar 表示だけでは prepare を許可しない。
- [x] 7.7 same-startup finalization では application recovery 後に開始する既存 result observer、ViewModel、success Snackbar path を再利用する。完了条件: success result は `migrationCompleted=true` であり、Snackbar の text/layout と app lifecycle behavior に変更がない。

## 8. Finalization acceptance tests

- [x] 8.1 `PendingRestoreApplierTest` に migration 済み strict validation success の full-order test を追加する。完了条件: 1 回の `runIfNeeded()` の event が `writeMarker:COMPLETED`、`writeResult(success=true, migrationCompleted=true)`、`cleanupPending` の順で、app termination/restart request を発生させない。
- [x] 8.2 marker/result atomic write の pre-commit と replace/commit failure injection tests を追加する。完了条件: 最後の valid JSON が読み取り可能で partial JSON は公開されず、marker failure 後は `MIGRATION_PENDING`、result failure 後は `COMPLETED` が維持される。
- [x] 8.3 marker write failure の recovery/prepare test を追加する。完了条件: result/cleanup は未実行、pending artifact は保持、同じ session の prepare は拒否、次回 invocation で再試行できる。
- [x] 8.4 result write failure injection と retry/prepare test を追加する。完了条件: cleanup は未実行、marker は `COMPLETED`、outer failure handler の `FAILED` result はなく、同じ session の prepare は拒否、次回 invocation が result write と cleanup を成功させる。
- [x] 8.5 cleanup throw、payload partial deletion、marker deletion failure の tests を追加する。完了条件: success result は cleanup 対象外で保持され、active marker と残存 payload がある間は prepare が拒否され、既にない payload を許容して次回 invocation が cleanup を完了する。
- [x] 8.6 cleanup success と same-session next restore test を `PendingRestoreManagerPrepareTest` または同等の manager test に追加する。完了条件: marker-removal commit point 前は prepare を拒否し、成功後は同じ process/session で次 prepare を許可する。result consumption だけでは unblock しない。
- [x] 8.7 `PendingRestoreResultConsumerTest` に markerless `success=true, migrationCompleted=true` の regression test を追加する。完了条件: existing success Snackbar classification になり、intermediate/failure result は success 扱いされない。
- [x] 8.8 instrumented acceptance test を source に追加する。受け入れ観点: 1 回の startup で strict validation、ordered finalization、result observation、既存 text/layout の success Snackbar 表示まで到達し、process termination/追加 restart がない。Snackbar 表示後も cleanup failure marker があれば prepare は拒否される。追加した test は現在の Android CI scope では実行されず、将来の instrumented 実行に備えて保持する。
- [x] 8.9 completion checker と既存 pending recovery regression tests を更新する。完了条件: completion checker の `COMPLETED -> success result -> cleanup` 順序、pre-migration wait、rollback/quarantine、non-migration restore の既存 semantics が維持される。

## 9. Scope and validation

- [x] 9.1 変更した atomic result writer、cleanup interface/implementation/helper、非自明 function の KDoc・section comments を repository rules に合わせる。完了条件: durable commit、cleanup artifact ownership、marker-last commit point、failure return、retry behavior が source 上で判別できる。
- [x] 9.2 `openspec validate fix-migration-pending-recovery --strict` を実行する。完了条件: OpenSpec validation が成功する。
- [x] 9.3 clean pushed HEAD に対して現在の Android CI scope（`testCiUnitTest assembleCi`）を実行する。完了条件: unit tests と CI APK build が exact HEAD で成功する。instrumented acceptance test は source に保持するが現在の CI では実行されず、この change では新しい instrumentation workflow の追加または実行を完了条件にしない。検証記録: HEAD `19b472a69c73b869da4cf08d3b317531d757934b`、Android CI run `29628392853` が成功した。
- [x] 9.4 implementation diff を確認する。完了条件: UI text/layout、追加 restart、backup/marker/result format、Room schema、queued quarantine-copy P2 を変更していない。
