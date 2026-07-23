# cookie-scope-preservation Specification

## Purpose
TBD - created by archiving change preserve-cookie-scope-on-restore. Update Purpose after archive.
## Requirements
### Requirement: Cookie restore shall preserve domain scope
バックアップ復元機能は、`BackupCookieItem.hostOnly` に基づいて OkHttp `Cookie` の domain scope を復元しなければならない。The system SHALL restore `hostOnly=true` の Cookie as host-only Cookie and `hostOnly=false` の Cookie as domain-scoped Cookie.

#### Scenario: Host-only Cookie is restored as host-only
- **WHEN** `BackupCookieItem.hostOnly` が `true` の Cookie を復元する
- **THEN** 復元された OkHttp `Cookie` の `hostOnly` は `true` である
- **AND** 復元された Cookie の `domain`、`path`、`name`、`value`、`expiresAt`、`secure`、`httpOnly` は backup item の値と一致する

#### Scenario: Domain-scoped Cookie is restored as domain-scoped
- **WHEN** `BackupCookieItem.hostOnly` が `false` の Cookie を復元する
- **THEN** 復元された OkHttp `Cookie` の `hostOnly` は `false` である
- **AND** 復元された Cookie の `domain`、`path`、`name`、`value`、`expiresAt`、`secure`、`httpOnly` は backup item の値と一致する

### Requirement: Cookie DataStore persistence shall preserve host-only scope
Cookie の DataStore 保存形式は、OkHttp `Cookie.hostOnly` を保存しなければならない。The system SHALL restore DataStore persisted Cookie records with the same host-only/domain-scoped scope that was serialized.

#### Scenario: New Cookie persistence format preserves host-only Cookie
- **WHEN** `hostOnly=true` の OkHttp `Cookie` を DataStore 保存用 JSON adapter で serialize し、同じ adapter で deserialize する
- **THEN** 復元された Cookie の `hostOnly` は `true` である
- **AND** 復元された Cookie の `domain`、`path`、`name`、`value`、`expiresAt`、`secure`、`httpOnly` は元の Cookie と一致する

#### Scenario: New Cookie persistence format preserves domain-scoped Cookie
- **WHEN** `hostOnly=false` の OkHttp `Cookie` を DataStore 保存用 JSON adapter で serialize し、同じ adapter で deserialize する
- **THEN** 復元された Cookie の `hostOnly` は `false` である
- **AND** 復元された Cookie の `domain`、`path`、`name`、`value`、`expiresAt`、`secure`、`httpOnly` は元の Cookie と一致する

### Requirement: Legacy Cookie persistence format shall remain readable
Cookie の DataStore 読み込み処理は、既存ユーザーの DataStore に保存済みの旧 7 field 形式を読み込めなければならない。The system MUST treat legacy records without `hostOnly` as domain-scoped Cookie records for backward compatibility.

#### Scenario: Legacy seven-field Cookie string is read as domain-scoped
- **WHEN** `name|value|expiresAt|domain|path|secure|httpOnly` の 7 field Cookie 文字列を読み込む
- **THEN** 復元された Cookie の `hostOnly` は `false` である
- **AND** 復元された Cookie の `domain`、`path`、`name`、`value`、`expiresAt`、`secure`、`httpOnly` は旧形式文字列の値と一致する

#### Scenario: Invalid Cookie persistence string is ignored
- **WHEN** field 数が不足している、または `expiresAt` を数値に変換できない Cookie 文字列を読み込む
- **THEN** Cookie adapter は `null` を返し、不正 Cookie を復元対象から除外できる

### Requirement: Cookie persistence shall preserve session and persistent state through expiresAt
Cookie の backup/restore と DataStore persistence は、OkHttp `Cookie.expiresAt` を維持しなければならない。The system SHALL preserve session/persistent state through the restored `expiresAt` value.

#### Scenario: Session Cookie remains session Cookie
- **WHEN** `expiresAt == Long.MAX_VALUE` の Cookie を backup/restore または DataStore persistence 経路で round-trip する
- **THEN** 復元された Cookie の `expiresAt` は `Long.MAX_VALUE` である
- **AND** 復元された Cookie の `persistent` は `false` である

#### Scenario: Persistent Cookie remains persistent Cookie
- **WHEN** `expiresAt` が `Long.MAX_VALUE` 以外の Cookie を backup/restore または DataStore persistence 経路で round-trip する
- **THEN** 復元された Cookie の `expiresAt` は元の値と一致する
- **AND** 復元された Cookie の `persistent` は `true` である

### Requirement: Pending restore shall write scope-preserving Cookie records
pending restore の DataStore 反映処理は、backup に含まれる Cookie scope を維持した DataStore Cookie record を書き込まなければならない。The system SHALL write Cookie records that preserve the backup item's host-only/domain-scoped scope.

#### Scenario: Pending restore writes host-only Cookie as host-only record
- **WHEN** pending restore が `hostOnly=true` の `BackupCookieItem` を含む `datastore/cookies.json` を DataStore に反映する
- **THEN** DataStore に書き込まれた Cookie record を Cookie adapter で読み戻すと、復元された Cookie の `hostOnly` は `true` である

#### Scenario: Pending restore writes domain-scoped Cookie as domain-scoped record
- **WHEN** pending restore が `hostOnly=false` の `BackupCookieItem` を含む `datastore/cookies.json` を DataStore に反映する
- **THEN** DataStore に書き込まれた Cookie record を Cookie adapter で読み戻すと、復元された Cookie の `hostOnly` は `false` である

