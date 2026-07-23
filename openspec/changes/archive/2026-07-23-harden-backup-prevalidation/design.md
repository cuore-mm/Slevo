## Context

`support-legacy-backup-restore` では、古い Room DB version のバックアップを current DB version へ migration して復元できるようにするため、`BackupDatabaseValidator` に `preValidate(dbFile, manifestDatabaseVersion)` と `validate(dbFile)` が分離された。現在の `RealBackupDatabaseValidator.preValidate()` は `PRAGMA integrity_check`、`PRAGMA user_version`、manifest の `databaseVersion`、`AppDatabase.hasMigrationPathForRestore()` のみを確認し、SQLite file が Slevo の DB schema を持つことは確認しない。

このままだと、別アプリの SQLite file でも `user_version` が Slevo の対応範囲内で manifest と一致すれば pending restore として staging される。DB swap 後に Room schema validation が `DatabaseCallback.onOpen()` より前に失敗すると、completion checker が同一起動で rollback-required を記録できず、次回 cold start まで recovery が遅れる。

一方で、古い DB version は現在 schema と異なるため、現在 version の Room identity hash や現在必須 table set をそのまま要求してはいけない。v2-v9 には exported Room schema JSON が存在するが、v1 schema は残っていない。ユーザー判断により v1 は復元対象外とし、source of truth がある v2 以降だけを対応範囲にする。

## Goals / Non-Goals

**Goals:**

- `MINIMUM_RESTORABLE_DATABASE_VERSION` を 2 に変更し、v1 backup restore を拒否する。
- `preValidate()` が、対象 DB version に対応する Slevo application table set の存在を確認する。
- current version の DB は preValidate 段階で既存の `validate()` と同等の strict validation を通す。
- 古い version の DB は current identity hash/current full table set ではなく、historical table set に基づく sanity check を行う。
- 不正 DB を pending restore 作成前、または起動時 DB swap 前に拒否できるようにする。

**Non-Goals:**

- v1 DB schema を復元して v1 backup restore を維持すること。
- historical Room identity hash の完全照合を必須にすること。
- historical column type、index、foreign key、primary key、Room schema JSON 全体の完全一致を検証すること。
- backup ZIP format や UI 文言を変更すること。
- Codex issue 2 の Cookie scope/persistence 修正を含めること。

## Decisions

### Decision 1: v1 は restore 対象外にする

`AppDatabase.MINIMUM_RESTORABLE_DATABASE_VERSION` を `1` から `2` に変更する。`BackupReader.validateManifest()` と `RealBackupDatabaseValidator.preValidate()` はこの定数を参照しているため、manifest と DB file の両方で v1 を too-old として拒否する。

代替案として v1 schema を migration DDL から再構成する方法もあるが、exported schema が存在せず、検証 source of truth が曖昧になる。restore は安全性が重要な起動前処理であるため、検証できない version は対象外にする。

### Decision 2: current version は `validate()` を再利用する

`preValidate()` で `userVersion == AppDatabase.CURRENT_DATABASE_VERSION` の場合は、既存の `validate(dbFile)` と同等の strict validation を実行する。これにより current DB backup は `room_master_table` の identity hash と current required table set を staging 前に確認できる。

実装時は、同じ `SQLiteDatabase` handle を使い回す private helper に分解してもよいし、`preValidate()` の DB close 後に `validate(dbFile)` を呼んでもよい。ただし二重 open にする場合は resource close を確実に行う。

### Decision 3: old version は version-aware table sanity check にする

`userVersion < CURRENT_DATABASE_VERSION` の場合は、version ごとの expected application table set を使って `sqlite_master` に table が存在するかを確認する。現在 schema の identity hash や current-only table を要求しない。

expected table set は `app/schemas/com.websarva.wings.android.slevo.data.datasource.local.AppDatabase/<version>.json` を source of truth とする。実装では runtime に JSON を読む必要はなく、`RealBackupDatabaseValidator` の companion object または専用 object に `Map<Int, Set<String>>` として定義してよい。

table set の目安:

- v2-v3: earliest exported schema の application tables
- v4: v2-v3 set + `post_identity_histories`
- v5: v4 set + `post_last_identities`
- v6-v9: v5 set + `thread_states`

