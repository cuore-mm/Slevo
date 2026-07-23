# backup-database-prevalidation Specification

## Purpose
TBD - created by archiving change harden-backup-prevalidation. Update Purpose after archive.
## Requirements
### Requirement: 対応最小 DB version の引き上げ
システムはバックアップ復元で受け付ける最小 Room DB version を v2 とし、v1 以下のバックアップを復元候補として受け付けてはならない（MUST NOT）。

#### Scenario: manifest version が v1 のバックアップを拒否する
- **WHEN** ユーザーが `manifest.databaseVersion = 1` のバックアップ ZIP を選択する
- **THEN** システムは復元準備を開始せず、現在のアプリでは復元できないバックアップとして扱う

#### Scenario: DB file の user_version が v1 のバックアップを拒否する
- **WHEN** `database/slevo.db` の `PRAGMA user_version` が 1 のバックアップを pre-migration validation する
- **THEN** システムは pending restore を作成せず、too-old DB version として拒否する

#### Scenario: v2 のバックアップは対応範囲として扱う
- **WHEN** `manifest.databaseVersion` と `database/slevo.db` の `PRAGMA user_version` が 2 で、現在 version までの migration path が存在する
- **THEN** システムは v2 であることだけを理由に復元候補から除外しない

### Requirement: current version DB の strict pre-validation
システムは現在 Room DB version と同じ `database/slevo.db` を pending restore として保存または適用する前に、post-migration validation と同等の strict validation を実行しなければならない（MUST）。

#### Scenario: current version の identity hash 不一致を拒否する
- **WHEN** `database/slevo.db` の `PRAGMA user_version` が現在 Room DB version と一致するが、`room_master_table` の identity hash が現在 schema と一致しない
- **THEN** システムは pending restore を作成せず、無効なバックアップとして拒否する

#### Scenario: current version の必須 table 不足を拒否する
- **WHEN** `database/slevo.db` の `PRAGMA user_version` が現在 Room DB version と一致するが、現在 schema の必須 application table が不足している
- **THEN** システムは pending restore を作成せず、無効なバックアップとして拒否する

#### Scenario: current version の strict validation 成功を受け付ける
- **WHEN** `database/slevo.db` の `PRAGMA user_version`、SQLite integrity、現在 identity hash、現在必須 application table がすべて現在 schema と一致する
- **THEN** システムは current version DB の pre-migration validation を成功として扱う

### Requirement: 古い DB version の historical table sanity check
システムは現在 Room DB version より古い v2 以上の `database/slevo.db` を pending restore として保存または適用する前に、その DB version に対応する Slevo application table set が存在することを確認しなければならない（MUST）。

#### Scenario: 古い DB version の expected table が存在する場合に受け付ける
- **WHEN** `database/slevo.db` の `PRAGMA user_version` が v2 以上かつ現在 Room DB version 未満で、SQLite integrity、manifest version 一致、migration path validation、対象 version の expected application table check がすべて成功する
- **THEN** システムはその DB を pre-migration validation 成功として扱う

#### Scenario: 古い DB version の expected table 不足を拒否する
- **WHEN** `database/slevo.db` の `PRAGMA user_version` が v2 以上かつ現在 Room DB version 未満だが、対象 version の expected application table が 1 つ以上不足している
- **THEN** システムは pending restore を作成せず、無効なバックアップとして拒否する

#### Scenario: non-Slevo SQLite file を拒否する
- **WHEN** SQLite integrity と `PRAGMA user_version` は有効だが、Slevo の対象 version に必要な application table set を持たない SQLite file がバックアップ ZIP に含まれる
- **THEN** システムは DB swap を行わず、無効なバックアップとして拒否する

#### Scenario: 古い DB version に current identity hash を要求しない
- **WHEN** `database/slevo.db` の `PRAGMA user_version` が v2 以上かつ現在 Room DB version 未満で、対象 version の expected application table check が成功する
- **THEN** システムは現在 Room schema の identity hash と一致しないことだけを理由に pending restore を拒否しない

#### Scenario: 古い DB version に current-only table を要求しない
- **WHEN** `database/slevo.db` の `PRAGMA user_version` が v2、v3、v4、または v5 で、対象 version より後の migration で追加された table が存在しない
- **THEN** システムは対象 version の expected application table が揃っている限り、その後追加された current-only table の不足だけを理由に pending restore を拒否しない

### Requirement: expected table set の source of truth
システムは pre-migration validation で使う version-aware expected application table set を、exported Room schema v2-v9 に基づいて管理しなければならない（MUST）。

#### Scenario: exported schema が存在しない version を対象外にする
- **WHEN** バックアップ DB version に対応する exported Room schema 由来の expected table set が定義されていない
- **THEN** システムはその backup DB version を復元対象外として拒否する

#### Scenario: expected table set の変更をテストで検出する
- **WHEN** exported Room schema の application table set と実装内の expected table set が乖離する
- **THEN** 自動テストは pre-migration validation の table set 定義不整合として失敗する

