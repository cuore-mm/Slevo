## Context

`PendingRestoreApplier.runIfNeeded()` は `SlevoApplication.onCreate()` の起動時処理として pending restore marker を読み、`PREPARED`、`APPLYING`、`DB_SWAPPED`、`MIGRATION_PENDING` などの状態を復旧する。古い DB version のバックアップを復元する場合、applier は staged DB を live DB へ swap し、DataStore を反映した後に marker を `MIGRATION_PENDING` にする。その後 Room が `AppDatabase` を初回 open したときに migration を実行し、`PendingRestoreCompletionChecker` が post-validation と `COMPLETED` 遷移を行う。

現在の `PendingRestoreApplier.recoverFromMigrationPending(marker)` は `MIGRATION_PENDING` marker を見つけると、live DB に対して `BackupDatabaseValidator.validate(liveDbFile)` を実行する。この validation は現在 schema / `currentDbVersion` を前提にするため、Room migration 前の旧版 DB では `user_version mismatch` などで失敗する。結果として、`MIGRATION_PENDING` 書き込み後・Room migration 前に process が kill された場合、次回起動時に正常な旧版 DB が rollback/quarantine される。

起動順序の要点:

1. `SlevoApplication.onCreate()` が `PendingRestoreApplier.runIfNeeded()` を実行する。
2. `MIGRATION_PENDING` は「DB swap と DataStore 反映は完了し、Room migration / completion checker 待ち」を表す。
3. `AppDatabase` は Hilt graph 内で lazy に生成され、Room migration は DB open 時に実行される。
4. Room `onOpen` 後に `PendingRestoreCompletionChecker` が marker を確認し、migration 後の strict validation を行う。

この change は `MIGRATION_PENDING` 復旧時だけを修正し、他の state transition と ZIP / DataStore format は変更しない。

## Goals / Non-Goals

**Goals:**

- `MIGRATION_PENDING` 復旧時に Room migration 前の旧版 DB を誤って rollback/quarantine しない。
- live DB の `PRAGMA user_version` を読み、migration 前・migration 済み・不明/異常を明確に分岐する。
- migration 前の DB は `BackupDatabaseValidator.preValidate(liveDbFile, marker.databaseVersion)` 相当で integrity / backup schema compatibility を確認し、成功時は marker を維持する。
- migration 済みの DB は従来通り `BackupDatabaseValidator.validate(liveDbFile)` で strict validation し、成功時は `COMPLETED` へ進める。
- unreadable DB、想定外 version、pre-validation failure、strict validation failure では既存の rollback/quarantine 方針を維持する。
- JVM unit test で crash window 相当の state を再現できるよう validator fake を拡張する。

**Non-Goals:**

- Room migration 定義や `AppDatabase` schema version を変更しない。
- `PendingRestoreCompletionChecker` の post-migration validation 方針を変更しない。
- 新しい marker status を追加しない。
- pending restore の DB swap、rollback backup、DataStore reflection の順序を変更しない。
- ユーザー向け UI 文言や backup ZIP format は変更しない。

## Decisions

### Decision 1: `BackupDatabaseValidator` に user_version 読み取り API を追加する

`BackupDatabaseValidator` に `getUserVersion(dbFile: File): Int?` を追加する。実装は Room を使わず、SQLite を read-only 相当で open して `PRAGMA user_version` を読む。読み取りに失敗した場合は `null` を返す。

理由:

- `recoverFromMigrationPending()` は strict validation を実行する前に DB が migration 前か migration 後かを判定する必要がある。
- `preValidate()` と `validate()` はどちらも failure message を返す API であり、version 分岐用の値を直接返さない。
- validator に閉じ込めることで SQLite access の責務を既存 DB validation module に集約できる。

代替案:

- `recoverFromMigrationPending()` が直接 SQLite を開く案は採用しない。DB validation に関する低レベル I/O が applier に散らばるため。
- `preValidate()` の返却文字列を parse する案は採用しない。error message に依存して脆い。

### Decision 2: `recoverFromMigrationPending()` は user_version で 3 系統に分岐する

