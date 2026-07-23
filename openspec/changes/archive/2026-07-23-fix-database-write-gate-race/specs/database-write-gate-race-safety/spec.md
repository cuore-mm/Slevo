## ADDED Requirements

### Requirement: 再開 writer の予約
システムは停止区間終了後に待機 writer を再開するとき、writer の block が実行可能になる前に active writer として予約しなければならない（MUST）。

#### Scenario: 再開 writer は後続停止要求に追い越されない
- **WHEN** `withWritesSuspended` の終了により待機中の `withWritePermit` が再開され、その直後に新しい `withWritesSuspended` が要求される
- **THEN** システムは再開された `withWritePermit` の block を新しい `withWritesSuspended` の block より先に実行する

#### Scenario: 再開 writer の実行中に後続停止区間は開始しない
- **WHEN** 停止区間終了後に再開された `withWritePermit` の block が実行中である
- **THEN** システムは後続の `withWritesSuspended` の block をその `withWritePermit` の block 完了まで開始しない

#### Scenario: 複数の再開 writer はまとめて予約される
- **WHEN** 停止区間終了時に複数の `withWritePermit` が待機している
- **THEN** システムは後続の `withWritesSuspended` を開始する前に、再開対象のすべての writer を active writer として扱う

### Requirement: 停止区間の排他性
システムは `withWritesSuspended` の block 実行中に `withWritePermit` の block を同時実行してはならない（MUST）。

#### Scenario: active writer がある間は停止区間を開始しない
- **WHEN** `withWritePermit` の block が実行中である、または再開済み writer が active writer として予約されている
- **THEN** システムは `withWritesSuspended` の block を開始せず、active writer が 0 になるまで待機する

#### Scenario: 停止区間中の writer は待機する
- **WHEN** `withWritesSuspended` の block が実行中である
- **THEN** システムは新しく要求された `withWritePermit` の block を停止区間完了まで開始しない

### Requirement: 単一 FIFO queue による順序制御
システムは writer と suspension の待機順序を単一 FIFO queue と同等の順序規則で扱わなければならない（MUST）。

#### Scenario: queue 先頭が writer の場合
- **WHEN** active writer が 0 かつ停止区間が実行中でなく、待機 queue の先頭から連続して `withWritePermit` が並んでいる
- **THEN** システムは次の `withWritesSuspended` より前に、それらの `withWritePermit` を再開する

#### Scenario: 連続 queued writer は group として再開される
- **WHEN** 待機 queue の先頭から複数の `withWritePermit` が連続して並んでいる
- **THEN** システムはそれらを同一 writer group として再開し、group 内の writer block を gate によって直列化しない

#### Scenario: queue 先頭が suspension の場合
- **WHEN** active writer が 0 かつ停止区間が実行中でなく、待機 queue の先頭が `withWritesSuspended` である
- **THEN** システムはその `withWritesSuspended` の block を 1 件だけ開始する

#### Scenario: queue が空の通常時は writer 同士を直列化しない
- **WHEN** 停止区間が実行中でなく、待機 queue が空の状態で複数の `withWritePermit` が要求される
- **THEN** システムは gate によって通常 writer 同士を直列化せず、それぞれの block を実行可能にする

#### Scenario: queued writer は新規 writer に追い越されない
- **WHEN** active writer が存在する間に pending `withWritesSuspended` が cancellation され、queue に古い `withWritePermit` が残っている状態で新しい `withWritePermit` が要求される
- **THEN** システムは新しい `withWritePermit` を queue 末尾に追加し、古い `withWritePermit` より先に block を開始しない

### Requirement: cancellation 時の gate 復旧
システムは queue 待機中または再開予約後の coroutine cancellation により gate 状態を破損してはならない（MUST）。

#### Scenario: queue 待機中 writer がキャンセルされる
- **WHEN** `withWritePermit` が queue 内で待機している間にキャンセルされる
- **THEN** システムは該当 writer を queue から取り除き、後続の `withWritePermit` と `withWritesSuspended` を継続可能にする

#### Scenario: 予約済み writer が block 開始前にキャンセルされる
- **WHEN** `withWritePermit` が active writer として予約された後、block 開始前にキャンセルされる
- **THEN** システムは予約済み active writer 数を解放し、後続の `withWritesSuspended` を必要に応じて開始可能にする

