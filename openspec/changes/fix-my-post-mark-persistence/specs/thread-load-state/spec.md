## MODIFIED Requirements

### Requirement: 取得成功時の派生データと履歴反映
ThreadViewModel は dat 取得に成功した場合、投稿一覧と派生情報（IDカウント、返信元、ツリー順/深さ）を UIState に反映し、スレ履歴を記録し、取得したスレッドに属する永続化済み未確定投稿の照合処理を実行しなければならない（MUST）。

#### Scenario: 取得成功時に UIState と履歴が更新される
- **WHEN** dat 取得に成功する
- **THEN** posts と派生情報が UIState に反映され、スレ履歴記録と対象スレッドの未確定投稿照合が実行される

#### Scenario: 取得失敗時は未確定投稿を消費しない
- **WHEN** dat 取得が失敗する、または投稿レスがまだ取得結果へ反映されていない
- **THEN** システムは一致を確認できていない未確定投稿を削除または `MATCHED` にしない
