## ADDED Requirements

### Requirement: MIGRATION_PENDING は Room migration 前の旧版 DB を保持する
システムは起動時に `MIGRATION_PENDING` marker を検出した場合、live DB の `user_version` が backup marker の databaseVersion と一致し、かつ current databaseVersion より古いとき、その DB を Room migration 待ちとして扱わなければならない（MUST）。この状態で migration 前 DB の pre-validation が成功する場合、システムは rollback、quarantine、pending cleanup、marker 更新を行ってはならない（MUST NOT）。

#### Scenario: Room migration 前の旧版 DB は rollback しない
- **WHEN** 起動時 recovery が `MIGRATION_PENDING` marker を読み、live DB の `user_version` が `marker.databaseVersion` と一致し、`marker.databaseVersion < currentDbVersion` であり、pre-validation が成功する
- **THEN** システムは marker を `MIGRATION_PENDING` のまま維持し、rollback backup と pending restore directory を保持し、Room open 後の migration と completion checker に処理を委ねる

#### Scenario: Room migration 前の旧版 DB が pre-validation に失敗する
- **WHEN** 起動時 recovery が `MIGRATION_PENDING` marker を読み、live DB の `user_version` が `marker.databaseVersion` と一致するが、pre-validation が失敗する
- **THEN** システムは restore 継続不能として既存の rollback backup があれば rollback し、rollback backup がなければ quarantine/failure path を実行する

### Requirement: MIGRATION_PENDING は Room migration 済み DB を strict validation する
システムは起動時に `MIGRATION_PENDING` marker を検出し、live DB の `user_version` が current databaseVersion 以上である場合、その DB を Room migration 済みとして扱い、current schema に対する strict validation を実行しなければならない（MUST）。

#### Scenario: Room migration 済み DB の strict validation が成功する
- **WHEN** 起動時 recovery が `MIGRATION_PENDING` marker を読み、live DB の `user_version` が current databaseVersion 以上であり、strict validation が成功する
- **THEN** システムは pending restore を完了済みとして扱い、success result の記録と pending cleanup を実行する

#### Scenario: Room migration 済み DB の strict validation が失敗する
- **WHEN** 起動時 recovery が `MIGRATION_PENDING` marker を読み、live DB の `user_version` が current databaseVersion 以上であり、strict validation が失敗する
- **THEN** システムは restore 継続不能として既存の rollback backup があれば rollback し、rollback backup がなければ quarantine/failure path を実行する

### Requirement: MIGRATION_PENDING は unreadable または想定外 version の DB を継続しない
システムは起動時に `MIGRATION_PENDING` marker を検出した場合、live DB の `user_version` を読み取れない、または `marker.databaseVersion` と current databaseVersion のどちらにも該当しない version を検出したとき、restore を待機継続してはならない（MUST NOT）。

#### Scenario: user_version を読み取れない
- **WHEN** 起動時 recovery が `MIGRATION_PENDING` marker を読み、live DB の `user_version` を読み取れない
- **THEN** システムは DB unreadable として既存の rollback backup があれば rollback し、rollback backup がなければ quarantine/failure path を実行する

#### Scenario: 想定外の中間 user_version
- **WHEN** 起動時 recovery が `MIGRATION_PENDING` marker を読み、live DB の `user_version` が `marker.databaseVersion` と一致せず、かつ current databaseVersion 未満である
- **THEN** システムは version mismatch として既存の rollback backup があれば rollback し、rollback backup がなければ quarantine/failure path を実行する

### Requirement: user_version 判定は Room に依存しない
システムは `MIGRATION_PENDING` の起動時復旧で live DB の `user_version` を判定するとき、Room database、DAO、Hilt graph に依存せず、SQLite file から `PRAGMA user_version` を読み取らなければならない（MUST）。

#### Scenario: 起動時 applier が Room 初期化前に user_version を読む
- **WHEN** `PendingRestoreApplier` が `SlevoApplication.onCreate()` の起動時処理として `MIGRATION_PENDING` recovery を実行する
- **THEN** システムは `AppDatabase` を生成せず、DAO を参照せず、SQLite file の pragma 読み取りだけで migration 前後を判定する
