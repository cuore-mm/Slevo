# database-write-gate Specification

## Purpose
TBD - created by archiving change add-database-write-gate. Update Purpose after archive.
## Requirements
### Requirement: Room DB 書き込みゲート
システムは Room DB への通常書き込みを共通の `DatabaseWriteGate` 経由で実行できなければならない（MUST）。

#### Scenario: 通常時の DB 書き込み
- **WHEN** バックアップ用停止区間が開始されていない状態で Room DB 書き込みが要求される
- **THEN** システムは `DatabaseWriteGate.withWritePermit` の block を待機なく実行する

#### Scenario: 通常書き込み同士は gate で直列化されない
- **WHEN** バックアップ用停止区間が開始されていない状態で複数の `withWritePermit` が要求される
- **THEN** システムは gate によって通常書き込み同士を直列化しない

### Requirement: バックアップ用停止区間
システムはバックアップなどの排他処理中、新規 Room DB 書き込みを待機させなければならない（MUST）。

#### Scenario: 停止区間中の新規書き込み待機
- **WHEN** `DatabaseWriteGate.withWritesSuspended` の block が実行中である
- **THEN** システムは新しく開始された `withWritePermit` の block を停止区間終了まで待機させる

#### Scenario: 進行中書き込みの完了待ち
- **WHEN** `withWritePermit` の block が実行中に `withWritesSuspended` が要求される
- **THEN** システムは進行中の書き込み block が完了してから `withWritesSuspended` の block を実行する

#### Scenario: 停止要求後かつ停止 block 開始前の新規書き込み待機
- **WHEN** `withWritePermit` の block が実行中に `withWritesSuspended` が要求され、その後に別の `withWritePermit` が要求される
- **THEN** システムは後続の `withWritePermit` を `withWritesSuspended` の block 完了まで待機させる

#### Scenario: 停止区間同士の排他
- **WHEN** `withWritesSuspended` の block が実行中に別の `withWritesSuspended` が要求される
- **THEN** システムは後続の `withWritesSuspended` を先行 block の完了まで待機させる

#### Scenario: 複数停止要求の順序
- **WHEN** 複数の `withWritesSuspended` が要求される
- **THEN** システムは `withWritesSuspended` の block を要求順に 1 つずつ実行する

#### Scenario: queued suspension 中の書き込み待機
- **WHEN** 最初の `withWritesSuspended` 要求で gate が閉じた後、別の `withWritesSuspended` と `withWritePermit` が要求される
- **THEN** システムは queued `withWritesSuspended` を完了してから `withWritePermit` を再開する

#### Scenario: 待機書き込みは後続停止要求に追い越されない
- **WHEN** gate が閉じた後に `withWritePermit` が待機状態になり、その後に別の `withWritesSuspended` が要求される
- **THEN** システムは gate close 時点の suspension queue が空になった後、後続 `withWritesSuspended` より先に待機中の `withWritePermit` を再開する

#### Scenario: 停止要求と待機書き込みの具体的な順序
- **WHEN** `S1` が active または pending の間に `S2`、`W1`、`S3` の順で `withWritesSuspended` / `withWritePermit` が要求される
- **THEN** システムは `S1`、`S2`、`W1`、`S3` の順で block を開始する

### Requirement: 例外時の gate 復旧
システムは gate 内の block が失敗またはキャンセルされた場合でも、gate 状態を復旧しなければならない（MUST）。

#### Scenario: 通常書き込み block が失敗する
- **WHEN** `withWritePermit` の block が例外またはキャンセルで終了する
- **THEN** システムは active writer 状態を解放し、後続の gate 操作を継続できる状態に戻す

#### Scenario: 停止区間 block が失敗する
- **WHEN** `withWritesSuspended` の block が例外またはキャンセルで終了する
- **THEN** システムは新規書き込み待機状態を解除し、待機中の `withWritePermit` を再開可能にする

#### Scenario: 通常書き込みの入場待ち中にキャンセルされる
- **WHEN** `withWritePermit` が停止区間の終了を待っている間にキャンセルされる
- **THEN** システムは待機状態を破損させず、後続の `withWritePermit` と `withWritesSuspended` を継続可能にする

#### Scenario: 停止区間の active writer 待ち中にキャンセルされる
- **WHEN** `withWritesSuspended` が進行中 writer の完了を待っている間にキャンセルされる
- **THEN** システムは停止要求を解除し、後続の `withWritePermit` と `withWritesSuspended` を継続可能にする

#### Scenario: 停止区間の順番待ち中にキャンセルされる
- **WHEN** `withWritesSuspended` が先行する停止区間の完了を待っている間にキャンセルされる
- **THEN** システムは待機キューを破損させず、後続の `withWritePermit` と `withWritesSuspended` を継続可能にする

#### Scenario: 停止区間 active 後にキャンセルされる
- **WHEN** `withWritesSuspended` が新規書き込みを閉じた後、block 開始前または block 実行中にキャンセルされる
- **THEN** システムは新規書き込み待機状態を解除し、後続の `withWritePermit` と `withWritesSuspended` を継続可能にする

### Requirement: Room DB 書き込み経路の移行
システムは既存の Room DB 書き込み経路を `DatabaseWriteGate.withWritePermit` 経由に移行しなければならない（MUST）。

#### Scenario: 主要 Repository の書き込みが gate を通る
- **WHEN** 掲示板サービス、板キャッシュ、ブックマーク、タブ、履歴、既読状態、投稿履歴、スレッド客観状態、NG、起動時 DB callback のいずれかが Room DB を更新する
- **THEN** システムは該当書き込みを `DatabaseWriteGate.withWritePermit` の内側で実行する

#### Scenario: DataStore と読み取り処理は gate 対象外
- **WHEN** DataStore 書き込み、read-only DAO query、Flow observe、remote data source、または parser 処理が実行される
- **THEN** システムは `DatabaseWriteGate` による不要な待機を追加しない

### Requirement: 二重 gate の回避
システムは複数 Repository/DataSource をまたぐ書き込みで二重 gate を発生させてはならない（MUST）。

#### Scenario: 外側 orchestration が内側書き込みを呼ぶ
- **WHEN** 外側の Repository method が gate を取得し、その内側で別 Repository/DataSource の書き込み処理を呼ぶ
- **THEN** システムは内側処理を ungated helper として呼び、同一 coroutine 内で二重に gate を取得しない

