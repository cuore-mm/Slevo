# thread-load-state Specification

## Purpose
TBD - created by archiving change refactor-thread-load-data. Update Purpose after archive.
## Requirements
### Requirement: スレ読み込みの進捗とローディング状態反映
ThreadViewModel はスレ読み込み開始時に UIState をローディング状態にし、取得進捗を UIState に反映し、完了時に進捗を完了状態へ更新しなければならない（MUST）。

#### Scenario: 読み込み開始から進捗更新を行う
- **WHEN** loadData が呼ばれて dat 取得が開始される
- **THEN** isLoading を true にし、進捗コールバックで loadProgress が更新される

#### Scenario: 読み込み完了で進捗を完了状態へ更新する
- **WHEN** dat 取得が成功または失敗で終了する
- **THEN** isLoading を false にし、loadProgress を完了値へ更新する

### Requirement: 取得成功時の派生データと履歴反映
ThreadViewModel は dat 取得に成功した場合、投稿一覧と派生情報（IDカウント、返信元、ツリー順/深さ）を UIState に反映し、スレ履歴を記録し、取得したスレッドに属する永続化済み未確定投稿の照合処理を実行しなければならない（MUST）。

#### Scenario: 取得成功時に UIState と履歴が更新される
- **WHEN** dat 取得に成功する
- **THEN** posts と派生情報が UIState に反映され、スレ履歴記録と対象スレッドの未確定投稿照合が実行される

#### Scenario: 取得失敗時は未確定投稿を消費しない
- **WHEN** dat 取得が失敗する、または投稿レスがまだ取得結果へ反映されていない
- **THEN** システムは一致を確認できていない未確定投稿を削除または `MATCHED` にしない

#### Scenario: 照合処理のDB失敗でも取得成功を維持する
- **WHEN** dat取得、投稿一覧のUIState反映、スレ履歴記録が成功した後に、永続化済み未確定投稿の照合処理が非キャンセル例外で失敗する
- **THEN** システムは取得済み投稿一覧、派生情報、スレ履歴、ロード成功状態を維持する
- **AND** システムはスレッド取得失敗の通知を表示せず、照合失敗をログへ記録して次回の対象スレッド取得時に再照合する

### Requirement: スレ読み込み失敗時の通知イベント発行
ThreadViewModel はスレ読み込みが失敗した場合、ローディング状態を解除したうえで、スレ画面に読み込み失敗 Toast を表示させる one-shot event を発行しなければならない（MUST）。

#### Scenario: dat 取得中に例外が発生する
- **WHEN** ThreadViewModel の loadData が dat 取得または変換中の例外で失敗する
- **THEN** ThreadViewModel は isLoading を false に戻し、スレッド読み込み失敗 Toast 用 event を発行する

#### Scenario: dat 取得が失敗結果で終了する
- **WHEN** ThreadViewModel の loadData が投稿一覧を取得できない失敗結果で終了する
- **THEN** ThreadViewModel は isLoading を false に戻し、スレッド読み込み失敗 Toast 用 event を発行する