`PendingRestoreApplier.recoverFromMigrationPending(marker)` は次の順序で処理する。

1. `liveDbFile = dbSwapper.getLiveDbFile()` と rollback backup の有無を確認する。
2. `val userVersion = dbValidator.getUserVersion(liveDbFile)` を取得する。
3. `userVersion == null` の場合は DB unreadable とみなし、rollback backup があれば `rollbackAndFail(...)`、なければ `quarantineAndFail(...)` を使う。
4. `userVersion >= currentDbVersion` の場合は Room migration 済みとして `dbValidator.validate(liveDbFile)` を実行する。
   - 成功: `recoverFromCompleted(...)` と同等の success result / cleanup path、または既存の completed transition helper を使って `COMPLETED` へ進める。
   - 失敗: 既存の stale `MIGRATION_PENDING` failure path と同じ rollback/quarantine。
5. `userVersion == marker.databaseVersion` の場合は Room migration 前として `dbValidator.preValidate(liveDbFile, marker.databaseVersion)` を実行する。
   - 成功: marker を `MIGRATION_PENDING` のまま残し、何も cleanup せず return する。次に Room が DB を open したときに migration と `PendingRestoreCompletionChecker` が進める。
   - 失敗: rollback/quarantine。
6. 上記以外（例: marker=7、current=9、userVersion=8）は想定外の中間/不一致 version として rollback/quarantine する。

理由:

- `MIGRATION_PENDING` の本来の意味を維持しながら、Room migration 前だけを待機継続できる。
- Room migration 済みかつ completion checker が完了前に落ちた case では、従来通り applier が復旧完了できる。
- DB unreadable / corruption / version mismatch は早期に復旧処理へ回せる。

代替案:

- `validate()` を単純に `preValidate()` へ置き換える案は採用しない。Room migration 済み DB では `user_version == currentDbVersion` になり、`preValidate(..., marker.databaseVersion)` が逆に失敗するため。
- `MIGRATION_PENDING` では validation せず常に return する案は採用しない。破損 DB を Room に渡して起動クラッシュを誘発する可能性があるため。
- 新 marker status を追加する案は採用しない。この問題は `MIGRATION_PENDING` の復旧分岐で解け、state machine 全体の変更は不要なため。

### Decision 3: migration 前で待機継続する場合は marker / rollback / result を変更しない

`userVersion == marker.databaseVersion` かつ `preValidate()` 成功の場合、`recoverFromMigrationPending()` は marker を再書き込みせず、rollback backup と pending directory を保持したまま return する。

理由:

- rollback backup は migration / completion 失敗時の復旧に必要である。
- marker を維持することで、Room open 後の `PendingRestoreCompletionChecker` が同じ `MIGRATION_PENDING` を処理できる。
- result file は既に「migration required / not completed」相当の状態を示している可能性があり、この change では UI 表示方針を変えない。

### Decision 4: tests は migration 前後を明示的に fake する

`PendingRestoreApplierTest` の fake validator に `userVersion: Int?` を追加し、`getUserVersion()` の戻り値を test case ごとに制御する。既存の `MIGRATION_PENDING` tests は「Room migration 済み」を表すよう `userVersion = currentDbVersion` を設定する。

新規 tests では次を検証する。

- migration 前 (`userVersion == marker.databaseVersion`) + preValidate 成功: rollback せず marker は `MIGRATION_PENDING` のまま。
- migration 前 + preValidate 失敗: rollback/quarantine する。
- migration 済み (`userVersion >= currentDbVersion`) + validate 成功: completion path へ進む。
- `userVersion == null`: unreadable DB として rollback/quarantine する。
- `userVersion` が marker/current のどちらでもない: rollback/quarantine する。

## Implementation Contract

実装 agent は以下を守ること。

