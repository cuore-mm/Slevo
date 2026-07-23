# durable-datastore-restore-rollback Specification

## Purpose
TBD - created by archiving change persist-datastore-rollback-snapshot. Update Purpose after archive.
## Requirements
### Requirement: Restore前DataStore snapshotをDB置換前に永続化する
システムはpending restoreでDBを置換する前に、settings、tabs、restore対象cookiesの現在値をlosslessなrollback snapshotとしてapp-private storageへatomicに確定しなければならない（MUST）。

#### Scenario: DBとDataStoreのrollback sourceを準備する
- **WHEN** pending restoreがAPPLYING状態でDB rollback snapshotとDataStore target validationを完了する
- **THEN** システムは現在のDataStore snapshotをatomicに確定した後だけ`ROLLBACK_READY`へ遷移してDB置換を開始する

#### Scenario: DataStore snapshot作成に失敗する
- **WHEN** DataStore読取、serialization、またはsnapshot publishが失敗する
- **THEN** システムはDBを置換せず、DataStoreを変更せず、restore準備失敗を記録する

#### Scenario: Snapshot publish中にprocessが終了する
- **WHEN** DataStore snapshotのatomic write開始後かつ確定前にprocessが終了する
- **THEN** 次回起動は未確定snapshotをrollback sourceとして使用せず、DBとDataStoreを変更前のまま扱う

### Requirement: DataStore snapshotは全Preferences状態をlosslessに保持する
システムはsnapshot対象storeの全keyについてkey名、value type、valueを保持し、key不在、empty value、cookies対象外を区別しなければならない（MUST）。

#### Scenario: Supported Preferences型をround-tripする
- **WHEN** restore前DataStoreがString、Boolean、Int、Long、Float、Double、StringSetの値を含む
- **THEN** snapshot encode/decode後の各key、type、valueはrestore前と一致する

#### Scenario: 空のcookies storeをsnapshotする
- **WHEN** cookiesがrestore対象でrestore前cookies DataStoreにkeyが存在しない
- **THEN** snapshotはcookies対象外ではなく空storeとして記録し、rollback時にcookies DataStoreをclearする

#### Scenario: Cookiesをrestore対象外にする
- **WHEN** pending restoreがcookiesを含めない
- **THEN** snapshotはcookies非対象を明示し、rollback時にcookies DataStoreを変更しない

#### Scenario: Snapshot内容が不正である
- **WHEN** snapshotにunsupported format version、duplicate key、unknown type、またはtypeとvalueの不整合がある
- **THEN** システムは対象DataStoreをeditせず、rollback未完了としてartifactを保持する

### Requirement: DB rollback時にDataStoreもrestore前状態へ戻す
システムはDataStore反映開始後またはrestore完了確認前にDBをrollbackする場合、durable snapshotからsettings、tabs、対象cookiesもrestore前状態へ戻さなければならない（MUST）。

#### Scenario: DB_SWAPPED直後にprocessが終了する
- **WHEN** DB_SWAPPED marker確定後、DataStore反映完了前にprocessが終了する
- **THEN** 次回起動はDBと対象DataStoreをrestore前snapshotへrollbackする

#### Scenario: Settingsだけ反映後にprocessが終了する
- **WHEN** settings write成功後かつtabs write完了前にprocessが終了する
- **THEN** 次回起動はsettings、tabs、対象cookiesをsnapshotからfull overwriteし、DBもrestore前へ戻す

#### Scenario: Tabs反映後にprocessが終了する
- **WHEN** settingsとtabs write成功後かつ対象cookies write完了前にprocessが終了する
- **THEN** 次回起動は全対象DataStoreとDBをrestore前へ戻す

#### Scenario: MIGRATION_PENDING validationが失敗する
- **WHEN** DataStore反映後のMIGRATION_PENDINGでDB validationまたはmigration completion validationが失敗する
- **THEN** システムはDBだけでなく全対象DataStoreもrestore前へrollbackする

#### Scenario: Fresh-install rollbackを実行する
- **WHEN** `hadExistingLiveDb`がfalseのrestoreをrollbackする
- **THEN** システムはrestore中に作成したDB setを削除し、既存DataStoreはsnapshotからrestore前状態へ戻す

### Requirement: Rollback完了までartifactを保持して再試行する
システムはDBとDataStoreのrollbackが両方完了するまでrollback sourceとpending stagingを削除してはならず（MUST NOT）、未完了rollbackを次回起動で再試行しなければならない（MUST）。

#### Scenario: DB rollbackとDataStore rollbackが成功する
- **WHEN** DBと対象DataStoreがrestore前状態へ正常に戻る
- **THEN** システムはfailure resultを記録し、pending stagingと両rollback snapshotをcleanupする

#### Scenario: DataStore rollbackが失敗する
- **WHEN** DB rollbackは成功したがDataStore snapshotのreadまたはwrite-backが失敗する
- **THEN** システムは`ROLLBACK_REQUIRED`と全rollback artifactを保持し、次回起動でDB/DataStore rollbackを再試行する

#### Scenario: Retryでrollbackが完了する
- **WHEN** 前回未完了だったDB/DataStore rollbackが次回起動で両方成功する
- **THEN** システムはfailure resultを確定し、pending directoryをcleanupする

#### Scenario: Restoreが正常完了する
- **WHEN** DB migration validationとDataStore反映が成功してCOMPLETED cleanupへ進む
- **THEN** システムはsuccess resultを記録した後、DataStore rollback snapshotを含むpending directoryをcleanupする

### Requirement: Snapshot欠損のlegacy pending状態を保全する
システムはDataStore反映済みの可能性があるpending markerにdurable DataStore snapshotがない場合、DB-only rollbackを完全rollbackとして実行してartifactを削除してはならない（MUST NOT）。

#### Scenario: Legacy DB_SWAPPEDにsnapshotがない
- **WHEN** 更新前versionが残したDB_SWAPPED markerにdurable DataStore snapshotが存在しない
- **THEN** システムはlive DB、DB rollback snapshot、staged DataStore JSONを保持し、完全rollback不能をfailure resultへ記録する

#### Scenario: Legacy MIGRATION_PENDINGにsnapshotがない
- **WHEN** snapshotなしのMIGRATION_PENDINGでDB rollbackが必要になる
- **THEN** システムはDBだけをrollbackしてcleanupせず、manual recoveryに必要なartifactを保持する

