## MODIFIED Requirements

### Requirement: expected table set の source of truth
システムは pre-migration validation で使う version-aware expected application table set を、exported Room schema v2-v10 に基づいて管理しなければならない（MUST）。

#### Scenario: exported schema が存在しない version を対象外にする
- **WHEN** バックアップ DB version に対応する exported Room schema 由来の expected table set が定義されていない
- **THEN** システムはその backup DB version を復元対象外として拒否する

#### Scenario: v10のexpected table setを検証する
- **WHEN** バックアップDB versionがv10である
- **THEN** システムは `pending_own_posts` を含むexported Room schema v10由来のapplication table setを要求する

#### Scenario: v2-v9のhistorical table setを維持する
- **WHEN** バックアップDB versionがv2以上v9以下である
- **THEN** システムは各versionの既存exported Room schema由来のtable setを使用し、`pending_own_posts` の不足だけを理由に拒否しない

#### Scenario: expected table set の変更をテストで検出する
- **WHEN** exported Room schema の application table set と実装内の expected table set が乖離する
- **THEN** 自動テストは pre-migration validation の table set 定義不整合として失敗する
