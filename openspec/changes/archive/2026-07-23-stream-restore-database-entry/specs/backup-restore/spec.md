## ADDED Requirements

### Requirement: 復元DB entry の streaming 読み込み
システムは復元 preview および復元確定時に、バックアップ ZIP 内の `database/slevo.db` を heap 上の単一 `ByteArray` として保持せず、一時ファイルへ stream して扱わなければならない（MUST）。

#### Scenario: 大きいDB entryを一時ファイルへ読み込む
- **WHEN** システムが ZIP 内の `database/slevo.db` entry を読み込む
- **THEN** システムは `ZipInputStream.readBytes()` などで DB 全体を heap に保持せず、一時ファイルへ sequential に書き込む

#### Scenario: JSON entryはmetadataとして読み込む
- **WHEN** システムが `manifest.json`、`datastore/settings.json`、`datastore/tabs.json`、または `datastore/cookies.json` を読み込む
- **THEN** システムはそれらを DataStore/manifest metadata として parse し、DB file payload と同じ大容量 binary として扱わない

#### Scenario: DB validationはstream済み一時ファイルに対して実行する
- **WHEN** システムが復元対象 DB の integrity と schema compatibility を検証する
- **THEN** システムは ZIP から stream して作成した一時 DB file を読み取り専用で検証する

#### Scenario: pending restoreは一時DB fileからstagingする
- **WHEN** システムが復元確定後に pending restore を作成する
- **THEN** システムは検証済み一時 DB file を pending restore directory の `database/slevo.db` へ move または copy し、DB 全体を heap に再読み込みしない

#### Scenario: pending restore準備中の失敗は部分stagingを削除する
- **WHEN** pending restore directory へ DB を staging した後、integrity check、DataStore JSON staging、または marker 作成が失敗する
- **THEN** システムは pending restore を作成済みとして扱わず、部分的に作成された pending restore directory を削除する

#### Scenario: previewのみの場合は一時DB fileを削除する
- **WHEN** システムが復元 preview の検証に成功し、UI へ preview 結果を返す
- **THEN** システムは preview 表示に不要な一時 DB file を処理完了後に削除する

#### Scenario: 検証失敗時は一時DB fileを削除する
- **WHEN** ZIP 読み込み後の manifest、DB、または DataStore JSON validation が失敗する
- **THEN** システムは pending restore を作成せず、作成済みの一時 DB file を best-effort で削除する

#### Scenario: DB stream読み込み失敗時は一時DB fileを削除する
- **WHEN** ZIP 内の `database/slevo.db` を一時ファイルへ stream している途中で I/O 例外または malformed ZIP read failure が発生する
- **THEN** システムは pending restore を作成せず、作成途中の一時 DB file を best-effort で削除する

#### Scenario: commit時再検証でもstreamingを使う
- **WHEN** preview 成功後にユーザーが復元を確定する
- **THEN** システムは同じ URI の ZIP を再読み込みして再検証する際にも、`database/slevo.db` を一時ファイルへ stream して扱う
