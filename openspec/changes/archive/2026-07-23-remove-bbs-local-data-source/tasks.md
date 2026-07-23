## 1. 事前確認

- [x] 1.1 `BbsLocalDataSource` / `BbsLocalDataSourceImpl` の利用箇所を検索する。完了条件: `BbsServiceRepository` 以外の利用がある場合は移行方法を明記し、ない場合は廃止対象として確定している。
- [x] 1.2 BBS 関連 DAO と DI cleanup 対象を確認する。完了条件: `BbsServiceDao`、`CategoryDao`、`BoardDao`、`BoardCategoryCrossRefDao` を `BbsServiceRepository` に直接注入すること、`DataSourceModule.bindBbsLocalDataSource` を削除することが確認済みである。

## 2. `BbsServiceRepository` への移行

- [x] 2.1 `BbsServiceRepository` の constructor を DAO 直接注入へ変更する。完了条件: `BbsLocalDataSource` 依存がなくなり、`BbsServiceDao`、`CategoryDao`、`BoardDao`、`BoardCategoryCrossRefDao` を利用できる。
- [x] 2.2 `BbsLocalDataSourceImpl.upsertService` 相当の処理を `BbsServiceRepository` の private helper へ移す。完了条件: domain で既存 service を検索し、未登録なら insert、display name または menu URL 変更時のみ update する。
- [x] 2.3 `BbsLocalDataSourceImpl.insertOrGetBoard` 相当の処理を `BbsServiceRepository` の private helper へ移す。完了条件: board insert conflict 時に URL lookup で既存 board ID を取得し、カテゴリ紐付けに利用できる。
- [x] 2.4 `BbsLocalDataSourceImpl.observeBoardsForCategory` 相当の処理を `BbsServiceRepository` へ移す。完了条件: category の board ID Flow と board DAO Flow を合成し、既存 UI 向け board list を返す。
- [x] 2.5 `[requires source inspection]` `addOrUpdateService` と `removeService` の既存 public API と DAO 呼び出し順序を確認し、挙動を維持する。完了条件: 呼び出し元の public method signature を変更せず、既存の service/category/board/cross-ref cleanup と再登録順序を保っている。

## 3. 旧 local data source の削除

- [x] 3.1 `DataSourceModule.bindBbsLocalDataSource` と関連 import を削除する。完了条件: Hilt module に BBS local data source binding が残っていない。
- [x] 3.2 `BbsLocalDataSource.kt` と `BbsLocalDataSourceImpl.kt` を削除する。完了条件: 未使用 interface/implementation が残っていない。
- [x] 3.3 `DatabaseWriteGate`、`withWritePermit`、`ThreadStateRepository` ungated helper を追加していないことを確認する。完了条件: この変更が BBS 層整理だけに限定されている。

## 4. テストと検証

- [x] 4.1 `BbsServiceRepository` の unit test または fake DAO test を追加/更新し、service insert/update 条件を検証する。完了条件: 未登録 insert、変更時 update、変更なし update なしを確認できる。
- [x] 4.2 board insert conflict fallback の直接テストは `add-database-write-gate` 側へ送る。完了条件: `addOrUpdateService` が `boardDao.clearForService(svcId)` 後に board を再登録するため、public flow からは conflict fallback が通常到達不能であることを確認し、helper 直呼びの単体テストは `internal` visibility を設計する `add-database-write-gate` に委ねる。
- [x] 4.3 `BbsServiceRepository` の unit test または fake DAO test で category/board/cross-ref 再登録を検証する。完了条件: `addOrUpdateService` の主要な DAO 呼び出しと順序を確認できる。
- [x] 4.4 category board Flow 合成の挙動を unit test、fake DAO test、または明示的な手動確認観点として記録する。完了条件: 既存 UI が受け取る board list の形を保つ確認が残っている。
- [x] 4.5 static/checklist で `BbsLocalDataSource` / `BbsLocalDataSourceImpl` / `DataSourceModule.bindBbsLocalDataSource` が残っていないことを確認する。完了条件: 削除対象の参照がなく、BBS Repository が DAO 直接注入になっている。

## 5. 仕上げ

- [x] 5.1 新規/移動した class や非自明関数に必要な KDoc / コメントがあることを確認する。完了条件: リポジトリのコメント規約に違反しない。
- [x] 5.2 GitHub Actions の build/test workflow を実行する。完了条件: `gh workflow list` またはリポジトリの CI 定義で該当 workflow 名を確認してから実行し、Android build と unit test が成功している。workflow 名を確認できない場合は停止して報告する。
