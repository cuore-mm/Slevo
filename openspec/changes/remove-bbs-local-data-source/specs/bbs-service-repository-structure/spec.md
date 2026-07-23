## ADDED Requirements

### Requirement: BBS local data source 層の廃止
システムは BBS サービス関連の Room DAO アクセスを `BbsServiceRepository` に集約し、`BbsLocalDataSource` / `BbsLocalDataSourceImpl` を経由してはならない（MUST）。

#### Scenario: BBS Repository が DAO を直接利用する
- **WHEN** `BbsServiceRepository` が BBS サービス、カテゴリ、板、またはカテゴリ紐付けを読み書きする
- **THEN** システムは `BbsServiceDao`、`CategoryDao`、`BoardDao`、`BoardCategoryCrossRefDao` を直接利用する

#### Scenario: BBS local data source binding が残らない
- **WHEN** DI graph が構築される
- **THEN** システムは `BbsLocalDataSource` / `BbsLocalDataSourceImpl` の Hilt binding を要求しない

### Requirement: BBS サービス更新挙動の保持
システムは `BbsLocalDataSourceImpl` 廃止後も、BBS サービス更新、板登録、カテゴリ紐付け、カテゴリ配下 board 表示の既存挙動を保持しなければならない（MUST）。

#### Scenario: service upsert の挙動保持
- **WHEN** BBS service が未登録の domain で追加される
- **THEN** システムは service を insert する
- **WHEN** BBS service が登録済みで display name または menu URL が変更されている
- **THEN** システムは既存 service を update する

#### Scenario: service upsert で変更がない場合
- **WHEN** BBS service が登録済みで display name と menu URL がどちらも変更されていない
- **THEN** システムは既存 service を update しない

#### Scenario: board insert conflict 時の fallback
- **WHEN** board insert が conflict を示す結果を返す
- **THEN** システムは board URL で既存 board を取得し、その ID をカテゴリ紐付けに利用する

#### Scenario: カテゴリと板の再登録
- **WHEN** `BbsServiceRepository.addOrUpdateService` がカテゴリと板を含む service を更新する
- **THEN** システムは既存カテゴリ、板、カテゴリ紐付けを整理し、入力カテゴリ、板、カテゴリ紐付けを既存と同等の順序で再登録する

#### Scenario: カテゴリ配下 board Flow の保持
- **WHEN** カテゴリ配下の board 一覧を observe する
- **THEN** システムは category の board ID Flow と board DAO の Flow を合成し、既存 UI が期待する board list を返す

### Requirement: Gate 導入を含めない
システムはこの変更で `DatabaseWriteGate` または `withWritePermit` を導入してはならない（MUST NOT）。

#### Scenario: Gate なしの構造整理
- **WHEN** BBS local data source 層を廃止する
- **THEN** システムは `DatabaseWriteGate`、`withWritePermit`、`ThreadStateRepository` の ungated helper を追加しない
