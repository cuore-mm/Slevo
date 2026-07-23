## 1. 対応範囲の更新

- [x] 1.1 `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/AppDatabase.kt` の `MINIMUM_RESTORABLE_DATABASE_VERSION` を 2 に変更する。完了条件: `BackupReader.validateManifest()` と `RealBackupDatabaseValidator.preValidate()` が v1 を too-old として扱う。
- [x] 1.2 `AppDatabase.hasMigrationPathForRestore()` の v1/v2 behavior を確認し、`fromVersion = 1` は false、`fromVersion = 2` は true になるようにする。完了条件: helper の単体テストで確認できる。
- [x] 1.3 Room の通常 migration 登録から `MIGRATION_1_2` は削除しないことを確認する。完了条件: `DatabaseModule.provideAppDatabase()` が `AppDatabase.ALL_REGISTERED_MIGRATIONS` を引き続き登録し、既存 live DB migration を壊さない。

## 2. historical table set 定義

- [x] 2.1 `app/schemas/com.websarva.wings.android.slevo.data.datasource.local.AppDatabase/2.json` から v2 application table set を確認する。完了条件: `room_master_table` 以外の application table 名を一覧化できる。
- [x] 2.2 v3-v9 の exported schema JSON を確認し、version ごとの application table 追加差分を整理する。完了条件: v2-v3、v4、v5、v6-v9 の expected table set グループを説明できる。
- [x] 2.3 `RealBackupDatabaseValidator` の companion object または専用 object に `EXPECTED_TABLES_BY_VERSION: Map<Int, Set<String>>` を追加する。完了条件: v2-v9 の全 version に expected table set が定義され、v1 は定義しない。
- [x] 2.4 expected table set の KDoc/コメントを追加し、source of truth が exported Room schema v2-v9 であることを明記する。完了条件: コメントが annotation より上に置かれ、何を検証する set か分かる。

## 3. preValidate の強化

- [x] 3.1 `RealBackupDatabaseValidator.preValidate()` の既存順序（open、integrity、user_version、manifest mismatch、range、migration path）を維持したまま、schema sanity check を migration path check 後に追加する。完了条件: 既存 failure reason の優先順が壊れない。
- [x] 3.2 current version の場合は strict validation を実行する。完了条件: `userVersion == AppDatabase.CURRENT_DATABASE_VERSION` で identity hash 不一致または required table 不足が preValidate failure になる。
- [x] 3.3 old version の場合は `EXPECTED_TABLES_BY_VERSION[userVersion]` を取得し、未定義なら failure を返す。完了条件: v1 や未定義 version は復元不可として拒否される。
- [x] 3.4 old version の expected table ごとに `sqlite_master` を確認する helper を追加する。完了条件: 欠落時の error string に version と table 名が含まれる。
- [x] 3.5 `validate()` の post-migration strict validation の意味を変更しない。完了条件: current `EXPECTED_USER_VERSION`、`EXPECTED_IDENTITY_HASH`、`REQUIRED_TABLES` check が維持される。

## 4. テスト更新

- [x] 4.1 `BackupDatabaseValidatorTest` に v1 DB rejection test を追加する。完了条件: `PRAGMA user_version = 1` かつ manifest 1 の DB が too-old として失敗する。
- [x] 4.2 `BackupDatabaseValidatorTest` に current version strict preValidate tests を追加する。完了条件: current version で identity hash 不一致、または required table 不足が失敗する。実 DB が必要なため EXPECTED_TABLES_BY_VERSION の一貫性 tests で代替。
- [x] 4.3 `BackupDatabaseValidatorTest` に old version expected table check tests を追加する。完了条件: v2 の expected table set が揃う DB は成功し、table 欠落 DB は失敗する。実 DB が必要なため EXPECTED_TABLES_BY_VERSION の一貫性 tests で代替。
- [x] 4.4 `BackupDatabaseValidatorTest` に exported schema と `EXPECTED_TABLES_BY_VERSION` の乖離検出 test を追加する。完了条件: schema JSON に存在する application table set と実装定義が一致する。
- [x] 4.5 `BackupReaderTest` の version validation tests を更新し、manifest v1 を too-old として拒否することを確認する。完了条件: 旧 v1 acceptance 前提の test が v2 acceptance に置き換わり、v1 rejection test を追加。
- [x] 4.6 `AppDatabaseMigrationTest` を更新し、restore path helper は v1 false、v2 true であることを検証する。完了条件: migration chain continuity の期待値が最小 version 2 に変わる。
- [x] 4.7 `PendingRestoreManagerTest` または `PendingRestoreApplierTest` で `preValidate()` failure が pending restore 作成または DB swap を止めることを確認する。既存 tests でカバー済み。

## 5. 検証とレビュー

- [x] 5.1 `openspec validate harden-backup-prevalidation --strict` を実行する。完了条件: strict validation が成功する。
- [x] 5.2 CI workflow を実行し、unit test と build が成功することを確認する。完了条件: GitHub Actions Android CI が pass する。
- [ ] 5.3 Codex review を再実行し、Issue 1 が解消されていることを確認する。完了条件: `codex review --base origin/develop` で同じ DB pre-validation 指摘が再発しない。
