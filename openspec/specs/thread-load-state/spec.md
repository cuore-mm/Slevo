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
ThreadViewModel は dat 取得に成功した場合、投稿一覧と派生情報（IDカウント、返信元、ツリー順/深さ）を UIState に反映し、スレ履歴と保留投稿の記録処理を実行しなければならない（MUST）。

#### Scenario: 取得成功時に UIState と履歴が更新される
- **WHEN** dat 取得に成功する
- **THEN** posts と派生情報が UIState に反映され、履歴記録と保留投稿記録が実行される

