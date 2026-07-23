# pending-restore-marker-durability Specification

## Purpose
TBD - created by archiving change make-pending-restore-marker-atomic. Update Purpose after archive.
## Requirements
### Requirement: Pending restore markerをatomicに確定する
システムはpending restore markerの初回作成および状態更新をatomicに確定し、書き込みが完了する前の内容を有効なrestore stateとして公開してはならない（MUST NOT）。

#### Scenario: Markerを初めて正常に作成する
- **WHEN** 検証済みrestore stagingの準備後に`PREPARED` markerの書き込みが正常終了する
- **THEN** システムは完全なmarker JSONを確定し、次回の読み取りで`PREPARED`状態を返す

#### Scenario: 既存markerを正常に更新する
- **WHEN** システムが確定済みmarkerを次のrestore statusへ更新し、atomic writeが正常終了する
- **THEN** 次回の読み取りは完全な新markerだけを返し、旧markerまたはpartial JSONを返さない

#### Scenario: 初回marker作成が中断される
- **WHEN** 有効markerが存在しない状態で初回marker writeが確定前に中断される
- **THEN** システムはpartial JSONを有効markerとして返さず、未確定のrestore stateを実行しない

### Requirement: Marker更新失敗時に直前の確定状態を保持する
システムは確定済みmarkerの更新中にprocess終了またはI/O failureが発生した場合、直前に確定したmarkerを保持または回復しなければならない（MUST）。

#### Scenario: 更新途中にprocessが終了する
- **WHEN** 確定済みmarkerが存在し、新markerの書き込み開始後かつ確定前にprocessが終了する
- **THEN** 次回起動時のmarker読み取りは直前に確定した完全なmarkerを回復する

#### Scenario: 更新時のI/O failureを処理する
- **WHEN** marker更新中にfilesystem writeまたは同期が失敗する
- **THEN** システムは不完全な新markerを確定せず、直前のmarkerを維持してfailureをcallerへ伝播する

### Requirement: 全marker I/O経路で同一のdurability contractを使用する
システムはrestore準備、起動時適用、migration完了確認を含む全production marker read/write pathで同じatomic publicationとrecovery contractを使用しなければならない（MUST）。

#### Scenario: Restore準備でmarkerを作成する
- **WHEN** `PendingRestoreManager`がstaging完了後にmarkerを作成する
- **THEN** markerは起動時applierが使用するmarker file storeと同じatomic protocolで確定される

#### Scenario: 起動時にmarker statusを更新する
- **WHEN** pending restore applierまたはcompletion checkerがmarker statusを更新する
- **THEN** 更新は同じatomic protocolで確定され、更新失敗が後続state処理へ成功として渡らない

#### Scenario: 未確定new fileまたはlegacy backupだけが残る中断状態を読み取る
- **WHEN** 前回のmarker更新中断により`.new`またはlegacy `.bak`が残っている
- **THEN** 全production readerはbase fileの存在確認だけで終了せず、`.new`を有効markerとして公開せず、legacy `.bak`からは直前の有効markerを回復する

### Requirement: Markerの互換性とcleanup範囲を維持する
システムはmarkerのpath、JSON schema、restore statusを変更せず、pending cleanupでmarker本体とatomic publication用artifactを削除しなければならない（MUST）。

#### Scenario: 既存markerを読み取る
- **WHEN** 更新前versionが作成したbackup artifactなしの有効な`restore.json`が存在する
- **THEN** システムはdata migrationなしでmarkerを読み取る

#### Scenario: Pending restoreをcleanupする
- **WHEN** システムがpending restore directoryをcleanupする
- **THEN** marker本体とatomic writeが使用するbackup artifactは削除され、pending directory外のquarantine artifactは削除されない