1. `BackupDatabaseValidator` interface の全実装・fake に `getUserVersion(dbFile: File): Int?` を追加する。
2. real validator の `getUserVersion()` は Room / Hilt / DAO に依存せず、SQLite pragma の読み取りだけを行う。
3. `PendingRestoreApplier.recoverFromMigrationPending(marker)` 以外の state recovery logic は必要最小限しか触らない。
4. `MIGRATION_PENDING` で `userVersion == marker.databaseVersion` かつ `preValidate()` 成功時は、marker、rollback backup、pending directory を削除・更新しない。
5. `userVersion >= currentDbVersion` の場合は既存 strict validation 成功/失敗 semantics を維持する。
6. rollback/quarantine helper は既存の `rollbackAndFail(...)` / `quarantineAndFail(...)` と同じ方針を使い、新しい失敗処理を重複実装しない。
7. failure reason には、可能な範囲で `MIGRATION_PENDING`、`user_version`、`marker.databaseVersion`、`currentDbVersion` の関係が分かる文言を含める。
8. production code に Room migration を手動実行する処理を追加しない。migration 実行は Room open に任せる。
9. 追加/変更する class・interface・非自明 function は repository の KDoc/comment rules に従う。

## Risks / Trade-offs

- [Risk] migration 前の DB を待機継続したまま、アプリがその起動で DB を open しないと `MIGRATION_PENDING` が残る。
  → Mitigation: 既存設計でも `MIGRATION_PENDING` は Room open / completion checker 待ちであり、次回起動でも同じ判定を繰り返せる。DB を open した時点で Room migration が進む。
- [Risk] `preValidate()` が旧版 DB に対して重すぎる、または現行 migration path 判定と重複する。
  → Mitigation: crash window の safety check として起動時に一度実行する。既存 pre-validation を再利用し、新しい検証ロジックを増やさない。
- [Risk] `getUserVersion()` が unreadable DB で null を返すため、実際には一時的 I/O error の場合も rollback する。
  → Mitigation: 起動時 recovery で DB が読めない状態は restore 継続不能とみなし、既存 rollback/quarantine 方針に合わせる。
- [Risk] `userVersion` が marker と current の間の値になる場合、Room migration が一部だけ完了した可能性がある。
  → Mitigation: Room migration は transaction 的に適用される想定だが、想定外状態として rollback/quarantine し、破損/中途半端な DB を live に残さない。

## Migration Plan

- Runtime data migration は不要。
- Marker format、backup ZIP format、Room schema version は変更しない。
- 実装後に `openspec validate fix-migration-pending-recovery --strict` を実行する。
- Android CI を current branch に対して実行し、`PendingRestoreApplierTest` と validator tests を含む unit tests が通ることを確認する。
- rollback strategy: この change は `MIGRATION_PENDING` recovery branch に限定されるため、問題があれば該当 branch の分岐と `getUserVersion()` 追加を revert して従来挙動へ戻せる。

## Testing Strategy

- `PendingRestoreApplierTest`
  - `migrationPending_roomNotMigratedYet_preValidatePasses_keepsMigrationPending` を追加する。完了条件: marker status が `MIGRATION_PENDING` のまま、rollback helper / cleanup helper が呼ばれない。
  - `migrationPending_roomNotMigratedYet_preValidateFails_rollsBackOrQuarantines` を追加する。完了条件: rollback backup ありなら rollback、なしなら quarantine になる。
  - 既存の strict validation success/failure tests は `getUserVersion() = currentDbVersion` を明示する。
  - `migrationPending_unreadableDb_rollsBackOrQuarantines` を追加する。完了条件: `getUserVersion() = null` で restore 継続しない。
  - `migrationPending_unexpectedIntermediateVersion_rollsBackOrQuarantines` を追加する。完了条件: marker/current のどちらでもない version で failure path に入る。
- `BackupDatabaseValidatorTest`
  - 実 DB file の `PRAGMA user_version` を設定し、`getUserVersion()` が値を返すことを確認する。
  - 読めない file / 存在しない file で `null` を返すことを確認する。
- Source inspection / dependency tests
  - `getUserVersion()` が Room / Hilt / DAO に依存しないことを確認する。
  - `PendingRestoreCompletionChecker` の migration 後 strict validation flow が変わっていないことを確認する。

## Open Questions

なし。
