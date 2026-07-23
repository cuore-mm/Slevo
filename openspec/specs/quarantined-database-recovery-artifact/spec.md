# quarantined-database-recovery-artifact Specification

## Purpose
TBD - created by archiving change preserve-quarantined-database-artifact. Update Purpose after archive.
## Requirements
### Requirement: quarantine artifactはpending stagingから独立して保存される

systemはrollback snapshotを利用できないrestore recovery failureでlive databaseをquarantineする場合、`pendingDir`の子孫ではないapplication-internal recovery directoryへ保存しなければならない（SHALL）。

#### Scenario: pending cleanup後もquarantineが残る
- **WHEN** systemがlive databaseをquarantineし、その後pending stagingを再帰cleanupする
- **THEN** pending stagingは削除される
- **AND** quarantine incident directoryと保存済みdatabase artifactは存在し続ける

#### Scenario: FAILED pending処理後もquarantineが残る
- **WHEN** FAILED状態のpending restoreを次回起動または次回restore準備でcleanupする
- **THEN** systemはquarantine incident directoryを削除しない

### Requirement: database setをincident単位で保持する

systemは1回のquarantine failureごとに既存artifactと衝突しないincident directoryを作り、live database本体と元々存在したWAL/SHM sidecarを同じincidentへ保存しなければならない（SHALL）。

#### Scenario: DB本体とsidecarsを保存する
- **WHEN** live database本体、WAL、SHMが存在する状態でquarantineを実行する
- **THEN** systemは3 filesを同じ新規incident directoryへ保存する

#### Scenario: sidecarが存在しない
- **WHEN** live database本体は存在するがWALまたはSHMが存在しない状態でquarantineを実行する
- **THEN** systemは存在するfilesを保存する
- **AND** 存在しないsidecarだけを理由にmain database artifactを破棄しない

#### Scenario: 複数failureが発生する
- **WHEN** 同じinstallationでquarantine対象failureが複数回発生する
- **THEN** systemはfailureごとに異なるincident directoryを使用する
- **AND** 後のfailureは先のartifactを上書きまたは削除しない

### Requirement: quarantine resultは実在artifactを正確に報告する

systemはmain databaseがincident directoryに存在することを確認した場合だけ、そのincident pathをquarantine成功先としてfailure resultへ記録しなければならない（MUST）。

#### Scenario: quarantine保存に成功する
- **WHEN** main databaseがincident directoryへ保存され、実在確認に成功する
- **THEN** failure resultはその実在incident directoryのpathを報告する

#### Scenario: quarantine保存に失敗する
- **WHEN** incident directory作成、main databaseのmove、またはfallback copyが失敗し、incident内のmain databaseを確認できない
- **THEN** failure resultはquarantine保存が完了しなかったことを報告する
- **AND** 存在しないpathをquarantine成功先として報告しない

#### Scenario: result書込後にpending cleanupする
- **WHEN** systemがquarantine failure resultを書き、その後pending cleanupを実行する
- **THEN** resultに記録された成功pathはcleanup後も実在する

### Requirement: quarantine artifactは暗黙に自動削除されない

systemはpending restoreの成功、失敗、再起動、再準備に伴う通常cleanupからquarantine recovery rootを除外しなければならない（SHALL）。

#### Scenario: cold startで再実行する
- **WHEN** quarantine failure後にapplicationが再起動してpending restore recoveryを再実行する
- **THEN** 既存quarantine incidentとそのdatabase artifactは保持される

#### Scenario: 新しいrestoreを準備する
- **WHEN** quarantine failure後に新しいrestoreを準備するためpending stagingを初期化する
- **THEN** 既存quarantine incidentsは保持される

#### Scenario: restore completion cleanupを実行する
- **WHEN** pending restore completion処理がstaging cleanupを実行する
- **THEN** systemはquarantine recovery rootをcleanup対象に含めない

