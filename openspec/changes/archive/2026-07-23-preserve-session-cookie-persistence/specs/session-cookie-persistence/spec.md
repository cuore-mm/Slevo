## ADDED Requirements

### Requirement: Backup restore shall preserve session cookie persistence state
システムは backup JSON の `BackupCookieItem.persistent` に基づいて OkHttp `Cookie.persistent` を復元しなければならない（MUST）。`BackupCookieItem.persistent == false` の Cookie は session cookie として復元し、復元時に OkHttp `Cookie.Builder.expiresAt()` を呼んで persistent cookie へ変化させてはならない（MUST NOT）。

#### Scenario: Session backup cookie remains session cookie
- **WHEN** `BackupCookieItem.persistent == false` の Cookie item を `BackupRestoreMapper.toCookie()` で復元する
- **THEN** 復元された OkHttp `Cookie.persistent` は `false` である
- **AND** 復元された Cookie の `name`、`value`、`domain`、`path`、`secure`、`httpOnly`、`hostOnly` は backup item と一致する

#### Scenario: Persistent backup cookie remains persistent cookie
- **WHEN** `BackupCookieItem.persistent == true` かつ finite な `expiresAt` を持つ Cookie item を `BackupRestoreMapper.toCookie()` で復元する
- **THEN** 復元された OkHttp `Cookie.persistent` は `true` である
- **AND** 復元された Cookie の `expiresAt` は backup item の `expiresAt` と一致する

#### Scenario: Persistent flag takes precedence over expiresAt for session item
- **WHEN** `BackupCookieItem.persistent == false` かつ finite な `expiresAt` を持つ Cookie item を `BackupRestoreMapper.toCookie()` で復元する
- **THEN** 復元された OkHttp `Cookie.persistent` は `false` である
- **AND** システムは session cookie として復元するために `expiresAt` より `persistent` を優先する

### Requirement: Cookie DataStore persistence shall store persistent state explicitly
Cookie の DataStore 保存形式は、OkHttp `Cookie.persistent` を明示的な field として保存しなければならない（MUST）。新規に serialize される Cookie record は `name|value|expiresAt|domain|path|secure|httpOnly|hostOnly|persistent` の 9 field 形式でなければならない（MUST）。

#### Scenario: New DataStore format preserves session cookie
- **WHEN** `persistent == false` の OkHttp `Cookie` を DataStore Cookie adapter で serialize し、同じ adapter で deserialize する
- **THEN** 復元された OkHttp `Cookie.persistent` は `false` である
- **AND** 復元された Cookie の `name`、`value`、`domain`、`path`、`secure`、`httpOnly`、`hostOnly` は元の Cookie と一致する

#### Scenario: New DataStore format preserves persistent cookie
- **WHEN** `persistent == true` かつ finite な `expiresAt` を持つ OkHttp `Cookie` を DataStore Cookie adapter で serialize し、同じ adapter で deserialize する
- **THEN** 復元された OkHttp `Cookie.persistent` は `true` である
- **AND** 復元された Cookie の `expiresAt` は元の Cookie と一致する

#### Scenario: Serialized DataStore cookie contains persistent field
- **WHEN** OkHttp `Cookie` を DataStore Cookie adapter で serialize する
- **THEN** serialized Cookie record は 9 field を持つ
- **AND** 9 番目の field は元の Cookie の `persistent` と一致する boolean string である

### Requirement: Legacy Cookie DataStore records shall remain readable
システムは `persistent` field を持たない既存 Cookie DataStore record を引き続き読み込めなければならない（MUST）。legacy 7 field record は domain-scoped persistent cookie として扱い、legacy 8 field record は保存済み `hostOnly` を維持した persistent cookie として扱わなければならない（MUST）。

#### Scenario: Legacy seven-field record remains readable
- **WHEN** `name|value|expiresAt|domain|path|secure|httpOnly` の 7 field Cookie record を DataStore Cookie adapter で deserialize する
- **THEN** 復元された OkHttp `Cookie.hostOnly` は `false` である
- **AND** 復元された OkHttp `Cookie.persistent` は `true` である
- **AND** 復元された Cookie の `name`、`value`、`domain`、`path`、`secure`、`httpOnly` は record の値と一致する

#### Scenario: Legacy eight-field record remains readable
- **WHEN** `name|value|expiresAt|domain|path|secure|httpOnly|hostOnly` の 8 field Cookie record を DataStore Cookie adapter で deserialize する
- **THEN** 復元された OkHttp `Cookie.hostOnly` は 8 番目の field と一致する
- **AND** 復元された OkHttp `Cookie.persistent` は `true` である
- **AND** 復元された Cookie の `name`、`value`、`domain`、`path`、`secure`、`httpOnly` は record の値と一致する

#### Scenario: Invalid DataStore cookie record is ignored
- **WHEN** field 数が 7 未満、`expiresAt` が数値変換できない、または OkHttp `Cookie.Builder` が reject する Cookie record を DataStore Cookie adapter で deserialize する
- **THEN** adapter は `null` を返す

### Requirement: Pending restore shall prepare session cookies without changing persistence state
pending restore の Cookie pre-validation と DataStore 書き込みは、backup item の session/persistent 状態を維持した Cookie record を準備しなければならない（MUST）。Cookie pre-validation は `persistent == false` の valid session cookie を失敗扱いにしてはならない（MUST NOT）。

#### Scenario: Pending restore prepares session cookie record
- **WHEN** staged `datastore/cookies.json` に `persistent == false` の valid `BackupCookieItem` が含まれる
- **THEN** Cookie pre-validation は success を返す
- **AND** prepared Cookie record を DataStore Cookie adapter で deserialize すると OkHttp `Cookie.persistent` は `false` である

#### Scenario: Pending restore prepares persistent cookie record
- **WHEN** staged `datastore/cookies.json` に `persistent == true` かつ finite な `expiresAt` を持つ valid `BackupCookieItem` が含まれる
- **THEN** Cookie pre-validation は success を返す
- **AND** prepared Cookie record を DataStore Cookie adapter で deserialize すると OkHttp `Cookie.persistent` は `true` である
- **AND** 復元された Cookie の `expiresAt` は backup item の `expiresAt` と一致する
