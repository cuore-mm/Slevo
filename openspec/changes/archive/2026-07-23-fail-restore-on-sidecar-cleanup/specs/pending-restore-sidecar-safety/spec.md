## ADDED Requirements

### Requirement: DB 切替前の live sidecar cleanup
システムは Room/SQLite writer が live DB を開く前の同期 cold-start restore 処理内で、pending restore の staged DB 置換または rollback DB 復元より前に、存在する live DB の `-wal` と `-shm` を削除しなければならない（SHALL）。いずれかの削除が `false` を返すか例外を送出した場合、システムは cleanup failure として扱わなければならない（MUST）。cleanup 開始から DB 置換または rollback 完了まで、システムは Room/SQLite writer に sidecar を再生成させてはならない（MUST NOT）。

#### Scenario: Cold-start quiescence
- **WHEN** pending restore の初回 apply または recovery が DB file 操作を実行する
- **THEN** システムは Hilt `AppDatabase` 生成前に同期処理を完了し、Room open 後に必要となった rollback は次回 cold start へ延期する

#### Scenario: Sidecar が存在しない置換
- **WHEN** live WAL と SHM が存在しない状態で staged DB 置換を開始する
- **THEN** システムは通常の DB 置換処理へ進む

#### Scenario: Sidecar の削除に成功する置換
- **WHEN** 存在する live WAL と SHM の削除がすべて成功する
- **THEN** システムは sidecar が存在しない状態で staged DB を live DB へ置換する

#### Scenario: WAL 削除が失敗する置換
- **WHEN** live WAL の削除が `false` を返すか例外を送出する
- **THEN** システムは置換失敗を返し、temp copy、live main DB の削除、rename を開始しない

#### Scenario: SHM 削除が失敗する置換
- **WHEN** live SHM の削除が `false` を返すか例外を送出する
- **THEN** システムは置換失敗を返し、live main DB を別世代の DB に切り替えない

#### Scenario: Sidecar 削除が失敗する rollback
- **WHEN** 有効な rollback snapshot があり、live WAL または SHM の削除が失敗する
- **THEN** システムは rollback 失敗を返し、live main DB の削除・上書きと rollback WAL の復元を開始しない

### Requirement: Cleanup failure 時の回復可能性
システムは sidecar cleanup failure によって安全な rollback が完了しない場合、既存の retryable recovery 状態と pending/rollback artifact を保持しなければならない（MUST）。cleanup failure を無視して terminal success または DB 切替へ進んではならない（MUST NOT）。

#### Scenario: Rollback cleanup failure が継続する
- **WHEN** DB 置換失敗後または recovery 中の rollback で sidecar cleanup failure が継続する
- **THEN** システムは `ROLLBACK_REQUIRED` を記録し、pending directory と完成 rollback snapshot を削除せず、次回起動で rollback を再試行する

#### Scenario: 一時的失敗後に安全な rollback が完了する
- **WHEN** 最初の sidecar cleanup は失敗するが、その後の既存 rollback 経路で cleanup と rollback が完了する
- **THEN** システムは異なる DB 世代と stale sidecar を共存させず、既存の terminal failure cleanup を続行できる

#### Scenario: WAL 削除後に SHM 削除が失敗する
- **WHEN** WAL の削除成功後に SHM の削除が失敗する
- **THEN** システムは部分 cleanup を failure として返し、live main DB と rollback artifact を保持して再試行可能にする

### Requirement: Sidecar cleanup failure の検証可能性
システムは WAL と SHM の削除結果を unit test から決定的に制御できなければならず（SHALL）、production では実際の filesystem 削除結果を使用しなければならない（MUST）。

#### Scenario: 削除が false を返す test
- **WHEN** unit test が対象 sidecar の削除結果 `false` を注入する
- **THEN** 置換または rollback は cleanup failure を返し、後続の破壊的操作が未実行であることを検証できる

#### Scenario: 削除が例外を送出する test
- **WHEN** unit test が対象 sidecar の削除例外を注入する
- **THEN** 置換または rollback は cleanup failure を返し、例外を成功として扱わない

#### Scenario: 全 failure 組合せの call-site gate test
- **WHEN** WAL と SHM のそれぞれについて `false` と例外を置換・rollback の各 call site に注入する
- **THEN** システムは全 8 組で後続の DB 切替操作が未実行であることを検証できる
