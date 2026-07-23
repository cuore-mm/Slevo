## Why

`BbsLocalDataSource` / `BbsLocalDataSourceImpl` は実質的に `BbsServiceRepository` からのみ利用される薄い DAO ラッパーであり、`add-database-write-gate` で write boundary を整理する際に二重 gate の候補になっている。`DatabaseWriteGate` 導入とは独立した構造整理として先に廃止し、後続の gate 変更を小さくする。

## What Changes

- `BbsLocalDataSource` interface と `BbsLocalDataSourceImpl` を廃止する。
- `BbsServiceRepository` は `BbsServiceDao`、`CategoryDao`、`BoardDao`、`BoardCategoryCrossRefDao` を直接 constructor injection する。
- `BbsLocalDataSourceImpl` にあった service upsert、board insert-or-lookup、category board Flow 合成の非自明処理を `BbsServiceRepository` の private helper へ移す。
- `DataSourceModule.bindBbsLocalDataSource` を削除する。
- 既存の BBS サービス、カテゴリ、板、カテゴリ紐付けのユーザー向け挙動と Room schema / DAO API は変更しない。
- `DatabaseWriteGate`、`withWritePermit`、`ThreadStateRepository` の ungated helper はこの変更では実装しない。

## Capabilities

### New Capabilities

- `bbs-service-repository-structure`: BBS サービス更新の data access boundary を `BbsServiceRepository` に集約し、不要な local data source 層を取り除く構造整理。

### Modified Capabilities

- なし

## Impact

- 影響範囲:
  - `app/src/main/java/com/websarva/wings/android/slevo/data/repository/BbsServiceRepository.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/BbsLocalDataSource.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/impl/BbsLocalDataSourceImpl.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/di/DataSourceModule.kt`
  - 必要に応じた `BbsServiceRepository` の unit test / fake DAO test
- Hilt binding から BBS local data source binding を削除する。
- Room schema、DAO query、DataStore schema、ネットワーク処理、画面 UI は変更しない。