#### Scenario: 予約済み writer が await 復帰前にキャンセルされる
- **WHEN** `withWritePermit` が queue から取り出され active writer として予約された後、`signal.await()` が cancellation で失敗する
- **THEN** システムは予約済み active writer 数を解放し、後続の `withWritePermit` と `withWritesSuspended` を継続可能にする

#### Scenario: 予約済み writer は一度だけ解放される
- **WHEN** 予約済み `withWritePermit` が await 復帰前、block 開始前、または block 実行中のいずれかでキャンセルされる
- **THEN** システムは該当 writer の active writer 予約を一度だけ解放し、active writer 数の二重減算またはリークを発生させない

#### Scenario: 停止区間待機中 suspension がキャンセルされる
- **WHEN** `withWritesSuspended` が queue 内で待機している間にキャンセルされる
- **THEN** システムは該当 suspension を queue から取り除き、後続の待機要素を継続可能にする

#### Scenario: activated suspension が block 開始前にキャンセルされる
- **WHEN** queued `withWritesSuspended` が queue から取り出され停止区間として active 化された後、block 開始前にキャンセルされる
- **THEN** システムは `suspensionActive` を解放し、後続の `withWritePermit` と `withWritesSuspended` を継続可能にする

#### Scenario: activated suspension は一度だけ解放される
- **WHEN** active 化された `withWritesSuspended` が block 開始前または block 実行中にキャンセルされる
- **THEN** システムは該当 suspension の active 状態を一度だけ解放し、queue 前進を一度だけ実行する

### Requirement: 例外時の gate 復旧
システムは `withWritePermit` または `withWritesSuspended` の block が例外で終了した場合でも、active 状態を解放し、後続 operation を継続可能にしなければならない（MUST）。

#### Scenario: 即時入場 writer の block が例外で終了する
- **WHEN** 即時入場した `withWritePermit` の block が例外を投げる
- **THEN** システムは該当 writer の active writer 予約を一度だけ解放し、後続の `withWritePermit` と `withWritesSuspended` を継続可能にする

#### Scenario: queue から再開された writer の block が例外で終了する
- **WHEN** queue から再開された `withWritePermit` の block が例外を投げる
- **THEN** システムは該当 writer の active writer 予約を一度だけ解放し、後続の `withWritePermit` と `withWritesSuspended` を継続可能にする

#### Scenario: 即時開始 suspension の block が例外で終了する
- **WHEN** 即時開始した `withWritesSuspended` の block が例外を投げる
- **THEN** システムは該当 suspension の active 状態を一度だけ解放し、後続の `withWritePermit` と `withWritesSuspended` を継続可能にする

#### Scenario: queue から開始された suspension の block が例外で終了する
- **WHEN** queue から開始された `withWritesSuspended` の block が例外を投げる
- **THEN** システムは該当 suspension の active 状態を一度だけ解放し、後続の `withWritePermit` と `withWritesSuspended` を継続可能にする

### Requirement: API 結果伝播
システムは `withWritePermit` と `withWritesSuspended` の public API として、wrapped block の戻り値、例外、cancellation を cleanup 後にそのまま呼び出し元へ伝播しなければならない（MUST）。

#### Scenario: withWritePermit の成功結果を返す
- **WHEN** `withWritePermit` の block が値を返して正常終了する
- **THEN** システムはその値を `withWritePermit` の戻り値として返す

#### Scenario: withWritesSuspended の成功結果を返す
- **WHEN** `withWritesSuspended` の block が値を返して正常終了する
- **THEN** システムはその値を `withWritesSuspended` の戻り値として返す

#### Scenario: withWritePermit の例外をそのまま伝播する
- **WHEN** `withWritePermit` の block が例外を投げる
- **THEN** システムは cleanup 後に同じ例外を呼び出し元へ再 throw する

#### Scenario: withWritesSuspended の例外をそのまま伝播する
- **WHEN** `withWritesSuspended` の block が例外を投げる
- **THEN** システムは cleanup 後に同じ例外を呼び出し元へ再 throw する

#### Scenario: withWritePermit の cancellation を cancellation として伝播する
- **WHEN** `withWritePermit` の実行中に coroutine cancellation が発生する
- **THEN** システムは cleanup 後も cancellation として呼び出し元へ伝播する

#### Scenario: withWritesSuspended の cancellation を cancellation として伝播する
- **WHEN** `withWritesSuspended` の実行中に coroutine cancellation が発生する
- **THEN** システムは cleanup 後も cancellation として呼び出し元へ伝播する
