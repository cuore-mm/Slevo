# quarantine-copy-integrity Specification

## Purpose
TBD - created by archiving change fix-quarantine-copy-integrity. Update Purpose after archive.
## Requirements
### Requirement: quarantine完了はartifact保持とlive source除去の両方を必要とする

systemはquarantine開始時に存在したmain databaseおよびsidecar fileごとに、renameまたはcopy APIが正常完了し、incident内のdestinationがregular fileとして実在して操作前source sizeと一致し、元のRoom pathからsourceが存在しなくなったことを確認した場合だけquarantineを完了扱いにしなければならない（MUST）。

#### Scenario: renameでquarantineが完了する
- **WHEN** 存在するDB setの各sourceをincidentへrenameでき、destinationのregular-file実在と操作前size一致およびsource不存在を確認できる
- **THEN** systemはquarantineを完了扱いにする
- **AND** failure resultは実在するincident pathをquarantine成功先として報告できる

#### Scenario: copyとsource削除でquarantineが完了する
- **WHEN** renameが失敗したsourceについてfallback copyが完了し、source削除に成功し、destinationのregular-file実在と操作前size一致およびsource不存在を確認できる
- **THEN** systemはそのfileのquarantineを成功扱いにする

#### Scenario: copy後にsource削除が失敗する
- **WHEN** renameが失敗し、fallback copyは完了するが、source削除が失敗してRoom pathにsourceが残る
- **THEN** systemはquarantineを完了扱いにしない
- **AND** incident内のcopy artifactを削除しない
- **AND** failure resultはincident pathをquarantine成功先として報告しない

### Requirement: quarantine部分失敗はretryable recovery stateを保持する

systemはincident作成またはDB set内の任意のfileのquarantineが完了しない場合、元のretryable markerとpending recovery payloadを保持し、terminal FAILED markerへの更新およびpending cleanupを実行してはならない（MUST NOT）。

#### Scenario: main databaseのdeleteが失敗する
- **WHEN** main databaseのfallback copy後にsource deleteが失敗する
- **THEN** systemは元の`MIGRATION_PENDING`または`ROLLBACK_REQUIRED` markerを保持する
- **AND** systemは`cleanupPending()`を実行しない
- **AND** 次回cold startで同じrecoveryを再試行できる

#### Scenario: sidecar処理が部分失敗する
- **WHEN** main database処理前のsidecar quarantineが失敗する
- **THEN** systemはmain databaseをRoom pathに残す
- **AND** 先に移動済みのsourceをincident artifactからbest-effortで元pathへ復元する
- **AND** systemはmarkerとpending payloadを保持してquarantine成功を報告しない

#### Scenario: 未完了result書込も失敗する
- **WHEN** quarantine部分失敗後のfailure result書込が例外で失敗する
- **THEN** systemはその例外を理由にmarkerをterminal FAILEDへ変更しない
- **AND** systemはpending recovery stateをcleanupしない

#### Scenario: 次回起動で再試行が成功する
- **WHEN** 部分失敗後にfilesystem障害が解消され、保持markerから次回cold start recoveryを実行する
- **THEN** systemはquarantineを再試行する
- **AND** 全対象sourceの除去とartifact保持を確認した後だけFAILED resultとpending cleanupへ終端する

### Requirement: quarantine部分artifactは回復処理で失われない

systemはquarantine未完了時に作成済みのincident directoryとcopy/move済みartifactを削除または上書きしてはならない（SHALL NOT）。

#### Scenario: source復元を実行する
- **WHEN** 後続fileのquarantine失敗後に先行成功sourceをincident artifactから元pathへ復元する
- **THEN** systemは復元元のincident artifactを保持する
- **AND** 次回試行は既存incidentを上書きしない新しいincidentを使用する

#### Scenario: source復元も失敗する
- **WHEN** quarantine部分失敗後に先行sourceの復元が失敗する
- **THEN** systemはpartial incidentとpending recovery stateを保持する
- **AND** systemはquarantine成功を報告せずpending cleanupを実行しない

#### Scenario: 現在失敗中のfileからsourceが消失している
- **WHEN** renameまたはdelete後のpostcondition検証が失敗し、現在処理中のsourceがRoom pathから消えてdestinationが存在する
- **THEN** systemは現在失敗中のfileもdestinationからsourceへの復元対象にする
- **AND** 復元後もdestination artifactを保持する

#### Scenario: sourceと利用可能なdestinationの両方がない
- **WHEN** postcondition失敗時にsourceが存在せず、復元可能なdestinationも存在しない
- **THEN** systemはmarker、pending payload、incidentを保持する
- **AND** systemはquarantine成功を報告せずpending cleanupを実行しない

