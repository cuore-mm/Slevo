## MODIFIED Requirements

### Requirement: pending restore の準備
システムはバックアップ内の `database/slevo.db` と DataStore JSON を検証した後、次回起動時に適用する pending restore として内部領域へ保存しなければならない（MUST）。

#### Scenario: 復元対象 DB の整合性を検証する
- **WHEN** システムが `database/slevo.db` を pending restore として保存しようとする
- **THEN** システムは復元対象 DB を読み取り専用で開き、`PRAGMA integrity_check` が `ok` を返すことを確認する

#### Scenario: DB schema compatibility を検証する
- **WHEN** システムが current version の `database/slevo.db` を pending restore として保存しようとする
- **THEN** システムは `PRAGMA user_version = 10`、exported Room schema v10と一致する `room_master_table` のidentity hash、および `pending_own_posts` を含むv10の必須application tableが存在することを確認する

#### Scenario: 整合性検証に失敗した DB を拒否する
- **WHEN** 復元対象 DB を開けない、または `PRAGMA integrity_check` が `ok` 以外を返す
- **THEN** システムは pending restore を作成せず、復元を失敗または無効なバックアップとして通知する

#### Scenario: schema validation に失敗した DB を拒否する
- **WHEN** 復元対象 DB の `user_version`、Room identity hash、または必須 application table が現在 schema と一致しない
- **THEN** システムは pending restore を作成せず、無効なバックアップとして通知する

#### Scenario: pending marker を最後に作成する
- **WHEN** システムが pending restore を準備する
- **THEN** システムは DB と DataStore JSON の staging が完了した後に pending marker を作成する

#### Scenario: 既存 prepared pending がある場合は新規準備を拒否する
- **WHEN** `prepared` 状態の pending restore が存在する状態でユーザーが別の復元を確定する
- **THEN** システムは新しい pending restore を作成せず、既存の復元準備を再起動で適用する必要があることを通知する

#### Scenario: 既存 failed pending がある場合は cleanup 後に新規準備する
- **WHEN** `failed` 状態の pending restore が存在する状態でユーザーが別の復元を確定する
- **THEN** システムは既存 pending directory、rollback backup、result file を cleanup できた場合のみ新しい pending restore を作成する

#### Scenario: 既存 applying または db-swapped pending がある場合は新規準備を拒否する
- **WHEN** `applying` または `db-swapped` 状態の pending restore が存在する状態でユーザーが別の復元を確定する
- **THEN** システムは新しい pending restore を作成せず、次回起動時の recovery を優先する

#### Scenario: 実行中の AppDatabase を close しない
- **WHEN** システムが pending restore を準備する
- **THEN** システムは Hilt singleton の既存 `AppDatabase` を close せず、live DB ファイルを即時置換しない

#### Scenario: 復元準備完了を通知する
- **WHEN** pending restore の作成が完了する
- **THEN** システムは復元準備完了ダイアログで、アプリ再起動後に復元が適用されることをユーザーへ通知する