Room internal table `room_master_table` は historical identity hash を必須にしないため old-version sanity check の必須 application table set には含めない。ただし current version の strict validation では既存 `validate()` が `room_master_table` を参照する。

### Decision 4: table 欠落は invalid backup として扱う

expected table が 1 つでも存在しない場合、`preValidate()` は error string を返す。呼び出し側である `BackupReader`、`PendingRestoreManager`、`PendingRestoreApplier` は既存の invalid/failed path に乗せ、DB swap を行わない。

エラーメッセージはテスト可能なように `missing expected table for version <version>: <table>` のように version と table 名を含める。

### Decision 5: migration path と Room migration 登録は分離しない

Room の通常 migration chain から `MIGRATION_1_2` を削除しない。既存ユーザーの live DB が v1 から起動する可能性を考慮し、Room 登録は維持してよい。ただし backup restore の `hasMigrationPathForRestore()` は `fromVersion < MINIMUM_RESTORABLE_DATABASE_VERSION` を false とし、v1 backup restore は拒否する。

## Implementation Contract

- 実装コード変更時は `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/AppDatabase.kt` の `MINIMUM_RESTORABLE_DATABASE_VERSION` を 2 にする。
- `AppDatabase.hasMigrationPathForRestore(fromVersion)` は `fromVersion < MINIMUM_RESTORABLE_DATABASE_VERSION` の場合 false を返すことを維持する。
- `RealBackupDatabaseValidator.preValidate()` は既存 checks の後、current version なら strict validation、old version なら historical table sanity check を実行する。
- `RealBackupDatabaseValidator.validate()` の post-migration strict validation の意味は変更しない。
- expected table set は exported schema v2-v9 を確認して作る。v1 の table set は作らない。
- `BackupReader.validateManifest()` の user-facing message は既存方針に合わせ、v1 は too-old として扱う。
- KDoc は annotation より上に置く。新規 class/object を作る場合は KDoc を付ける。

## Error Cases and Compatibility

- `manifest.databaseVersion = 1` は manifest validation で拒否する。
- `database/slevo.db` の `PRAGMA user_version = 1` は preValidate で拒否する。
- `manifest.databaseVersion` と `PRAGMA user_version` が異なる場合は schema sanity check 前に拒否する。
- SQLite file が open できない、または `PRAGMA integrity_check` が `ok` でない場合は既存通り拒否する。
- expected table set が定義されていない version は拒否する。
- current version で identity hash が不一致、または current required table が不足する場合は `validate()` と同じ理由で拒否する。
- old version で historical expected table が不足する場合は DB swap 前に拒否する。

## Testing Strategy

- `BackupDatabaseValidatorTest` に実 SQLite file を使う tests を追加し、v1 rejection、current version strict rejection、old version table-missing rejection、old version valid table set acceptance を確認する。
- `BackupReaderTest` で `manifest.databaseVersion = 1` が invalid/too-old になることを確認する。
- `AppDatabaseMigrationTest` で `MINIMUM_RESTORABLE_DATABASE_VERSION` が 2 であること、restore migration path helper が v1 を false、v2 を true とすることを確認する。
- `PendingRestoreManagerTest` または `PendingRestoreApplierTest` で `preValidate()` failure が pending restore 作成または DB swap を止める既存 behavior を確認する。
- 最後に `openspec validate harden-backup-prevalidation --strict` と CI を実行する。

## Risks / Trade-offs

- [Risk] v1 backup restore ができなくなる → [Mitigation] v1 は backup/export 機能導入前の schema で exported schema がなく、安全に検証できないため明示的に対象外にする。
- [Risk] table set 定数の管理コストが増える → [Mitigation] exported schema v2-v9 を source of truth とし、変更時は `AppDatabaseMigrationTest` で table set と schema JSON の乖離を検出する。
- [Risk] table-only check では column mismatch を完全には検出できない → [Mitigation] この check は non-Slevo DB を staging 前に落とす sanity guard とし、最終 schema correctness は Room migration と post-migration `validate()` が担う。
- [Risk] current version DB の preValidate が厳しくなり既存テスト fake と差が出る → [Mitigation] fake は failure/success injection に留め、本番 validator の実 SQLite tests を追加する。
