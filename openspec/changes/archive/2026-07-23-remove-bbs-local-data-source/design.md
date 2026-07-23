## Context

現在の BBS サービス関連データアクセスは `BbsServiceRepository` → `BbsLocalDataSource` → `BbsLocalDataSourceImpl` → DAO という構造になっている。一方で、既存の多くの Repository は DAO を直接 constructor injection しており、`BbsLocalDataSource` は他の DataStore local data source と異なり Room DAO の薄いラッパーとして機能している。

後続の `add-database-write-gate` では `BbsServiceRepository.addOrUpdateService` を write boundary として扱う予定であり、`BbsLocalDataSourceImpl` を残したまま gate を導入すると public wrapper / ungated helper の分岐が必要になる。この変更では gate 導入前に BBS local data source 層を取り除き、後続変更の二重 gate リスクとレビュー範囲を減らす。

## Goals / Non-Goals

**Goals:**

- `BbsLocalDataSource` / `BbsLocalDataSourceImpl` を廃止する。
- `BbsServiceRepository` が `BbsServiceDao`、`CategoryDao`、`BoardDao`、`BoardCategoryCrossRefDao` を直接使う構造にする。
- `BbsLocalDataSourceImpl` の非自明な処理を `BbsServiceRepository` の private helper に移し、既存挙動を保持する。
- `DataSourceModule.bindBbsLocalDataSource` を削除する。

**Non-Goals:**

- `DatabaseWriteGate` や `withWritePermit` は追加しない。
- `ThreadStateRepository` の ungated helper は追加しない。
- Room schema、DAO method、DataStore schema、UI、ネットワーク処理は変更しない。
- BBS データ更新の transaction / gate 方針はこの変更では変更しない。

## Decisions

### 1. `BbsServiceRepository` を BBS data access boundary にする

`BbsServiceRepository` の constructor は `BbsLocalDataSource` ではなく、以下の DAO を直接受け取る。

- `BbsServiceDao`
- `CategoryDao`
- `BoardDao`
- `BoardCategoryCrossRefDao`

`BbsServiceRepository` は既存の public API を保つ。既存 ViewModel / callback / session store から見える repository API は変更しない。

### 2. `BbsLocalDataSourceImpl` の非自明処理を private helper に移す

以下の処理は `BbsServiceRepository` の private helper として移植する。

- service upsert:
  - domain で既存 service を検索する。
  - 未登録なら insert する。
  - 登録済みで display name または menu URL が変わっている場合のみ update する。
- board insert-or-lookup:
  - board insert を試みる。
  - conflict を示す戻り値の場合は URL で既存 board を取得し、その ID を後続処理に使う。
- category board Flow 合成:
  - category の board ID Flow と board DAO の Flow を合成する。
  - 既存 UI が受け取る board list の形を変えない。

### 3. 書き込みフローの既存順序を保つ

`addOrUpdateService` は既存と同じ論理順序を保つ。

1. service を insert/update し service ID を確定する。
2. 既存カテゴリ、板、カテゴリ紐付けを既存実装と同等の順序でクリアする。
3. 入力カテゴリを登録する。
4. 入力 board を insert-or-lookup する。
5. board/category cross-ref を登録する。

cleanup と再登録の正確な DAO 呼び出し順序は `[requires source inspection]` として実装前に確認する。確認後は既存順序を保持し、順序を変える必要がある場合はこの OpenSpec を更新してから実装する。

`removeService` は既存と同じ DAO 削除処理を実行する。

### 4. DI cleanup

`DataSourceModule.bindBbsLocalDataSource` を削除する。`BbsLocalDataSource` / `BbsLocalDataSourceImpl` の import と binding が残らないことを確認する。

## Risks / Trade-offs

- [Risk] 移植時に service upsert や board conflict fallback の挙動が変わる。 → helper ごとに既存条件を design/tasks に明記し、必要に応じて fake DAO test で確認する。
- [Risk] `BbsLocalDataSource` の利用者が他にも存在する。 → 実装前に利用箇所を検索し、`BbsServiceRepository` 以外が見つかった場合は同じ変更内で移行方法を明記してから進める。
- [Risk] 後続の `add-database-write-gate` と責務が重複する。 → この変更では gate を追加せず、純粋な構造整理に限定する。

## Migration Plan

1. `BbsLocalDataSource` / `BbsLocalDataSourceImpl` の利用箇所を確認する。
2. `BbsServiceRepository` の constructor を DAO 直接注入に変更する。
3. `BbsLocalDataSourceImpl` の非自明処理を private helper として `BbsServiceRepository` へ移す。
4. `BbsServiceRepository` の既存 public API と呼び出し元の compile compatibility を確認する。
5. `DataSourceModule.bindBbsLocalDataSource`、`BbsLocalDataSource`、`BbsLocalDataSourceImpl` を削除する。
6. BBS service 更新・削除・category board observe の挙動保持を unit test または fake DAO test / checklist で確認する。
7. CI build/test workflow を実行する。

## Testing Strategy

- Unit / fake DAO test:
  - service 未登録時に insert されること。
  - service 登録済みで display name または menu URL が変わった場合のみ update されること。
  - board insert が conflict を返した場合に URL lookup の ID を使うこと。
  - `addOrUpdateService` が category、board、cross-ref を既存順序で再登録すること。
  - `removeService` が service 削除 DAO を呼ぶこと。
  - category board Flow 合成が既存 UI 向けの board list を返すこと。
- Static/checklist:
  - `BbsLocalDataSource` / `BbsLocalDataSourceImpl` / `DataSourceModule.bindBbsLocalDataSource` が残っていないこと。
  - `DatabaseWriteGate` / `withWritePermit` を追加していないこと。
- CI:
  - 実装時に `gh workflow list` またはリポジトリの CI 定義で build/test workflow 名を確認し、GitHub Actions の該当 workflow を実行する。workflow 名を確認できない場合は停止して報告する。

## Open Questions

- なし。実装前の source inspection で `BbsLocalDataSource` の追加利用者が見つかった場合は、この design を更新してから実装する。
